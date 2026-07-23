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

package services

import connectors.AgentServicesAccountConnector
import helpers.AgentServicesAccountConnectorMocks
import models.SuspensionDetails
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.http.HeaderCarrier

class AgentSuspensionServiceSpec
    extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with ScalaFutures
    with AgentServicesAccountConnectorMocks {

  implicit val mockAgentServicesAccountConnector: AgentServicesAccountConnector = mock[AgentServicesAccountConnector]

  "Get agent suspension details from agent services account" should {

    val service = new AgentSuspensionService(mockAgentServicesAccountConnector)

    implicit val hc: HeaderCarrier = HeaderCarrier()

    "return empty suspension details when not suspended" in {

      expectGetSuspensionDetailsFromAgentServicesAccount()

      val result = service.getSuspensionDetails().futureValue

      result shouldBe SuspensionDetails(suspensionStatus = false, regimes = None)
    }

    "return suspension details when suspended" in {

      expectGetSuspensionDetailsFromAgentServicesAccount(suspensionStatus = true, regimes = Some(Set("AGSV")))

      val result = service.getSuspensionDetails().futureValue

      result shouldBe SuspensionDetails(suspensionStatus = true, regimes = Some(Set("AGSV")))
    }
  }

}
