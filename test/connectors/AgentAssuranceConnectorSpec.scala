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
import config.AppConfig
import helpers.{AgentAssuranceConnectorMocks, BaseSpec, HttpClientMocksV2}
import play.api.Application
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.{SuspensionDetails, SuspensionDetailsNotFound}
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HttpResponse, StringContextOps, UpstreamErrorResponse}

class AgentAssuranceConnectorSpec extends BaseSpec with HttpClientMocksV2 with AgentAssuranceConnectorMocks {

  implicit val mockHttpClient: HttpClientV2 = mock[HttpClientV2]
  implicit val requestBuilder: RequestBuilder = mock[RequestBuilder]
  val appConfig = fakeApplication.injector.instanceOf[AppConfig]

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit =
      bind(classOf[HttpClientV2]).toInstance(mockHttpClient)
  }

  override implicit lazy val fakeApplication: Application = appBuilder.build()

  val connector: AgentAssuranceConnector =
    fakeApplication.injector.instanceOf[AgentAssuranceConnector]

  "getSuspensionDetails" should {
    "return SuspensionDetails when OK with valid JSON response received" in {
      val suspendedDetails: SuspensionDetails = SuspensionDetails(suspensionStatus = true, Some(Set("ALL")))

      val jsonString = s"""{
                          |   "suspensionDetails":{
                          |    "suspensionStatus": true,
                          |    "regimes": [
                          |        "ALL"
                          |    ]
                          |}}""".stripMargin

      mockHttpGet(url"${appConfig.agentAssuranceBaseUrl}/agent-assurance/agent-record-with-checks")
      mockRequestBuilderExecute(HttpResponse.apply(200, jsonString))

      connector.getSuspensionDetails().futureValue shouldBe suspendedDetails
    }
    "return SuspensionDetails when NO_CONTENT received" in {
      val suspensionDetails: SuspensionDetails = SuspensionDetails(suspensionStatus = false, None)

      mockHttpGet(url"${appConfig.agentAssuranceBaseUrl}/agent-assurance/agent-record-with-checks")
      mockRequestBuilderExecute(HttpResponse.apply(204, s""" "" """))

      connector.getSuspensionDetails().futureValue shouldBe suspensionDetails
    }

    "throw an SuspensionDetailsNotFound when NOT_FOUND received" in {
      mockHttpGet(url"${appConfig.agentAssuranceBaseUrl}/agent-assurance/agent-record-with-checks")
      mockRequestBuilderExecute(HttpResponse.apply(404, s""" "" """))

      intercept[SuspensionDetailsNotFound] {
        await(connector.getSuspensionDetails())
      }
    }
    "throw an UpstreamErrorResponse when unexpected response" in {
      mockHttpGet(url"${appConfig.agentAssuranceBaseUrl}/agent-assurance/agent-record-with-checks")
      mockRequestBuilderExecute(HttpResponse.apply(500, s""" "" """))

      intercept[UpstreamErrorResponse] {
        await(connector.getSuspensionDetails())
      }
    }
  }

}
