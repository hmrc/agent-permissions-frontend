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
import forms.{AddClientsToGroupForm, AddTeamMembersToGroupForm, CreateGroupForm, YesNoForm}
import models.{ButtonSelect, DisplayClient, TeamMember}
import connectors.{AgentPermissionsConnector, GroupRequest}
import models.DisplayClient.toEnrolment
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repository.SessionCacheRepository
import services.{GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Enrolment}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class GroupController @Inject()
(
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  create: create,
  confirm_group_name: confirm_group_name,
  client_group_list: client_group_list,
  review_clients_to_add: review_clients_to_add,
  team_members_list: team_members_list,
  review_team_members_to_add: review_team_members_to_add,
  check_your_answers: check_your_answers,
  group_created: group_created,
  val agentPermissionsConnector: AgentPermissionsConnector,
  sessionCacheService: SessionCacheService,
  val sessionCacheRepository: SessionCacheRepository,
  groupService: GroupService
)(
  implicit val appConfig: AppConfig, ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc) with I18nSupport with SessionBehaviour with Logging {

  import authAction._


  def start: Action[AnyContent] = Action {
    Redirect(routes.GroupController.showGroupName)
  }

  def showGroupName: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeName =>
        Ok(create(CreateGroupForm.form().fill(maybeName.getOrElse("")))).toFuture
      }
    }
  }

  def submitGroupName: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        CreateGroupForm.form()
          .bindFromRequest
          .fold(
            formWithErrors =>
              Ok(create(formWithErrors)).toFuture
            ,
            (name: String) =>
              sessionCacheService.writeGroupNameAndRedirect(name)(routes.GroupController.showConfirmGroupName)
          )
      }
    }
  }

  def showConfirmGroupName: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeName =>
        maybeName.fold(Redirect(routes.GroupController.showGroupName).toFuture) { name =>
          Ok(confirm_group_name(
            YesNoForm.form("group.name.confirm.required.error"), name)).toFuture
        }
      }
    }
  }

  def submitConfirmGroupName: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeName =>
        maybeName.fold(Redirect(routes.GroupController.showGroupName).toFuture) { name =>
          YesNoForm
            .form("group.name.confirm.required.error")
            .bindFromRequest
            .fold(
              formWithErrors => Ok(confirm_group_name(formWithErrors, name)).toFuture,
              (nameIsCorrect: Boolean) => {
                if (nameIsCorrect)
                  sessionCacheService.confirmGroupNameAndRedirect(routes.GroupController.showSelectClients)
                else
                  Redirect(routes.GroupController.showGroupName.url).toFuture
              }
            )
        }
      }
    }
  }

  def showSelectClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        maybeGroupName.fold(Redirect(routes.GroupController.showGroupName).toFuture) { groupName =>
          isOptedInWithSessionItem[Seq[DisplayClient]](FILTERED_CLIENTS)(arn) { maybeFilteredResult =>
            isOptedInWithSessionItem[Boolean](HIDDEN_CLIENTS_EXIST)(arn) { maybeHiddenClients =>
              if (maybeFilteredResult.isDefined)
                Ok(client_group_list(maybeFilteredResult, groupName, maybeHiddenClients, AddClientsToGroupForm.form())).toFuture
              else groupService.getClients(arn).flatMap { maybeClients =>
                Ok(client_group_list(maybeClients, groupName, maybeHiddenClients, AddClientsToGroupForm.form())).toFuture
              }
            }
          }
        }
      }
    }
  }

  def submitSelectedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      val buttonSelection: ButtonSelect = request.body.asFormUrlEncoded
        .fold(ButtonSelect.Continue: ButtonSelect)(someMap =>
          ButtonSelect(
            someMap.getOrElse("continue", someMap.getOrElse("submitFilter", someMap.getOrElse("submitClear", throw new RuntimeException("invalid button value for submitAddClients")))).last
          )
        )
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        maybeGroupName.fold(Redirect(routes.GroupController.showGroupName).toFuture) { groupName =>
          isOptedInWithSessionItem[Seq[DisplayClient]](FILTERED_CLIENTS)(arn) { maybeFilteredResult =>
            isOptedInWithSessionItem[Boolean](HIDDEN_CLIENTS_EXIST)(arn) { maybeHiddenClients =>
              AddClientsToGroupForm
                .form(buttonSelection)
                .bindFromRequest()
                .fold(
                  formWithErrors => {
                    for {
                      _       <- if(buttonSelection == ButtonSelect.Continue)
                        sessionCacheService.clearSelectedClients()
                      else ().toFuture
                      result  <- if(maybeFilteredResult.isDefined)
                                  Ok(client_group_list(maybeFilteredResult, groupName, maybeHiddenClients, formWithErrors)).toFuture
                                    else groupService.getClients(arn).flatMap { maybeClients =>
                                      Ok(client_group_list(maybeClients, groupName, maybeHiddenClients, formWithErrors)).toFuture
                      }
                    } yield result
                  },
                  formData => {
                    groupService.processFormDataForClients(buttonSelection)(arn)(formData).map(_ =>
                      if(buttonSelection == ButtonSelect.Continue) Redirect(routes.GroupController.showReviewSelectedClients)
                      else Redirect(routes.GroupController.showSelectClients))
                  }
                )
            }
          }
        }
    }
    }
  }

  def showReviewSelectedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        isOptedInWithSessionItem[Seq[DisplayClient]](GROUP_CLIENTS_SELECTED)(arn) { maybeClients =>
          maybeGroupName.fold(Redirect(routes.GroupController.showGroupName).toFuture)(groupName =>
            maybeClients.fold(Redirect(routes.GroupController.showSelectClients).toFuture)(clients =>
              Ok(review_clients_to_add(clients, groupName)).toFuture
            )
          )
        }
      }
    }
  }

  def showSelectTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        isOptedInWithSessionItem[Seq[TeamMember]](GROUP_TEAM_MEMBERS_SELECTED)(arn) { maybeSelectedTeamMembers =>
          isOptedInWithSessionItem[Seq[TeamMember]](FILTERED_TEAM_MEMBERS)(arn) { maybeFilteredResult =>
            isOptedInWithSessionItem[Boolean](HIDDEN_TEAM_MEMBERS_EXIST)(arn) { maybeHiddenTeamMembers =>
              maybeGroupName.fold(Redirect(routes.GroupController.showGroupName).toFuture) { groupName =>
                if (maybeFilteredResult.isDefined)
                  Ok(team_members_list(maybeFilteredResult, groupName, maybeHiddenTeamMembers, AddTeamMembersToGroupForm.form())).toFuture
                else groupService.getTeamMembers(arn)(maybeSelectedTeamMembers).flatMap { maybeTeamMembers =>
                  Ok(team_members_list(maybeTeamMembers, groupName, maybeHiddenTeamMembers, AddTeamMembersToGroupForm.form())).toFuture
                }
              }
            }
          }
        }
      }
    }
  }

  def submitSelectedTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      val buttonSelection: ButtonSelect = request.body.asFormUrlEncoded
        .fold(ButtonSelect.Continue: ButtonSelect)(someMap =>
          ButtonSelect(
            someMap.getOrElse("continue", someMap.getOrElse("submitFilter", someMap.getOrElse("submitClear", throw new RuntimeException("invalid button value for submitAddClients")))).last
          )
        )
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        isOptedInWithSessionItem[Seq[TeamMember]](GROUP_TEAM_MEMBERS_SELECTED)(arn) { maybeSelectedTeamMembers =>
          isOptedInWithSessionItem[Seq[TeamMember]](FILTERED_TEAM_MEMBERS)(arn) { maybeFilteredResult =>
          maybeGroupName.fold(Redirect(routes.GroupController.showGroupName).toFuture) { groupName =>
            groupService.getTeamMembers(arn)(maybeSelectedTeamMembers).flatMap { maybeTeamMembers =>
              isOptedInWithSessionItem[Boolean](HIDDEN_TEAM_MEMBERS_EXIST)(arn) { maybeHiddenTeamMembers =>
              AddTeamMembersToGroupForm
                .form(buttonSelection)
                .bindFromRequest()
                .fold(
                  formWithErrors => {
                    for {
                      _       <- if(buttonSelection == ButtonSelect.Continue)
                        sessionCacheService.clearSelectedTeamMembers()

                      else ().toFuture
                      result  <- if(maybeFilteredResult.isDefined)
                        Ok(team_members_list(maybeFilteredResult, groupName, maybeHiddenTeamMembers, formWithErrors)).toFuture
                      else {
                        Ok(team_members_list(maybeTeamMembers, groupName, maybeHiddenTeamMembers, formWithErrors)).toFuture
                      }
                    } yield result
                  },
                  formData => {
                    groupService.processFormDataForTeamMembers(buttonSelection)(arn)(formData).map(_ =>
                      if(buttonSelection == ButtonSelect.Continue) Redirect(routes.GroupController.showReviewSelectedTeamMembers)
                      else Redirect(routes.GroupController.showSelectTeamMembers))
                  }
                )
              }
            }
          }
        }
      }
      }
    }
  }

  def showReviewSelectedTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        isOptedInWithSessionItem[Seq[TeamMember]](GROUP_TEAM_MEMBERS_SELECTED)(arn) { maybeTeamMembers =>
          maybeGroupName.fold(Redirect(routes.GroupController.showGroupName).toFuture)(groupName =>
            maybeTeamMembers.fold(
              Redirect(routes.GroupController.showSelectTeamMembers).toFuture)
            (teamMembers =>
              Ok(review_team_members_to_add(teamMembers, groupName)).toFuture
            )
          )
        }
      }
    }
  }

  def showCheckYourAnswers: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        maybeGroupName.fold(
          Redirect(routes.GroupController.showGroupName).toFuture) { groupName =>
          isOptedInWithSessionItem[Seq[TeamMember]](GROUP_TEAM_MEMBERS_SELECTED)(arn) { maybeTeamMembers =>
            isOptedInWithSessionItem[Seq[DisplayClient]](GROUP_CLIENTS_SELECTED)(arn) { maybeClients =>
              Ok(check_your_answers(groupName, maybeTeamMembers.map(_.length), maybeClients.map(_.length))).toFuture
            }
          }
        }
      }
    }
  }

  def submitCheckYourAnswers: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        maybeGroupName.fold(
          Redirect(routes.GroupController.showGroupName).toFuture
        ) { groupName =>
          val createGroupResponse = for {
            enrolments: Option[Seq[Enrolment]] <-
              sessionCacheRepository.getFromSession[Seq[DisplayClient]](GROUP_CLIENTS_SELECTED)
                .flatMap { maybeClients: Option[Seq[DisplayClient]] =>
                  Future(
                    maybeClients.map(dcs => dcs.map(toEnrolment(_)))
                  )
                }

            members: Option[Seq[AgentUser]] <-
              sessionCacheRepository.getFromSession[Seq[TeamMember]](GROUP_TEAM_MEMBERS_SELECTED)
                .flatMap { maybeTeamMembers: Option[Seq[TeamMember]] =>
                  Future(maybeTeamMembers.map(tms => tms.map(tm => AgentUser(tm.userId.get, tm.name))))
                }

            groupRequest = GroupRequest(groupName, members, enrolments)
            response <- agentPermissionsConnector.createGroup(arn)(groupRequest)

          } yield response

          createGroupResponse.transformWith {
            case Success(_) =>
              sessionCacheService.clearAll()
              sessionCacheRepository
                .putSession[String](NAME_OF_GROUP_CREATED, groupName)
                .map(_ => Redirect(routes.GroupController.showGroupCreated))
            case Failure(ex) =>
              throw ex
          }
        }
      }
    }
  }

  def showGroupCreated: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](NAME_OF_GROUP_CREATED)(arn) { maybeGroupName =>
        maybeGroupName.fold(
          Redirect(routes.GroupController.showGroupName).toFuture
        )(groupName =>
          Ok(group_created(groupName)).toFuture
        )
      }
    }
  }

}
