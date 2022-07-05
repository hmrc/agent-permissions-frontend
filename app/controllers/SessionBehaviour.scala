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

import connectors.AgentPermissionsConnector
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Results.Redirect
import play.api.mvc.{Request, Result}
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.{ExecutionContext, Future}

trait SessionBehaviour {

  val sessionCacheRepository: SessionCacheRepository
  val agentPermissionsConnector: AgentPermissionsConnector

  def isEligibleToOptIn(arn: Arn)(body: OptinStatus => Future[Result])(
      implicit request: Request[_],
      hc: HeaderCarrier,
      ec: ExecutionContext): Future[Result] =
    eligibleFor(controllers.isEligibleToOptIn)(arn)(body)(request, hc, ec)

  def isOptedIn(arn: Arn)(body: OptinStatus => Future[Result])(
      implicit request: Request[_],
      hc: HeaderCarrier,
      ec: ExecutionContext): Future[Result] =
    eligibleFor(controllers.isOptedIn)(arn)(body)(request, hc, ec)

  def isOptedInComplete(arn: Arn)(body: OptinStatus => Future[Result])(
      implicit request: Request[_],
      hc: HeaderCarrier,
      ec: ExecutionContext): Future[Result] =
    eligibleFor(controllers.isOptedInComplete)(arn)(body)(request, hc, ec)

  def isOptedOut(arn: Arn)(body: OptinStatus => Future[Result])(
      implicit request: Request[_],
      hc: HeaderCarrier,
      ec: ExecutionContext): Future[Result] =
    eligibleFor(controllers.isOptedOut)(arn)(body)(request, hc, ec)

  def isOptedInWithSessionItem[T](dataKey: DataKey[T])(arn: Arn)(
      body: Option[T] => Future[Result])(
      implicit reads: Reads[T],
      request: Request[_],
      hc: HeaderCarrier,
      ec: ExecutionContext): Future[Result] = {
    sessionCacheRepository.getFromSession[OptinStatus](OPTIN_STATUS).flatMap {
      case Some(status) if status == OptedInReady =>
        sessionCacheRepository
          .getFromSession[T](dataKey)
          .flatMap(data => body(data))
      case _ =>
        agentPermissionsConnector.getOptInStatus(arn).flatMap {
          case Some(status) if status == OptedInReady =>
            sessionCacheRepository
              .putSession[OptinStatus](OPTIN_STATUS, status)
              .flatMap(_ => body(None))
          case _ => Redirect(routes.RootController.start).toFuture
        }
    }
  }

  private def eligibleFor(predicate: OptinStatus => Boolean)(arn: Arn)(
      body: OptinStatus => Future[Result])(
      implicit request: Request[_],
      hc: HeaderCarrier,
      ec: ExecutionContext): Future[Result] = {
    sessionCacheRepository
      .getFromSession[OptinStatus](OPTIN_STATUS)
      .flatMap {
        case Some(status) if predicate(status) => body(status)
        case Some(_)                           => Redirect(routes.RootController.start.url).toFuture
        case None =>
          initialiseSession(arn)
            .flatMap(_ =>
              sessionCacheRepository.getFromSession[OptinStatus](OPTIN_STATUS))
            .flatMap {
              case Some(status) if predicate(status) => body(status)
              case Some(_)                           => Redirect(routes.RootController.start).toFuture
              case None =>
                throw new RuntimeException(
                  s"opt-in status could not be found for ${arn.value}")
            }
      }
  }

  private def initialiseSession(arn: Arn)(implicit request: Request[_],
                                          writes: Writes[OptinStatus],
                                          hc: HeaderCarrier,
                                          ec: ExecutionContext) =
    agentPermissionsConnector.getOptInStatus(arn).flatMap {
      case Some(status) =>
        sessionCacheRepository.putSession[OptinStatus](OPTIN_STATUS, status)
      case None =>
        throw new RuntimeException(
          s"could not initialise session because opt-In status was not returned for ${arn.value}")
    }

  def clearSession()(implicit request: Request[_],
                     hc: HeaderCarrier,
                     ec: ExecutionContext) = {
    Future.sequence(
      sessionKeys.map(sessionCacheRepository.deleteFromSession(_)))
  }

}
