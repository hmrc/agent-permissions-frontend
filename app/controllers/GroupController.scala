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
import forms.{AddClientsToGroupForm, CreateGroupForm, YesNoForm}
import models.DisplayClient
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repository.SessionCacheRepository
import services.{ClientListService, SessionCacheService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class GroupController @Inject()
(
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  create: create,
  confirm_group_name: confirm_group_name,
  client_group_list: client_group_list,
  review_clients_to_add: review_clients_to_add,
  val agentPermissionsConnector: AgentPermissionsConnector,
  sessionCacheService: SessionCacheService,
  val sessionCacheRepository: SessionCacheRepository,
  clientListService: ClientListService
)(
  implicit val appConfig: AppConfig, ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc) with I18nSupport with SessionBehaviour {

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
            clientListService.getClientList(arn)(maybeSelectedClients)
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
          clientListService.getClientList(arn)(maybeClients).flatMap { maybeClients =>
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
          maybeClients.fold(Redirect(routes.GroupController.showAddClients).toFuture)( clients =>
            Ok(review_clients_to_add(clients, groupName)).toFuture
          )
       )
        }
      }
    }
  }


//  def submitReviewClientsToAdd: Action[AnyContent] = Action.async {
//    implicit request =>
//      isAuthorisedAgent {
//        arn =>
//          isOptedInWithSessionItem[Seq[DisplayClient]](GROUP_CLIENTS_SELECTED)(arn) { maybeClients =>
//            maybeClients.fold(Redirect(routes.GroupController.showAddClients))(_ =>
//
//            )
//
//
//
//
//
//            maybeClients =>
//              if (maybeClients.isDefined) {
//                YesNoForm
//                  .form()
//                  .bindFromRequest()
//                  .fold(
//
//                    formWithErrors => {
//                      for {
//                        maybeGroupName <- sessionCacheRepository.getFromSession(GROUP_NAME)
//                      } yield Ok(review_clients_to_add(maybeClients.get, maybeGroupName, Some
//                      (formWithErrors)))
//                        .toFuture
//                    },
//                    (needToAddMore: Boolean) => {
//                      if (needToAddMore) {
//                        //go back to add more
//                        Redirect(routes.GroupController.showAddClients.url).toFuture
//                      } else {
//                        //now add clients to the group
//                        println("************************")
//                        println(maybeClients.getOrElse(Seq.empty))
//                        println("************************")
//                        Redirect(routes.GroupController.showAddTeamMembers.url).toFuture
//                      }
//                    }
//                  )
//                Ok(review_clients_to_add(maybeClients.getOrElse(Seq.empty))).toFuture
//              } else {
//                Redirect(routes.GroupController.showAddClients).toFuture
//              }
//          }
//      }
//  }

  def showAddTeamMembers: Action[AnyContent] = Action.async {
    implicit request =>
      Ok("add team members here").toFuture
  }

}
