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

import connectors.mocks.{MockAgentPermissionsConnector, MockHttpClient}
import helpers.BaseISpec
import uk.gov.hmrc.agentmtdidentifiers.model.OptedInReady
import uk.gov.hmrc.http.HttpResponse

class AgentPermissionsConnectorISpec extends BaseISpec with MockHttpClient with MockAgentPermissionsConnector {

  val connector = new AgentPermissionsConnectorImpl(mockHttpClient)

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

}
