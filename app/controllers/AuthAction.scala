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
import play.api.mvc.Results.{Forbidden, Redirect}
import play.api.mvc.{Request, Result}
import play.api.{Configuration, Environment, Logging}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{
  allEnrolments,
  credentialRole
}
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.AuthRedirects
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthAction @Inject()(val authConnector: AuthConnector,
                           val env: Environment,
                           val config: Configuration)
    extends AuthRedirects
    with AuthorisedFunctions
    with Logging {

  private val agentEnrolment = "HMRC-AS-AGENT"
  private val agentReferenceNumberIdentifier = "AgentReferenceNumber"

  def isAuthorisedAgent(body: Arn => Future[Result])(
      implicit ec: ExecutionContext,
      request: Request[_],
      appConfig: AppConfig) = {

    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    authorised(AuthProviders(GovernmentGateway) and Enrolment(agentEnrolment))
      .retrieve(allEnrolments and credentialRole) {
        case enrols ~ credRole =>
          getArn(enrols) match {
            case Some(arn) =>
              credRole match {
                case Some(User) | Some(Admin) => body(arn)
                case _ =>
                  logger.warn("Invalid credential role")
                  Future.successful(Forbidden)
              }
            case None =>
              logger.warn(s"No $agentReferenceNumberIdentifier in enrolment")
              Future.successful(Forbidden)
          }
      }
      .recover(handleFailure)
  }

  def handleFailure(
      implicit request: Request[_],
      appConfig: AppConfig): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession =>
      Redirect(
        s"${appConfig.basGatewayUrl}/bas-gateway/sign-in",
        Map(
          "continue_url" -> Seq(s"${appConfig.loginContinueUrl}${request.uri}"),
          "origin" -> Seq(appConfig.appName))
      )
    case _: InsufficientEnrolments => {
      logger.warn(s"user does not have ASA agent enrolment")
      Forbidden
    }
    case _: UnsupportedAuthProvider ???
      logger.warn(s"user has an unsupported auth provider")
      Forbidden
  }

  private def getArn(enrolments: Enrolments): Option[Arn] = {
    enrolments
      .getEnrolment(agentEnrolment)
      .flatMap(_.getIdentifier(agentReferenceNumberIdentifier))
      .map(e => Arn(e.value))
  }
}
