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

import config.AppConfig
import connectors.{AgentAssuranceConnector, AgentServicesAccountConnector}
import helpers.{AgentAssuranceConnectorMocks, AgentServicesAccountConnectorMocks}
import models.SuspensionDetails
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier

class AgentSuspensionServiceSpec
    extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with ScalaFutures with AgentAssuranceConnectorMocks
    with AgentServicesAccountConnectorMocks {

  implicit val mockAgentAssuranceConnector: AgentAssuranceConnector = mock[AgentAssuranceConnector]
  implicit val mockAgentServicesAccountConnector: AgentServicesAccountConnector = mock[AgentServicesAccountConnector]

  "Get agent suspension details from agent services account" should {

    def appBuilder =
      GuiceApplicationBuilder()
        .disable[uk.gov.hmrc.play.bootstrap.metrics.Metrics]
        .configure("auditing.enabled" -> false)
        .configure("metrics.enabled" -> true)
        .configure("metrics.jvm" -> false)

    implicit lazy val fakeApplication: Application = appBuilder.build()

    val appConfig: AppConfig = fakeApplication.injector.instanceOf[AppConfig]

    val service = new AgentSuspensionService(mockAgentAssuranceConnector, mockAgentServicesAccountConnector, appConfig)

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
