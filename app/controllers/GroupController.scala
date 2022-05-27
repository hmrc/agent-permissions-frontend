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
import forms.{CreateGroupForm, YesNoForm}
import models.{Client}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repository.SessionCacheRepository
import services.SessionCacheService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class GroupController @Inject()(
                                 authAction: AuthAction,
                                 mcc: MessagesControllerComponents,
                                 create: create,
                                 confirm_group_name: confirm_group_name,
                                 client_group_list: client_group_list,
                                 val agentPermissionsConnector: AgentPermissionsConnector,
                                 sessionCacheService: SessionCacheService,
                                 val sessionCacheRepository: SessionCacheRepository
                               )(
                                 implicit val appConfig: AppConfig, ec: ExecutionContext,
                                 implicit override val messagesApi: MessagesApi
                               ) extends FrontendController(mcc) with I18nSupport with SessionBehaviour {

  import authAction._

  val obfuscatedPrefix = "xxxx"

  def showCreateGroup: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { session =>
        Ok(create(CreateGroupForm.form.fill(session.group.map(_.name).getOrElse("")))).toFuture
      }
    }
  }

  def submitCreateGroup: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { session =>
        CreateGroupForm.form()
          .bindFromRequest
          .fold(
            formWithErrors =>
              Ok(create(formWithErrors)).toFuture
            ,
            (name: String) =>
              sessionCacheService.writeGroupNameAndRedirect(name)(routes.GroupController.showConfirmGroupName)(session)
          )
      }
    }
  }

  def showConfirmGroupName: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { session =>
        session.group.fold(
          Redirect(routes.GroupController.showCreateGroup
          ).toFuture)(grp =>
        Ok(confirm_group_name(
          YesNoForm.form("group.name.confirm.required.error"), grp.name)).toFuture)
      }
    }
  }

  def submitConfirmGroupName: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { session =>
        YesNoForm
          .form("group.name.confirm.required.error")
          .bindFromRequest
          .fold(
            formWithErrors => Ok(confirm_group_name(formWithErrors, session.group.map(_.name).getOrElse(""))).toFuture,
            (nameIsCorrect: Boolean) => {
              if (nameIsCorrect)
              sessionCacheService.confirmGroupNameAndRedirect(routes.GroupController.showAddClients)(session)
              else
                Redirect(routes.GroupController.showCreateGroup.url).toFuture
            }
          )
      }
    }
  }

  def showAddClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { session =>
          session.clientList match {
            case Some(enrolments) =>
              val clients = enrolments
                .map(e => { //TODO we will remove the client list from the session and instead need to call ES20? to get the client list each time we need it (per DM), so let's nmake a ClientList service that does this logic for us i.e. it will need to transform an auth Enrolment to a Client.
                  val value = if (e.identifiers.nonEmpty) {
                    obfuscatedPrefix.concat(e.identifiers.head.value.takeRight(4))
                  } else ""
                  Client(value, e.friendlyName, e.service)
                })
              Ok(client_group_list(clients)).toFuture
            case None => Ok(client_group_list(Seq.empty)).toFuture
          }
      }
    }
  }

  def submitAddClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { session =>
        Redirect(routes.GroupController.showAddClients).toFuture
      }
    }
  }

}
