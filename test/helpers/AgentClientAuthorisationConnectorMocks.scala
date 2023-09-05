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


import connectors.AgentClientAuthorisationConnector
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.agentmtdidentifiers.model.{SuspensionDetails, SuspensionDetailsNotFound}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

trait AgentClientAuthorisationConnectorMocks extends MockFactory {

  def expectGetSuspensionDetails(suspensionStatus: Boolean = false, regimes: Option[Set[String]] = None)(
      implicit agentClientAuthConnector: AgentClientAuthorisationConnector): Unit =
    (agentClientAuthConnector
      .getSuspensionDetails()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(Future successful SuspensionDetails(suspensionStatus, regimes))

  def expectGetSuspensionDetailsNotFound(
                                             implicit agentClientAuthConnector: AgentClientAuthorisationConnector): Unit =
    (agentClientAuthConnector
      .getSuspensionDetails()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .throwing(SuspensionDetailsNotFound("No record found for this agent"))

  def expectGetSuspensionDetailsError(
      implicit agentClientAuthConnector: AgentClientAuthorisationConnector): Unit =
    (agentClientAuthConnector
      .getSuspensionDetails()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .throwing(UpstreamErrorResponse.apply("error", 503))

}
