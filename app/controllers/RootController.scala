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
import models.JourneySession
import play.api.Logging
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Results}
import repository.SessionCacheRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class RootController @Inject()(
                              authAction: AuthAction,
  mcc: MessagesControllerComponents,
  agentPermissionsConnector: AgentPermissionsConnector,
  sessionCacheRepository: SessionCacheRepository)(implicit val appConfig: AppConfig, ec: ExecutionContext)
    extends FrontendController(mcc) with Logging {

  import authAction._

  val start: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent{ arn =>
      sessionCacheRepository.getFromSession(DATA_KEY).flatMap{
        case Some(session) => if(session.isEligibleToOptIn)
          Redirect(routes.OptInController.start.url).toFuture
        else if(session.isEligibleToOptOut) Redirect(routes.OptOutController.start.url).toFuture
        else {
          logger.warn(s"user was not eligible to opt-In or opt-Out, redirecting to ASA.")
          Redirect(appConfig.agentServicesAccountManageAccountUrl).toFuture
        }
        case None => agentPermissionsConnector.getOptInStatus(arn).flatMap{
          case Some(status) =>
            sessionCacheRepository.putSession(DATA_KEY, JourneySession(optInStatus = status))
              .map(_ => Results.Redirect(routes.RootController.start.url))
          case None => throw new RuntimeException("there was a problem when trying to get the opted-In status")
        }
      }
    }
  }
}
