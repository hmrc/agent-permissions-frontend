/*
 * Copyright 2023 HM Revenue & Customs
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
import controllers.actions.{GroupAction, SessionAction}
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
     sessionAction: SessionAction,
     mcc: MessagesControllerComponents,
     choose_name: choose_name,
     confirm_name: confirm_name,
     duplicate_group_name: duplicate_group_name,
     val sessionCacheService: SessionCacheService,
     val groupService: GroupService,
   )(
     implicit val appConfig: AppConfig,
     ec: ExecutionContext,
     implicit override val messagesApi: MessagesApi
   ) extends FrontendController(mcc)
  with I18nSupport with Logging {

  import groupAction._
  import sessionAction.withSessionItem

  private val controller: ReverseCreateGroupSelectNameController = routes.CreateGroupSelectNameController

  def showGroupName: Action[AnyContent] = Action.async { implicit request =>
    withGroupTypeAndAuthorised { (groupType, _) =>
      withSessionItem[String](GROUP_NAME) { maybeName =>
        Ok(choose_name(
          formWithFilledValue(GroupNameForm.form(), maybeName)
        )).toFuture
      }
    }
  }

  def submitGroupName: Action[AnyContent] = Action.async { implicit request =>
    withGroupTypeAndAuthorised { (_, _) =>
        GroupNameForm
          .form()
          .bindFromRequest()
          .fold(
            formWithErrors => Ok(choose_name(formWithErrors)).toFuture,
            (name: String) => {
            sessionCacheService.put[String](GROUP_NAME, name)
              .map(_=> Redirect(controller.showConfirmGroupName()))
            }
          )
    }
  }

  def showConfirmGroupName: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName,_, _) =>
      sessionCacheService.get(GROUP_NAME_CONFIRMED).map( mConfirmed =>
      Ok(confirm_name(formWithFilledValue(YesNoForm.form("group.name.confirm.required.error"), mConfirmed), groupName))
      )
    }
  }

  def submitConfirmGroupName: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, groupType, arn) =>
      YesNoForm
        .form("group.name.confirm.required.error")
        .bindFromRequest()
        .fold(
          formWithErrors =>
            Ok(confirm_name(formWithErrors, groupName)).toFuture,
          (nameIsCorrect: Boolean) => {
            if (nameIsCorrect)
              groupService
                .groupNameCheck(arn, groupName)
                .flatMap(
                  nameAvailable =>
                    if (nameAvailable) {
                      for {
                        _ <- sessionCacheService.put[Boolean](GROUP_NAME_CONFIRMED, true)
                      } yield
                        if(groupType == CUSTOM_GROUP) {
                        Redirect(routes.CreateGroupSelectClientsController.showSearchClients())
                      } else Redirect(routes.CreateGroupSelectTeamMembersController.showSelectTeamMembers(None,None))
                    } else
                      Redirect(controller.showAccessGroupNameExists()).toFuture)
            else
              Redirect(controller.showGroupName().url).toFuture
          }
        )
    }
  }

  def showAccessGroupNameExists: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, _, _) =>
      Ok(duplicate_group_name(groupName)).toFuture
    }
  }

}
