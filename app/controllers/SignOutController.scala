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
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import sttp.model.Uri.UriContext
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.timeout._

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class SignOutController @Inject() (
  mcc: MessagesControllerComponents,
  you_have_been_timed_out: you_have_been_timed_out,
  you_have_signed_out: you_have_signed_out
)(implicit val appConfig: AppConfig, implicit override val messagesApi: MessagesApi)
    extends FrontendController(mcc) with I18nSupport {

  private def signOutWithContinue(continue: String) = {
    val signOutAndRedirectUrl: String = uri"${appConfig.signOut}?${Map("continue" -> continue)}".toString
    Redirect(signOutAndRedirectUrl)
  }

  def timeOut: Action[AnyContent] = Action.async {
    val continue = uri"${appConfig.selfExternalUrl + routes.SignOutController.timedOut().url}"
    Future.successful(signOutWithContinue(continue.toString))
  }

  def timedOut: Action[AnyContent] = Action { implicit request =>
    Ok(you_have_been_timed_out())
  }

  def signOut: Action[AnyContent] = Action.async {
    val continue = uri"${appConfig.selfExternalUrl + routes.SignOutController.signedOut().url}"
    Future.successful(signOutWithContinue(continue.toString))
  }

  def signedOut: Action[AnyContent] = Action { implicit request =>
    Ok(you_have_signed_out())
  }

}
