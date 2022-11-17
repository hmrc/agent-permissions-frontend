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
import controllers.action.SessionAction
import forms.{AddClientsToGroupForm, AddTeamMembersToGroupForm, GroupNameForm, YesNoForm}
import models.{AddClientsToGroup, AddTeamMembersToGroup, DisplayClient, TeamMember}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{ClientService, GroupService, SessionCacheService, TeamMemberService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateGroupController @Inject()(
       authAction: AuthAction,
       sessionAction: SessionAction,
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
       val sessionCacheService: SessionCacheService,
       val groupService: GroupService,
       clientService: ClientService,
       optInStatusAction: OptInStatusAction,
       teamMemberService: TeamMemberService
     )(
       implicit val appConfig: AppConfig,
       ec: ExecutionContext,
       implicit override val messagesApi: MessagesApi
     ) extends FrontendController(mcc)
  with I18nSupport with Logging {

  import authAction._
  import optInStatusAction._
  import sessionAction.withSessionItem

  private val controller: ReverseCreateGroupController = routes.CreateGroupController

  def start: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { _ =>
      sessionCacheService.deleteAll(sessionKeys).map(_ => Redirect(controller.showGroupName))
    }
  }

  def showGroupName: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        withSessionItem[String](GROUP_NAME) { maybeName =>
          sessionCacheService.deleteAll(sessionKeys).map(_ =>
            Ok(create(GroupNameForm.form().fill(maybeName.getOrElse(""))))
          )
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
            (name: String) =>{
              val saved = for {
                _ <- sessionCacheService.put[String](GROUP_NAME, name)
                _ <- sessionCacheService.put[Boolean](GROUP_NAME_CONFIRMED, false)
              } yield ()
               saved.map(_=> Redirect(controller.showConfirmGroupName))
            }
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
                      sessionCacheService.put[Boolean](GROUP_NAME_CONFIRMED, true).map(_=>
                        Redirect(controller.showSelectClients(None, None))
                      )
                    else
                      Redirect(controller.showAccessGroupNameExists).toFuture)
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

  def showSelectClients(page: Option[Int] = None , pageSize: Option[Int] = None ): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[String](CLIENT_FILTER_INPUT) { clientFilterTerm =>
        withSessionItem[String](CLIENT_SEARCH_INPUT) { clientSearchTerm =>
          withSessionItem[String](RETURN_URL) { returnUrl =>
            clientService.getPaginatedClients(arn)(page.getOrElse(1), pageSize.getOrElse(20)).map { paginatedClients =>
              Ok(
                client_group_list(
                  paginatedClients.pageContent,
                  groupName,
                  backUrl = Some(returnUrl.getOrElse(routes.CreateGroupController.showConfirmGroupName.url)),
                  form = AddClientsToGroupForm.form().fill(AddClientsToGroup(clientSearchTerm, clientFilterTerm)),
                  paginationMetaData = Some(paginatedClients.paginationMetaData)
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
      withSessionItem[Seq[String]](SELECTED_CLIENT_IDS) { maybeSelected =>
        // allows form to bind if preselected clients so we can `.saveSelectedOrFilteredClients`
        val hasPreSelected = maybeSelected.getOrElse(Seq.empty).nonEmpty
        AddClientsToGroupForm
          // if pre-selected exist will not empty error
          .form(hasPreSelected)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              for {
                paginatedClients <- clientService.getPaginatedClients(arn)(1, 20)
              } yield
                Ok(
                  client_group_list(
                    clients = paginatedClients.pageContent,
                    groupName = groupName,
                    form = formWithErrors,
                    paginationMetaData = Some(paginatedClients.paginationMetaData))
                )
            },
            formData => {
              clientService
                .savePageOfClients(formData)
                .flatMap(_ => {
                  if (formData.submit == CONTINUE_BUTTON) {
                    // check selected clients from session cache AFTER saving (removed de-selections)
                    val hasSelectedClients = for {
                      selectedClientIds <- sessionCacheService.get(SELECTED_CLIENT_IDS)
                      // if "empty" returns Some(Vector()) so .nonEmpty on it's own returns true
                    } yield selectedClientIds.getOrElse(Seq.empty).nonEmpty
                    hasSelectedClients.flatMap(selectedNotEmpty => {
                      if (selectedNotEmpty) {
                        Redirect(controller.showReviewSelectedClients).toFuture
                      } else { // render page with empty client error
                          for {
                            paginatedClients <- clientService.getPaginatedClients(arn)(1, 20)
                            returnUrl <- sessionCacheService.get(RETURN_URL)
                          } yield
                            Ok(
                              client_group_list(
                                paginatedClients.pageContent,
                                groupName,
                                backUrl = Some(returnUrl.getOrElse(routes.CreateGroupController.showConfirmGroupName.url)),
                                form = AddClientsToGroupForm.form().withError("clients", "error.select-clients.empty"),
                                paginationMetaData = Some(paginatedClients.paginationMetaData)
                              )
                            )
                      }
                    })
                  } else if (formData.submit.startsWith(PAGINATION_BUTTON)) {
                    val pageToShow = formData.submit.replace(s"${PAGINATION_BUTTON}_", "").toInt
                    Redirect(controller.showSelectClients(Some(pageToShow), Some(20))).toFuture
                  } else {
                    Redirect(controller.showSelectClients(None, None)).toFuture
                  }
                }
                )
            }
          )
      }
    }
  }

  def showReviewSelectedClients: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, _) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { maybeClients =>
        maybeClients.fold(Redirect(controller.showSelectClients(None, None)).toFuture)(
          clients => Ok(review_clients_to_add(clients, groupName, YesNoForm.form())).toFuture)
      }
    }
  }

  def submitReviewSelectedClients(): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, _) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) {
        maybeClients =>
          maybeClients.fold(Redirect(controller.showSelectClients(None, None)).toFuture)(
            clients =>
              YesNoForm
                .form("group.clients.review.error")
                .bindFromRequest
                .fold(
                  formWithErrors => {
                    Ok(review_clients_to_add(clients, groupName, formWithErrors)).toFuture
                  }, (yes: Boolean) => {
                    if (yes)
                      sessionCacheService.deleteAll(clientFilteringKeys).map(_ =>
                        Redirect(controller.showSelectClients(None, None))
                      )
                    else {
                      sessionCacheService.get(RETURN_URL)
                        .map(returnUrl =>
                          returnUrl.fold(Redirect(controller.showSelectTeamMembers))(url => {
                            sessionCacheService.delete(RETURN_URL)
                            Redirect(url)
                          })
                        )
                    }
                  }
                )
          )
      }
    }
  }

  def showSelectTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[String](TEAM_MEMBER_SEARCH_INPUT) { teamMemberSearchTerm =>
        withSessionItem[String](RETURN_URL) { returnUrl =>
          teamMemberService.getFilteredTeamMembersElseAll(arn).map { teamMembers =>
            Ok(
              team_members_list(
                teamMembers,
                groupName,
                backUrl = Some(returnUrl.getOrElse(routes.CreateGroupController.showReviewSelectedClients.url)),
                form = AddTeamMembersToGroupForm.form().fill(
                  AddTeamMembersToGroup(
                    search = teamMemberSearchTerm
                  )
                )
              )
            )
          }
        }
      }
    }
  }

  def submitSelectedTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { maybeSelected =>
        val hasPreSelected = maybeSelected.getOrElse(Seq.empty).nonEmpty
        AddTeamMembersToGroupForm
          .form(hasPreSelected)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              teamMemberService.getFilteredTeamMembersElseAll(arn).map(tm =>
                Ok(team_members_list(tm, groupName, formWithErrors))
              )
            },
            formData => {
              teamMemberService
                .saveSelectedOrFilteredTeamMembers(formData.submit)(arn)(formData).flatMap(_ => {
                if (formData.submit == CONTINUE_BUTTON) {
                  // check selected from session AFTER saving (removed de-selections)
                  val hasSelected = for {
                    selected <- sessionCacheService.get(SELECTED_TEAM_MEMBERS)
                    // if "empty" returns Some(Vector()) so .nonEmpty on it's own returns true
                  } yield selected.getOrElse(Seq.empty).nonEmpty

                  hasSelected.flatMap(selectedNotEmpty => {
                    if (selectedNotEmpty) {
                      Redirect(controller.showReviewSelectedTeamMembers).toFuture
                    } else { // render page with empty error on continue
                      for {
                        teamMembers <- teamMemberService.getAllTeamMembers(arn)
                        returnUrl <- sessionCacheService.get(RETURN_URL)
                      } yield
                        Ok(
                          team_members_list(
                            teamMembers,
                            groupName,
                            backUrl = Some(returnUrl.getOrElse(routes.CreateGroupController.showReviewSelectedClients.url)),
                            form = AddTeamMembersToGroupForm.form().withError("members", "error.select-members.empty")
                          )
                        )
                    }
                  })
                } else Redirect(controller.showSelectTeamMembers).toFuture
              })
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
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { selectedMembers =>
        selectedMembers
          .fold(
            Redirect(controller.showSelectTeamMembers).toFuture
          ) { members =>
            YesNoForm
              .form("group.teamMembers.review.error")
              .bindFromRequest
              .fold(
                formWithErrors => {
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
          Ok(check_your_answers(groupName, maybeTeamMembers.map(_.length), maybeClients.map(_.length))).toFuture
        }
      }
    }
  }

  def redirectToEditClients: Action[AnyContent] = Action.async { implicit request =>
    sessionCacheService
      .put(RETURN_URL, controllers.routes.CreateGroupController.showCheckYourAnswers.url)
      .map(_ => Redirect(controllers.routes.CreateGroupController.showSelectClients(None, None)))

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
