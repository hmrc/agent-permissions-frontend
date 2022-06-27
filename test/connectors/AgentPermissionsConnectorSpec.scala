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
import helpers.{AgentPermissionsConnectorMocks, BaseSpec, HttpClientMocks}
import models.DisplayClient
import play.api.Application
import play.api.http.Status.{CREATED, INTERNAL_SERVER_ERROR, OK}
import play.api.libs.json.Json
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.{Client, OptedInReady}
import uk.gov.hmrc.http.{HttpClient, HttpResponse, UpstreamErrorResponse}

class AgentPermissionsConnectorSpec extends BaseSpec with HttpClientMocks with AgentPermissionsConnectorMocks {

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
      connector.getOptInStatus(arn).futureValue shouldBe Some(OptedInReady)
    }
    "return None when there was an error status" in {

      mockHttpGet[HttpResponse](HttpResponse.apply(503, s""" "" """))
      connector.getOptInStatus(arn).futureValue shouldBe None
    }
  }

  "postOptin" should {
    "return Done when successful" in {

      mockHttpPostEmpty[HttpResponse](HttpResponse.apply(CREATED, ""))
      connector.optIn(arn).futureValue shouldBe Done
    }
    "throw an exception when there was a problem" in {

      mockHttpPostEmpty[HttpResponse](HttpResponse.apply(503, ""))
      intercept[UpstreamErrorResponse] {
        await(connector.optIn(arn))
      }
    }
  }

  "postOptOut" should {
    "return Done when successful" in {

      mockHttpPostEmpty[HttpResponse](HttpResponse.apply(CREATED, ""))
      connector.optOut(arn).futureValue shouldBe Done
    }

    "throw an exception when there was a problem" in {

      mockHttpPostEmpty[HttpResponse](HttpResponse.apply(503, ""))
      intercept[UpstreamErrorResponse] {
        await(connector.optOut(arn))
      }
    }
  }


  "post Create Group" should {

    "return Done when response code is 201 CREATED" in {

      val groupRequest = GroupRequest("name of group", None, None)
      val url = s"http://localhost:9447/agent-permissions/arn/${arn.value}/group/create "
      val mockResponse = HttpResponse.apply(CREATED, "response Body")
      mockHttpPost[GroupRequest, HttpResponse](url, groupRequest, mockResponse)
      connector.createGroup(arn)(groupRequest).futureValue shouldBe Done
    }

    "throw an exception for any other HTTP response code" in {

      val groupRequest = GroupRequest("name of group", None, None)
      val url = s"http://localhost:9447/agent-permissions/arn/${arn.value}/group/create "
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      mockHttpPost[GroupRequest, HttpResponse](url, groupRequest, mockResponse)
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.createGroup(arn)(groupRequest))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "GET GroupSummaries" should {

    "return successfully" in {
      //given
      val groupSummaries = Seq(
        GroupSummary("groupId", "groupName", 33, 9)
      )
      val groupSummaryResponse = AccessGroupSummaries(
        groupSummaries,
        Set(Client("taxService~identKey~hmrcRef", "name"))
      )
      val expectedUrl = s"http://localhost:9447/agent-permissions/arn/${arn.value}/groups-information"
      val mockJsonResponseBody = Json.toJson(groupSummaryResponse).toString
      val mockResponse = HttpResponse.apply(OK, mockJsonResponseBody)

      //when
      mockHttpGetWithUrl[HttpResponse](expectedUrl, mockResponse)

      //and
      val expectedTransformedResponse = Some(
        (groupSummaries,
        Seq(DisplayClient("hmrcRef","name","taxService","identKey")))
      )
      //then
      connector.groupsSummaries(arn).futureValue shouldBe expectedTransformedResponse
    }

    "throw an exception for any other HTTP response code" in {

      //given
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      mockHttpGet[HttpResponse](mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.groupsSummaries(arn))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
