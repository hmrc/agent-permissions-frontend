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
import forms.YesNoForm
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repository.SessionCacheRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class OptOutController @Inject()(
                                  authAction: AuthAction,
                                  mcc: MessagesControllerComponents,
                                  val agentPermissionsConnector: AgentPermissionsConnector,
                                  val sessionCacheRepository: SessionCacheRepository,
                                  opt_out_start: opt_out_start,
                                  want_to_opt_out: want_to_opt_out,
                                  you_have_opted_out: you_have_opted_out,
                                )(
                                  implicit val appConfig: AppConfig, ec: ExecutionContext, implicit override val messagesApi: MessagesApi)
  extends FrontendController(mcc)
    with I18nSupport with SessionBehaviour {

  import authAction._

  def start: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        Ok(opt_out_start()).toFuture
      }
    }
  }

  def showDoYouWantToOptOut: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        Ok(want_to_opt_out(YesNoForm.form())).toFuture
      }
    }
  }

  def submitDoYouWantToOptOut: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        YesNoForm
          .form("do-you-want-to-opt-out.yes.error")
          .bindFromRequest
          .fold(
            formWithErrors => Ok(want_to_opt_out(formWithErrors)).toFuture,
            (iWantToOptOut: Boolean) => {
              if (iWantToOptOut)
                agentPermissionsConnector.optOut(arn)
                  .map(_ => Redirect(routes.OptOutController.showYouHaveOptedOut.url))
              else
                Redirect(appConfig.agentServicesAccountManageAccountUrl).toFuture
            }
          )
      }
    }
  }

  def showYouHaveOptedOut: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedOut(arn) { _ =>
        sessionCacheRepository
          .deleteFromSession(DATA_KEY)
          .map(_ => Ok(you_have_opted_out()))
      }
    }
  }

}
