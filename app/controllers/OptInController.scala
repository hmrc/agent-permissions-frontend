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
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector}
import forms.YesNoForm
import models.JourneySession
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repository.SessionCacheRepository
import services.OptInService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OptInController @Inject()(
   authAction: AuthAction,
   mcc: MessagesControllerComponents,
   val agentPermissionsConnector: AgentPermissionsConnector,
   val sessionCacheRepository: SessionCacheRepository,
   agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
   optInService: OptInService,
   start_optIn: start,
   want_to_opt_in: want_to_opt_in,
   you_have_opted_in: you_have_opted_in,
   you_have_not_opted_in: you_have_not_opted_in
 )(implicit val appConfig: AppConfig, ec: ExecutionContext, implicit override val messagesApi: MessagesApi)
  extends FrontendController(mcc) with I18nSupport with SessionBehaviour {

  import authAction._

  def start: Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent { arn =>
      withEligibleToOptIn(arn) {
        Future successful Ok(start_optIn())
      }
    }
  }

  def showDoYouWantToOptIn: Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent { arn =>
      withEligibleToOptIn(arn) {
        Ok(want_to_opt_in(YesNoForm.form())).toFuture
      }
    }
  }

  def submitDoYouWantToOptIn: Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent { arn =>
      withEligibleToOptIn(arn) {
        YesNoForm
          .form("do-you-want-to-opt-in.yes.error")
          .bindFromRequest
          .fold(
            formWithErrors => Ok(want_to_opt_in(formWithErrors)).toFuture,
            (iWantToOptIn: Boolean) => {
              if (iWantToOptIn)
                optInService.processOptIn(arn).map(_ => Redirect(routes.OptInController.showYouHaveOptedIn.url))
              else
                Redirect(routes.OptInController.showYouHaveNotOptedIn.url).toFuture
            }
          )
      }
    }
  }

  //if there is a clientList available then proceed to Groups otherwise go back to the ASA dashboard (and wait)
  def showYouHaveOptedIn: Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent { _ =>
      withSession { session =>
        session.clientList.fold(
          sessionCacheRepository.deleteFromSession(DATA_KEY).map(_ => Ok(you_have_opted_in(appConfig.agentServicesAccountManageAccountUrl))
        ))(_ => Ok(you_have_opted_in(routes.GroupsController.root.url)).toFuture)
      }
    }
  }

  def showYouHaveNotOptedIn: Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent { _ =>
      Future.successful(Ok(you_have_not_opted_in()))
    }
  }
}
