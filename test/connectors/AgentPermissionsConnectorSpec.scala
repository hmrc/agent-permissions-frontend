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

package connectors

import akka.Done
import com.google.inject.AbstractModule
import helpers.{AgentPermissionsConnectorMocks, BaseISpec, HttpClientMocks}
import play.api.Application
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.OptedInReady
import uk.gov.hmrc.http.{HttpClient, HttpResponse, UpstreamErrorResponse}

class AgentPermissionsConnectorSpec extends BaseISpec with HttpClientMocks with AgentPermissionsConnectorMocks {

  implicit val mockHttpClient: HttpClient = mock[HttpClient]

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[HttpClient]).toInstance(mockHttpClient)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .build()

  val connector: AgentPermissionsConnector = fakeApplication.injector.instanceOf[AgentPermissionsConnectorImpl]

  "getOptinStatus" should {
    "return the OptinStatus when valid JSON response received" in {

      mockHttpGet[HttpResponse](HttpResponse.apply(200, s""" "Opted-In_READY" """))
      connector.getOptinStatus(arn).futureValue shouldBe Some(OptedInReady)
    }
    "return None when there was an error status" in {

      mockHttpGet[HttpResponse](HttpResponse.apply(503, s""" "" """))
      connector.getOptinStatus(arn).futureValue shouldBe None
    }
  }

  "postOptin" should {
    "return Done when successful" in {

      mockHttpPost[HttpResponse](HttpResponse.apply(201, ""))
      connector.optin(arn).futureValue shouldBe Done
    }
    "throw an exception when there was a problem" in {

      mockHttpPost[HttpResponse](HttpResponse.apply(503, ""))
      intercept[UpstreamErrorResponse]{
        await(connector.optin(arn))
      }
    }
  }
}
