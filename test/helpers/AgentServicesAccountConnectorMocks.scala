/*
 * Copyright 2026 HM Revenue & Customs
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

import connectors.AgentServicesAccountConnector
import models.SuspensionDetails
import org.scalamock.scalatest.MockFactory
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.Future

trait AgentServicesAccountConnectorMocks extends AnyWordSpec with MockFactory {

  def expectGetSuspensionDetailsFromAgentServicesAccount(suspensionStatus: Boolean = false, regimes: Option[Set[String]] = None)(implicit
                                                                                                         agentServicesAccountConnector: AgentServicesAccountConnector
  ): Unit =
    (agentServicesAccountConnector
      .getSuspensionDetails()(_: HeaderCarrier))
      .expects(*)
      .returning(Future successful SuspensionDetails(suspensionStatus, regimes))

  def expectGetSuspensionDetailsErrorFromAgentServicesAccount(implicit agentServicesAccountConnector: AgentServicesAccountConnector): Unit =
    (agentServicesAccountConnector
      .getSuspensionDetails()(_: HeaderCarrier))
      .expects(*)
      .throwing(UpstreamErrorResponse.apply("error", 503))

}
