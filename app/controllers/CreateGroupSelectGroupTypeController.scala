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
import controllers.actions.{AuthAction, OptInStatusAction, SessionAction}
import forms.{TaxServiceGroupTypeForm, YesNoForm}
import models.TaxServiceGroupType
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{ClientService, GroupService, SessionCacheService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.ViewUtils
import views.html.groups.create.groupType.{review_group_type, select_group_tax_type, select_group_type}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext


@Singleton
class CreateGroupSelectGroupTypeController @Inject()
(
  mcc: MessagesControllerComponents,
  val sessionCacheService: SessionCacheService,
  val groupService: GroupService,
  authAction: AuthAction,
  optInStatusAction: OptInStatusAction,
  clientService: ClientService,
  sessionAction: SessionAction,
  select_group_type: select_group_type,
  select_group_tax_type: select_group_tax_type,
  review_group_type: review_group_type,
)(implicit val appConfig: AppConfig, ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc) with I18nSupport with Logging {

  val ctrlRoutes = controllers.routes.CreateGroupSelectGroupTypeController

  import authAction.isAuthorisedAgent
  import optInStatusAction.isOptedIn
  import sessionAction.withSessionItem

  def showSelectGroupType: Action[AnyContent] = Action.async { implicit request =>
    Ok(select_group_type(YesNoForm.form())).toFuture
  }

  def submitSelectGroupType: Action[AnyContent] = Action.async { implicit request =>
    YesNoForm
      .form("group.type.error")
      .bindFromRequest
      .fold(
        formWithErrors =>
          Ok(select_group_type(formWithErrors)).toFuture,
        (isCustomGroupType: Boolean) => {
          if (isCustomGroupType) {
            sessionCacheService
              .put(GROUP_TYPE, CUSTOM_GROUP)
              .map(_=> Redirect(controllers.routes.CreateGroupSelectNameController.showGroupName))
          } else {
            sessionCacheService
              .put(GROUP_TYPE, TAX_SERVICE_GROUP)
              .map(_=> Redirect(ctrlRoutes.showSelectTaxServiceGroupType))
          }
        }
      )

  }

  def showSelectTaxServiceGroupType: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        clientService.getAvailableTaxServiceClientCount(arn).map(info =>
          Ok(select_group_tax_type(TaxServiceGroupTypeForm.form, info))
        )
      }
    }
  }

  def submitSelectTaxServiceGroupType: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        TaxServiceGroupTypeForm.form.bindFromRequest()
          .fold(formWithErrors => {
            clientService.getAvailableTaxServiceClientCount(arn).map(info =>
              Ok(select_group_tax_type(formWithErrors, info))
            )
          },
            (formData: TaxServiceGroupType) => {
              sessionCacheService
                .put(GROUP_SERVICE_TYPE, formData.groupType)
                .flatMap(_ => {
                  sessionCacheService
                    .put(GROUP_NAME, ViewUtils.displayTaxServiceFromServiceKey(formData.groupType))
                    .map(_=>
                      Redirect(ctrlRoutes.showReviewTaxServiceGroupType)
                    )
                })

            }
          )
      }
    }
  }

  def showReviewTaxServiceGroupType: Action[AnyContent] = Action.async { implicit request =>
    withSessionItem[String](GROUP_SERVICE_TYPE) { maybeTaxServiceGroupType =>
      maybeTaxServiceGroupType.fold(Redirect(ctrlRoutes.showSelectTaxServiceGroupType))(taxGroupType =>
        Ok(review_group_type(YesNoForm.form(""), taxGroupType))
      ).toFuture
    }
  }

  def submitReviewTaxServiceGroupType: Action[AnyContent] = Action.async { implicit request =>
    YesNoForm.form("group.tax-service.review.error").bindFromRequest()
      .fold(formWithErrors => {
        withSessionItem[String](GROUP_SERVICE_TYPE) { maybeTaxServiceGroupType =>
          maybeTaxServiceGroupType.fold(Redirect(ctrlRoutes.showSelectTaxServiceGroupType))(taxGroupType =>
            Ok(review_group_type(formWithErrors, taxGroupType))
          ).toFuture
        }
      },
        (continue: Boolean) => {
          if (continue) {
            Redirect(routes.CreateGroupSelectTeamMembersController.showSelectTeamMembers(None, None))
          } else {
            Redirect(ctrlRoutes.showSelectGroupType)
          }
        }.toFuture
      )
  }
}
