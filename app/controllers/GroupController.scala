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
import forms.{AddClientsToGroupForm, AddTeamMembersToGroupForm, CreateGroupForm, YesNoForm}
import models.{DisplayClient, TeamMember}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repository.SessionCacheRepository
import services.{GroupService, SessionCacheService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
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


  def start = Action {
    Redirect(routes.GroupController.showGroupName)
  }

  def showGroupName: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeName =>
        Ok(create(CreateGroupForm.form.fill(maybeName.getOrElse("")))).toFuture
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
                  sessionCacheService.confirmGroupNameAndRedirect(routes.GroupController.showAddClients)
                else
                  Redirect(routes.GroupController.showGroupName.url).toFuture
              }
            )
        }
      }
    }
  }

  def showAddClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        isOptedInWithSessionItem[Seq[DisplayClient]](GROUP_CLIENTS_SELECTED)(arn) { maybeSelectedClients =>
          maybeGroupName.fold(Redirect(routes.GroupController.showGroupName).toFuture) { groupName =>
            groupService.getClients(arn)(maybeSelectedClients)
              .flatMap(maybeClients =>
                Ok(client_group_list(maybeClients, groupName, AddClientsToGroupForm.form())).toFuture)
          }
        }
      }
    }
  }

  def submitAddClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        isOptedInWithSessionItem[Seq[DisplayClient]](GROUP_CLIENTS_SELECTED)(arn) { maybeClients =>
          maybeGroupName.fold(Redirect(routes.GroupController.showGroupName).toFuture) { groupName =>
            groupService.getClients(arn)(maybeClients).flatMap { maybeClients =>
              AddClientsToGroupForm
                .form()
                .bindFromRequest()
                .fold(
                  formWithErrors => {
                    Ok(client_group_list(maybeClients, groupName, formWithErrors)).toFuture
                  },
                  (clientsToAdd: Seq[DisplayClient]) =>
                    sessionCacheService
                      .saveSelectedGroupClientsAndRedirect(clientsToAdd)(routes.GroupController.showReviewClientsToAdd)

                )
            }
          }
        }
      }
    }
  }


  def showReviewClientsToAdd: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        isOptedInWithSessionItem[Seq[DisplayClient]](GROUP_CLIENTS_SELECTED)(arn) { maybeClients =>
          maybeGroupName.fold(Redirect(routes.GroupController.showGroupName).toFuture)(groupName =>
            maybeClients.fold(Redirect(routes.GroupController.showAddClients).toFuture)(clients =>
              Ok(review_clients_to_add(clients, groupName)).toFuture
            )
          )
        }
      }
    }
  }

  def showAddTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        isOptedInWithSessionItem[Seq[TeamMember]](GROUP_TEAM_MEMBERS_SELECTED)(arn) { maybeTeamMembers =>
          maybeGroupName.fold(Redirect(routes.GroupController.showGroupName).toFuture) { groupName =>
            groupService.getTeamMembers(arn)(maybeTeamMembers).flatMap { maybeTeamMembers =>
              Ok(team_members_list(maybeTeamMembers, groupName, AddTeamMembersToGroupForm.form())).toFuture
            }
          }
        }
      }
    }
  }

  def submitAddTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        isOptedInWithSessionItem[Seq[TeamMember]](GROUP_TEAM_MEMBERS_SELECTED)(arn) { maybeClients =>
          maybeGroupName.fold(Redirect(routes.GroupController.showGroupName).toFuture) { groupName =>
            groupService.getTeamMembers(arn)(maybeClients).flatMap { maybeClients =>
              AddTeamMembersToGroupForm
                .form()
                .bindFromRequest()
                .fold(
                  formWithErrors => {
                    Ok(team_members_list(maybeClients, groupName, formWithErrors)).toFuture
                  },
                  (teamMembersToAdd: Seq[TeamMember]) =>
                    sessionCacheService
                      .saveSelectedTeamMembers(teamMembersToAdd).transformWith {
                      case Success(_) => Redirect(routes.GroupController.showReviewTeamMembersToAdd).toFuture
                      case Failure(ex) =>
                        logger.warn(s"Unable to save team members in session ${ex.getMessage}")
                        throw ex
                    }
                )
            }
          }
        }
      }
    }
  }

  def showReviewTeamMembersToAdd: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        isOptedInWithSessionItem[Seq[TeamMember]](GROUP_TEAM_MEMBERS_SELECTED)(arn) { maybeTeamMembers =>
          maybeGroupName.fold(Redirect(routes.GroupController.showGroupName).toFuture)(groupName =>
            maybeTeamMembers.fold(
              Redirect(routes.GroupController.showAddTeamMembers).toFuture)
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
    //TODO: probably remove everything from the session here and persist, before redirecting
    Redirect(routes.GroupController.showGroupCreated).toFuture
  }

  def showGroupCreated: Action[AnyContent] = Action.async { implicit request =>
    //TODO: probably don't use session now but actual created group from wherever it's saved?
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        maybeGroupName.fold(Redirect(routes.GroupController.showGroupName).toFuture) { groupName =>
          Ok(group_created(groupName)).toFuture
        }
      }
    }
  }

}
