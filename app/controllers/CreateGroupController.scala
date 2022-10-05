/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package controllers

import config.AppConfig
import connectors.AgentPermissionsConnector
import forms.{AddClientsToGroupForm, AddTeamMembersToGroupForm, GroupNameForm, YesNoForm}
import models.{AddClientsToGroup, AddTeamMembersToGroup, ButtonSelect, DisplayClient, TeamMember}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
import services.{ClientService, GroupService, SessionCacheService, TeamMemberService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateGroupController @Inject()(
                                 authAction: AuthAction,
                                 mcc: MessagesControllerComponents,
                                 create: create,
                                 confirm_group_name: confirm_group_name,
                                 access_group_name_exists: access_group_name_exists,
                                 val client_group_list: client_group_list,
                                 review_clients_to_add: review_clients_to_add,
                                 team_members_list: team_members_list,
                                 review_team_members_to_add: review_team_members_to_add,
                                 check_your_answers: check_your_answers,
                                 group_created: group_created,
                                 val agentPermissionsConnector: AgentPermissionsConnector,
                                 val sessionCacheService: SessionCacheService,
                                 val sessionCacheRepository: SessionCacheRepository,
                                 val groupService: GroupService,
                                 clientService: ClientService,
                                 teamMemberService: TeamMemberService
                               )(
                                 implicit val appConfig: AppConfig,
                                 ec: ExecutionContext,
                                 implicit override val messagesApi: MessagesApi
                               ) extends FrontendController(mcc)
  with GroupsControllerCommon
  with I18nSupport
  with SessionBehaviour
  with Logging {

  import authAction._

  private val controller: ReverseCreateGroupController = routes.CreateGroupController

  def start: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { _ =>
      clearSession().map(_ => Redirect(controller.showGroupName))
    }
  }

  def showGroupName: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        withSessionItem[String](GROUP_NAME) { maybeName =>
          Ok(create(GroupNameForm.form().fill(maybeName.getOrElse("")))).toFuture
        }
      }
    }
  }

  def submitGroupName: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        GroupNameForm
          .form()
          .bindFromRequest
          .fold(
            formWithErrors => Ok(create(formWithErrors)).toFuture,
            (name: String) =>
              sessionCacheService.writeGroupNameAndRedirect(name)(
                controller.showConfirmGroupName)
          )
      }
    }
  }

  def showConfirmGroupName: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, _) =>
      val form = YesNoForm.form("group.name.confirm.required.error")
      Ok(confirm_group_name(form, groupName)).toFuture
    }
  }

  def submitConfirmGroupName: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      YesNoForm
        .form("group.name.confirm.required.error")
        .bindFromRequest
        .fold(
          formWithErrors =>
            Ok(confirm_group_name(formWithErrors, groupName)).toFuture,
          (nameIsCorrect: Boolean) => {
            if (nameIsCorrect)
              agentPermissionsConnector
                .groupNameCheck(arn, groupName)
                .flatMap(
                  nameAvailable =>
                    if (nameAvailable)
                      sessionCacheService.confirmGroupNameAndRedirect(
                        controller.showSelectClients)
                    else
                      Redirect(
                        controller.showAccessGroupNameExists).toFuture)
            else
              Redirect(controller.showGroupName.url).toFuture
          }
        )
    }
  }

  def showAccessGroupNameExists: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, _) =>
      Ok(access_group_name_exists(groupName)).toFuture
    }
  }

  def showSelectClients: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[String](CLIENT_FILTER_INPUT) { clientFilterTerm =>
        withSessionItem[String](CLIENT_SEARCH_INPUT) { clientSearchTerm =>
          withSessionItem[Boolean](HIDDEN_CLIENTS_EXIST) { maybeHiddenClients =>
            clientService.getClients(arn).map { maybeClients =>
              Ok(
                client_group_list(
                  maybeClients,
                  groupName,
                  maybeHiddenClients,
                  AddClientsToGroupForm.form().fill(
                    AddClientsToGroup(
                      maybeHiddenClients.getOrElse(false),
                      search = clientSearchTerm,
                      filter = clientFilterTerm,
                      clients = None))
                )
              )
            }
          }
        }
      }
    }
  }

  def submitSelectedClients: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[Boolean](HIDDEN_CLIENTS_EXIST) { maybeHiddenClients =>
        val buttonSelection: ButtonSelect = buttonClickedByUserOnFilterFormPage(request.body.asFormUrlEncoded)

        AddClientsToGroupForm
          .form(buttonSelection)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              for {
                _ <- if (buttonSelection == ButtonSelect.Continue)
                  sessionCacheService.clearSelectedClients()
                else ().toFuture
                clients <- clientService.getClients(arn)
              } yield
                Ok(
                  client_group_list(clients,
                    groupName,
                    maybeHiddenClients,
                    formWithErrors))

            },
            formData => {
              clientService.saveSelectedOrFilteredClients(buttonSelection)(arn)(formData)(clientService.getAllClients)
                .map(_ =>
                  if (buttonSelection == ButtonSelect.Continue)
                    Redirect(controller.showReviewSelectedClients)
                  else
                    Redirect(
                      controller.showSelectClients))
            }
          )
      }
    }
  }

  def showReviewSelectedClients: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, _) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { maybeClients =>
        maybeClients.fold(Redirect(controller.showSelectClients).toFuture)(
          clients => Ok(review_clients_to_add(clients, groupName,YesNoForm.form())).toFuture)
      }
    }
  }

  def submitReviewSelectedClients(): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent{ (groupName, arn) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) {
        maybeClients =>
          maybeClients.fold(Redirect(controller.showSelectClients).toFuture)(
            clients  =>
            YesNoForm
              .form("group.clients.review.error")
              .bindFromRequest
              .fold(
                formWithErrors =>{
                  Ok(review_clients_to_add(clients, groupName, formWithErrors)).toFuture
                }, (yes: Boolean) => {
                  if (yes)
                    Redirect(controller.showSelectClients).toFuture
                  else
                    Redirect(controller.showSelectTeamMembers).toFuture
                }
              )
          )
          }
      }
    }

  def showSelectTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[String](TEAM_MEMBER_SEARCH_INPUT) { teamMemberSearchTerm =>
        withSessionItem[Boolean](HIDDEN_TEAM_MEMBERS_EXIST) { maybeHiddenTeamMembers =>
          teamMemberService.getTeamMembers(arn).map { maybeTeamMembers =>
            Ok(
              team_members_list(
                maybeTeamMembers,
                groupName,
                maybeHiddenTeamMembers,
                AddTeamMembersToGroupForm.form().fill(AddTeamMembersToGroup(
                  hasAlreadySelected = maybeHiddenTeamMembers.getOrElse(false),
                  search = teamMemberSearchTerm,
                  members = None
                ))
              ))
          }
        }
      }
    }
  }

  def submitSelectedTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
            withSessionItem[Boolean](HIDDEN_TEAM_MEMBERS_EXIST) { maybeHiddenTeamMembers =>

              val buttonSelection: ButtonSelect = buttonClickedByUserOnFilterFormPage(request.body.asFormUrlEncoded)

              AddTeamMembersToGroupForm
                .form(buttonSelection)
                .bindFromRequest()
                .fold(
                  formWithErrors => {
                    for {
                      _ <- if (buttonSelection == ButtonSelect.Continue)
                        sessionCacheService
                          .clearSelectedTeamMembers()
                      else ().toFuture
                      teamMembers <- teamMemberService.getTeamMembers(arn)
                    } yield
                        Ok(
                          team_members_list(
                            teamMembers,
                            groupName,
                            maybeHiddenTeamMembers,
                            formWithErrors))
                  },
                  formData => {
                    teamMemberService
                      .saveSelectedOrFilteredTeamMembers(
                        buttonSelection)(arn)(formData)
                      .map(_ =>
                        if (buttonSelection == ButtonSelect.Continue)
                          Redirect(controller.showReviewSelectedTeamMembers)
                        else Redirect(controller.showSelectTeamMembers))
                  }
                )
        }
      }
  }

  def showReviewSelectedTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { maybeTeamMembers =>
        maybeTeamMembers.fold(
          Redirect(controller.showSelectTeamMembers).toFuture
        )(members =>
          Ok(review_team_members_to_add(members, groupName, YesNoForm.form())).toFuture
        )
      }
    }
  }

  def submitReviewSelectedTeamMembers(): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent{ (groupName, arn) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { selectedMembers =>
        selectedMembers
          .fold(
            Redirect(controller.showSelectTeamMembers).toFuture
          ){ members =>
            YesNoForm
              .form("group.teamMembers.review.error")
              .bindFromRequest
              .fold(
                formWithErrors =>{
                  Ok(review_team_members_to_add(members, groupName, formWithErrors)).toFuture
                }, (yes: Boolean) => {
                  if (yes)
                    Redirect(controller.showSelectTeamMembers).toFuture
                  else
                    Redirect(controller.showCheckYourAnswers).toFuture
                }
              )
          }
      }
    }
  }

  def showCheckYourAnswers: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { maybeTeamMembers =>
        withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { maybeClients =>
          Ok(
            check_your_answers(groupName,
              maybeTeamMembers.map(_.length),
              maybeClients.map(_.length))).toFuture
        }
      }
    }
  }

  def submitCheckYourAnswers: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      groupService.createGroup(arn, groupName).map(_ =>
      Redirect(controller.showGroupCreated))
    }
  }

  def showGroupCreated: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](NAME_OF_GROUP_CREATED)(arn) {
        maybeGroupName =>
          maybeGroupName.fold(
            Redirect(controller.showGroupName).toFuture
          )(groupName => Ok(group_created(groupName)).toFuture)
      }
    }
  }

  private def withGroupNameForAuthorisedOptedAgent(body: (String, Arn) => Future[Result])
                                                  (implicit ec: ExecutionContext, request: MessagesRequest[AnyContent], appConfig: AppConfig): Future[Result] = {
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        maybeGroupName.fold(Redirect(controller.showGroupName).toFuture) {
          groupName => body(groupName, arn)
        }
      }
    }
  }

}
