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

import connectors.AgentPermissionsConnector
import controllers.*
import models.Arn
import models.accessgroups.optin.{OptedInReady, OptinStatus}
import play.api.libs.json.Reads
import play.api.mvc.Results.Redirect
import play.api.mvc.{Request, Result}
import play.api.{Configuration, Environment, Logging}
import services.SessionCacheService
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.DataKey

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OptInStatusAction @Inject() (
  val authConnector: AuthConnector,
  val env: Environment,
  val config: Configuration,
  val sessionCacheService: SessionCacheService,
  val agentPermissionsConnector: AgentPermissionsConnector
) extends Logging {

  def isEligibleToOptIn(arn: Arn)(
    body: OptinStatus => Future[Result]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    eligibleFor(controllers.isEligibleToOptIn)(arn)(body)(using hc, ec)

  def isOptedIn(arn: Arn)(
    body: OptinStatus => Future[Result]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    eligibleFor(controllers.isOptedIn)(arn)(body)(using hc, ec)

  def isOptedInComplete(arn: Arn)(
    body: OptinStatus => Future[Result]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    eligibleFor(controllers.isOptedInComplete)(arn)(body)(using hc, ec)

  def isOptedOut(arn: Arn)(
    body: OptinStatus => Future[Result]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    eligibleFor(controllers.isOptedOut)(arn)(body)(using hc, ec)

  def isOptedInWithSessionItem[T](dataKey: DataKey[T])(arn: Arn)(
    body: Option[T] => Future[Result]
  )(implicit reads: Reads[T], request: Request[?], hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    agentPermissionsConnector
      .getOptInStatus(arn)
      .flatMap:
        case Some(OptedInReady) => sessionCacheService.get[T](dataKey).flatMap(body(_))
        case _                  => Redirect(routes.RootController.start()).toFuture

  private def eligibleFor(predicate: OptinStatus => Boolean)(arn: Arn)(
    body: OptinStatus => Future[Result]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    agentPermissionsConnector
      .getOptInStatus(arn)
      .flatMap:
        case Some(status) if predicate(status) => body(status)
        case Some(_)                           => Redirect(routes.RootController.start()).toFuture
        case None                              =>
          throw new RuntimeException(s"opt-in status could not be found for ${arn.value}")
}
