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
import forms.{TaxServiceGroupTypeForm, YesNoForm}
import models.TaxServiceGroupType
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{GroupService, SessionCacheService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.create.{select_group_tax_type, select_group_type}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext


@Singleton
class CreateGroupSelectGroupTypeController @Inject()
(
  mcc: MessagesControllerComponents,
  val sessionCacheService: SessionCacheService,
  val groupService: GroupService,
  select_group_type: select_group_type,
  select_group_tax_type: select_group_tax_type,
)(implicit val appConfig: AppConfig, ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc) with I18nSupport with Logging {


  def showSelectGroupType: Action[AnyContent] = Action.async { implicit request =>
    Ok(select_group_type(YesNoForm.form())).toFuture
  }

  def submitSelectGroupType: Action[AnyContent] = Action.async { implicit request =>
    YesNoForm
      .form("group.type.error")
      .bindFromRequest
      .fold(
        formWithErrors =>
          Ok(select_group_type(formWithErrors)),
        (isCustomGroupType: Boolean) => {
          if (isCustomGroupType) {
            Redirect(controllers.routes.CreateGroupController.showGroupName)
          } else {
            Redirect(controllers.routes.CreateGroupSelectGroupTypeController.showSelectTaxServiceGroupType)
          }
        }
      ).toFuture

  }

  def showSelectTaxServiceGroupType: Action[AnyContent] = Action.async { implicit request =>
    Ok(select_group_tax_type(TaxServiceGroupTypeForm.form)).toFuture
  }

  def submitSelectTaxServiceGroupType: Action[AnyContent] = Action.async { implicit request =>
    TaxServiceGroupTypeForm.form.bindFromRequest()
      .fold(formWithErrors =>
        Ok(select_group_tax_type(formWithErrors)).toFuture,
        (formData: TaxServiceGroupType) => {
          sessionCacheService
            .put(GROUP_SERVICE_TYPE, formData.groupType)
            .map(_ =>
              if (formData.addAutomatically) {
                Redirect(routes.CreateGroupSelectTeamMembersController.submitReviewSelectedTeamMembers)
              } else {
                Redirect(routes.CreateGroupSelectTeamMembersController.showSelectTeamMembers(None, None))
              }
            )
        }
      )

  }
}
