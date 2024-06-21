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

package connectors

import com.google.inject.AbstractModule
import helpers.{AgentClientAuthorisationConnectorMocks, BaseSpec, HttpClientMocks}
import play.api.Application
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.{SuspensionDetails, SuspensionDetailsNotFound}
import uk.gov.hmrc.http.{HttpClient, HttpResponse, UpstreamErrorResponse}

class AgentClientAuthorisationConnectorSpec
    extends BaseSpec with HttpClientMocks with AgentClientAuthorisationConnectorMocks {

  implicit val mockHttpClient: HttpClient = mock[HttpClient]

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit =
      bind(classOf[HttpClient]).toInstance(mockHttpClient)
  }

  override implicit lazy val fakeApplication: Application = appBuilder.build()

  val connector: AgentClientAuthorisationConnector =
    fakeApplication.injector.instanceOf[AgentClientAuthorisationConnectorImpl]

  "getSuspensionDetails" should {
    "return SuspensionDetails when OK with valid JSON response received" in {
      val suspendedDetails: SuspensionDetails = SuspensionDetails(suspensionStatus = true, Some(Set("ALL")))

      val jsonString = s"""{
                          |    "suspensionStatus": true,
                          |    "regimes": [
                          |        "ALL"
                          |    ]
                          |}""".stripMargin

      expectHttpClientGET[HttpResponse](HttpResponse.apply(200, jsonString))

      connector.getSuspensionDetails().futureValue shouldBe suspendedDetails
    }
    "return SuspensionDetails when NO_CONTENT received" in {
      val suspensionDetails: SuspensionDetails = SuspensionDetails(suspensionStatus = false, None)

      expectHttpClientGET[HttpResponse](HttpResponse.apply(204, s""" "" """))

      connector.getSuspensionDetails().futureValue shouldBe suspensionDetails
    }

    "throw an SuspensionDetailsNotFound when NOT_FOUND received" in {
      expectHttpClientGET[HttpResponse](HttpResponse.apply(404, s""" "" """))

      intercept[SuspensionDetailsNotFound] {
        await(connector.getSuspensionDetails())
      }
    }
    "throw an UpstreamErrorResponse when unexpected response" in {
      expectHttpClientGET[HttpResponse](HttpResponse.apply(503, s""" "" """))

      intercept[UpstreamErrorResponse] {
        await(connector.getSuspensionDetails())
      }
    }
  }

}
