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

package controllers.actions

import config.AppConfig
import connectors.{AgentAssuranceConnector, AgentPermissionsConnector}
import controllers._
import play.api.libs.json.Reads
import play.api.mvc.Results.{Forbidden, Redirect}
import play.api.mvc.{Request, RequestHeader, Result}
import play.api.{Configuration, Environment, Logging}
import services.SessionCacheService
import sttp.model.Uri.UriContext
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, SuspensionDetails}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{allEnrolments, credentialRole}
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthAction @Inject() (
  val authConnector: AuthConnector,
  val env: Environment,
  val config: Configuration,
  agentPermissionsConnector: AgentPermissionsConnector,
  agentAssuranceConnector: AgentAssuranceConnector,
  sessionCacheService: SessionCacheService
) extends AuthorisedFunctions with Logging {

  private val agentEnrolment = "HMRC-AS-AGENT"
  private val agentReferenceNumberIdentifier = "AgentReferenceNumber"

  def isAuthorisedAgent(
    body: Arn => Future[Result]
  )(implicit ec: ExecutionContext, request: Request[_], appConfig: AppConfig): Future[Result] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    authorised(AuthProviders(GovernmentGateway) and Enrolment(agentEnrolment))
      .retrieve(allEnrolments and credentialRole) { case enrols ~ credRole =>
        getArn(enrols) match {
          case Some(arn) =>
            withSuspendedCheck { _ =>
              credRole match {
                case Some(User) | Some(Admin) =>
                  agentPermissionsConnector.isArnAllowed flatMap { isArnAllowed =>
                    if (isArnAllowed) {
                      body(arn)
                    } else {
                      logger.warn("ARN is not on allowed list")
                      Future.successful(Forbidden)
                    }
                  }
                case _ =>
                  logger.warn(s"Invalid credential role $credRole")
                  Future.successful(Forbidden)
              }
            }
          case None =>
            logger.warn(s"No $agentReferenceNumberIdentifier in enrolment")
            Future.successful(Forbidden)
        }
      }
      .recover(handleFailure)
  }

  def isAuthorisedAssistant(
    body: Arn => Future[Result]
  )(implicit ec: ExecutionContext, request: Request[_], appConfig: AppConfig): Future[Result] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    authorised(AuthProviders(GovernmentGateway) and Enrolment(agentEnrolment))
      .retrieve(allEnrolments and credentialRole) { case enrols ~ credRole =>
        getArn(enrols) match {
          case Some(arn) =>
            withSuspendedCheck { _ =>
              credRole match {
                case Some(Assistant) =>
                  agentPermissionsConnector.isArnAllowed flatMap { isArnAllowed =>
                    if (isArnAllowed) {
                      body(arn)
                    } else {
                      logger.warn("ARN is not on allowed list")
                      Future.successful(Forbidden)
                    }
                  }
                case _ =>
                  logger.warn(s"Invalid credential role $credRole - assistant only")
                  Future.successful(Forbidden)
              }
            }
          case None =>
            logger.warn(s"No $agentReferenceNumberIdentifier in enrolment")
            Future.successful(Forbidden)
        }
      }
      .recover(handleFailure)
  }

  def handleFailure(implicit request: RequestHeader, appConfig: AppConfig): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession =>
      val continueUrl = uri"${appConfig.selfExternalUrl + request.uri}"
      val signInUrl = uri"${appConfig.signInUrl}?${Map(
          "continue_url" -> continueUrl,
          "origin"       -> appConfig.appName
        )}"
      Redirect(signInUrl.toString)
    case _: InsufficientEnrolments =>
      logger.warn(s"user does not have ASA agent enrolment")
      Forbidden
    case _: UnsupportedAuthProvider =>
      logger.warn(s"user has an unsupported auth provider")
      Forbidden
  }

  private def getArn(enrolments: Enrolments): Option[Arn] =
    enrolments
      .getEnrolment(agentEnrolment)
      .flatMap(_.getIdentifier(agentReferenceNumberIdentifier))
      .map(e => Arn(e.value))

  private def withSuspendedCheck(body: Option[Boolean] => Future[Result])(implicit
    reads: Reads[Boolean],
    request: Request[_],
    hc: HeaderCarrier,
    ec: ExecutionContext,
    appConfig: AppConfig
  ): Future[Result] =
    sessionCacheService.get[Boolean](SUSPENSION_STATUS).flatMap {
      case Some(status) if !status =>
        body(None)
      case _ =>
        agentAssuranceConnector
          .getSuspensionDetails()
          .flatMap {
            case suspensionDetails: SuspensionDetails if !suspensionDetails.suspensionStatus =>
              sessionCacheService
                .put[Boolean](SUSPENSION_STATUS, suspensionDetails.suspensionStatus)
                .flatMap(_ => body(None))
            case _ =>
              logger.info("Suspended agent - redirecting to ASA")
              Redirect(s"${appConfig.agentServicesAccountExternalUrl}/agent-services-account/account-limited").toFuture
          }
    }

}
