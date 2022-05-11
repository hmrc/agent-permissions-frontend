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

package connectors.mocks

import akka.Done
import connectors.AgentPermissionsConnector
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, OptinStatus}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

trait MockAgentPermissionsConnector extends MockFactory {

  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]

  def stubOptinStatusOk(arn: Arn)(optinStatus: OptinStatus)(implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector.getOptinStatus(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn,*,*)
      .returning(Future successful(Some(optinStatus)))

  def stubPostOptinAccepted(arn: Arn)(implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector.optin(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn,*,*)
      .returning(Future successful Done)

  def stubPostOptinError(arn: Arn)(implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector.optin(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn,*,*)
      .returning(throw UpstreamErrorResponse.apply("error",503))
}
