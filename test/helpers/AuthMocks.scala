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

package helpers

import config.AppConfig
import connectors.AgentPermissionsConnector
import controllers.actions.AuthAction
import org.scalamock.handlers.{CallHandler2, CallHandler4}
import org.scalamock.scalatest.MockFactory
import org.scalatest.wordspec.AnyWordSpec
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AuthConnector, CredentialRole, Enrolments}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait AuthMocks extends AnyWordSpec with MockFactory {

  type GrantAccess = Enrolments ~ Option[CredentialRole]

  def expectAuthorisationGrantsAccess(response: GrantAccess)(implicit authConnector: AuthConnector): Unit =
    (authConnector
      .authorise(_: Predicate, _: Retrieval[GrantAccess])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future successful response)

  def expectAuthorisationFails(throwable: Throwable)(implicit authConnector: AuthConnector): Unit =
    (authConnector
      .authorise(_: Predicate, _: Retrieval[GrantAccess])(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future failed throwable)

  def expectIsArnAllowed(allowed: Boolean)(implicit
    agentPermissionsConnector: AgentPermissionsConnector
  ): CallHandler2[HeaderCarrier, ExecutionContext, Future[Boolean]] =
    (agentPermissionsConnector
      .isArnAllowed(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(Future successful allowed)

  def expectIsAuthorisedAgent(futureResult: Future[Result])(implicit
    authAction: AuthAction
  ): CallHandler4[Arn => Future[Result], ExecutionContext, Request[_], AppConfig, Future[Result]] =
    (authAction
      .isAuthorisedAgent(_: Arn => Future[Result])(_: ExecutionContext, _: Request[_], _: AppConfig))
      .expects(*, *, *, *)
      .returning(futureResult)

}
