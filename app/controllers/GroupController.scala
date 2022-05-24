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
import forms.{ConfirmCreateGroupForm, CreateGroupForm}
import models.{Client, ConfirmGroup}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repository.SessionCacheRepository
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
                                 val sessionCacheRepository: SessionCacheRepository
                               )(
                                 implicit val appConfig: AppConfig, ec: ExecutionContext,
                                 implicit override val messagesApi: MessagesApi
                               ) extends FrontendController(mcc) with I18nSupport with SessionBehaviour {

  import authAction._

  val obfuscatedPrefix = "xxxx"

  def root: Action[AnyContent] = Action { implicit request =>
    Ok("groups")
  }

  def showCreateGroup: Action[AnyContent] = Action.async { implicit request =>
    Ok(create(CreateGroupForm.form)).toFuture
  }

  def submitCreateGroup: Action[AnyContent] = Action { implicit request =>
    CreateGroupForm.form()
      .bindFromRequest
      .fold(
        formWithErrors => {
          Ok(create(formWithErrors))
        },
        (name: String) => {
          //save name
          Redirect(routes.GroupController.showConfirmGroupName)
            .withSession(request.session + ("groupName" -> name))
        }
      )
  }

  def showConfirmGroupName: Action[AnyContent] = Action { implicit request =>
    val groupName = request.session.get("groupName").getOrElse("")
    Ok(confirm_group_name(
      ConfirmCreateGroupForm
        .form("group.name.confirm.required.error")
        .fill(ConfirmGroup(groupName, None))))
  }

  def submitConfirmGroupName: Action[AnyContent] = Action.async { implicit request =>
    ConfirmCreateGroupForm
      .form("group.name.confirm.required.error")
      .bindFromRequest
      .fold(
        formWithErrors => Ok(confirm_group_name(formWithErrors)).toFuture,
        validForm => {
          if (validForm.answer.get) {
            //save group name to BE then redirect to add clients to group page
            Redirect(routes.GroupController.showAddClients).toFuture
          }
          else
            Redirect(routes.GroupController.showCreateGroup.url).toFuture
        }
      )
  }

  def showAddClients: Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent { arn =>
      withSession { session =>
        session.clientList match {
          case Some(enrolments) =>
            val clients = enrolments
              .map(e => {
                val value = if (!e.identifiers.isEmpty) {
                  obfuscatedPrefix.concat(e.identifiers(0).value.takeRight(4))
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
    Redirect(routes.GroupController.showAddClients).toFuture
  }

}
