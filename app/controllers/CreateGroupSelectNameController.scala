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
import controllers.actions.{AuthAction, GroupAction, OptInStatusAction, SessionAction}
import forms.{GroupNameForm, YesNoForm}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{GroupService, SessionCacheService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.create.name._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class CreateGroupSelectNameController @Inject()(
     groupAction: GroupAction,
     authAction: AuthAction,
     sessionAction: SessionAction,
     mcc: MessagesControllerComponents,
     choose_name: choose_name,
     confirm_name: confirm_name,
     duplicate_group_name: duplicate_group_name,
     val sessionCacheService: SessionCacheService,
     val groupService: GroupService,
     optInStatusAction: OptInStatusAction,
   )(
     implicit val appConfig: AppConfig,
     ec: ExecutionContext,
     implicit override val messagesApi: MessagesApi
   ) extends FrontendController(mcc)
  with I18nSupport with Logging {

  import authAction._
  import groupAction._
  import optInStatusAction._
  import sessionAction.withSessionItem

  private val controller: ReverseCreateGroupSelectNameController = routes.CreateGroupSelectNameController

  def showGroupName: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        withSessionItem[String](GROUP_NAME) { maybeName =>
          sessionCacheService.deleteAll(sessionKeys).map(_ =>
            Ok(choose_name(GroupNameForm.form().fill(maybeName.getOrElse(""))))
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
            formWithErrors => Ok(choose_name(formWithErrors)).toFuture,
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
    withGroupNameAndAuthorised { (groupName, _) =>
      val form = YesNoForm.form("group.name.confirm.required.error")
      Ok(confirm_name(form, groupName)).toFuture
    }
  }

  def submitConfirmGroupName: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, arn) =>
      YesNoForm
        .form("group.name.confirm.required.error")
        .bindFromRequest
        .fold(
          formWithErrors =>
            Ok(confirm_name(formWithErrors, groupName)).toFuture,
          (nameIsCorrect: Boolean) => {
            if (nameIsCorrect)
              agentPermissionsConnector
                .groupNameCheck(arn, groupName)
                .flatMap(
                  nameAvailable =>
                    if (nameAvailable)
                      sessionCacheService.put[Boolean](GROUP_NAME_CONFIRMED, true).map(_=>
                        Redirect(routes.CreateGroupSelectClientsController.showSearchClients)
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
    withGroupNameAndAuthorised { (groupName, _) =>
      Ok(duplicate_group_name(groupName)).toFuture
    }
  }

}
