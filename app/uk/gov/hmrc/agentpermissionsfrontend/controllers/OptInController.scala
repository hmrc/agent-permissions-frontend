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

package uk.gov.hmrc.agentpermissionsfrontend.controllers

import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.agentpermissionsfrontend.config.AppConfig
import uk.gov.hmrc.agentpermissionsfrontend.views.html.start
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OptInController @Inject()(
     mcc: MessagesControllerComponents,
     val config: Configuration,
     val env: Environment,
     val authConnector: DefaultAuthConnector,
     start_optIn: start,
) (implicit val appConfig: AppConfig, ec: ExecutionContext) extends FrontendController(mcc) with AuthAction {

  def start: Action[AnyContent] = Action.async { implicit request =>
    withAuthorisedAgent{
      arn => Future.successful(Ok(start_optIn()))
    }
  }

}
