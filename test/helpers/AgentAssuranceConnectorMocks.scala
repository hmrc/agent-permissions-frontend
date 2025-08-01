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

import connectors.AgentAssuranceConnector
import org.scalamock.scalatest.MockFactory
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.agentmtdidentifiers.model.SuspensionDetails
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

trait AgentAssuranceConnectorMocks extends AnyWordSpec with MockFactory {

  def expectGetSuspensionDetails(suspensionStatus: Boolean = false, regimes: Option[Set[String]] = None)(implicit
    agentAssuranceConnector: AgentAssuranceConnector
  ): Unit =
    (agentAssuranceConnector
      .getSuspensionDetails()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(Future successful SuspensionDetails(suspensionStatus, regimes))

  def expectGetSuspensionDetailsError(implicit agentAssuranceConnector: AgentAssuranceConnector): Unit =
    (agentAssuranceConnector
      .getSuspensionDetails()(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .throwing(UpstreamErrorResponse.apply("error", 503))

}
