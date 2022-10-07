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
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Client, OptedInReady}
import uk.gov.hmrc.http.{HttpClient, HttpResponse}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate

class AgentPermissionsConnectorSpec
    extends BaseSpec
    with HttpClientMocks
    with AgentPermissionsConnectorMocks {

  implicit val mockHttpClient: HttpClient = mock[HttpClient]

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[HttpClient]).toInstance(mockHttpClient)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .build()

  val connector: AgentPermissionsConnector =
    fakeApplication.injector.instanceOf[AgentPermissionsConnectorImpl]

  "getOptinStatus" should {
    "return the OptinStatus when valid JSON response received" in {

      mockHttpGet[HttpResponse](
        HttpResponse.apply(200, s""" "Opted-In_READY" """))
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
      connector.optIn(arn, None).futureValue shouldBe Some(Done)
    }
    "return None when there was a problem" in {

      mockHttpPostEmpty[HttpResponse](HttpResponse.apply(503, ""))
      connector.optIn(arn, None).futureValue shouldBe None
    }
  }

  "postOptOut" should {
    "return Done when successful" in {

      mockHttpPostEmpty[HttpResponse](HttpResponse.apply(CREATED, ""))
      connector.optOut(arn).futureValue shouldBe Some(Done)
    }

    "return None when there was a problem" in {

      mockHttpPostEmpty[HttpResponse](HttpResponse.apply(503, ""))
      connector.optOut(arn).futureValue shouldBe None
    }
  }

  "post Create Group" should {

    "return Done when response code is 201 CREATED" in {

      val groupRequest = GroupRequest("name of group", None, None)
      val url =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/groups"
      val mockResponse = HttpResponse.apply(CREATED, "response Body")
      mockHttpPost[GroupRequest, HttpResponse](url, groupRequest, mockResponse)
      connector.createGroup(arn)(groupRequest).futureValue shouldBe Some(Done)
    }

    "return None for any other HTTP response code" in {

      val groupRequest = GroupRequest("name of group", None, None)
      val url =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/groups"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      mockHttpPost[GroupRequest, HttpResponse](url, groupRequest, mockResponse)
      connector.createGroup(arn)(groupRequest).futureValue shouldBe None
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
      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/groups"
      val mockJsonResponseBody = Json.toJson(groupSummaryResponse).toString
      val mockResponse = HttpResponse.apply(OK, mockJsonResponseBody)

      mockHttpGetWithUrl[HttpResponse](expectedUrl, mockResponse)

      //and
      val expectedTransformedResponse = Some(
        (groupSummaries,
         Seq(DisplayClient("hmrcRef", "name", "taxService", "identKey")))
      )

      //then
      connector
        .groupsSummaries(arn)
        .futureValue shouldBe expectedTransformedResponse
    }

    "return None for any other HTTP response code" in {

      //given
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      mockHttpGet[HttpResponse](mockResponse)

      //then
      connector
        .groupsSummaries(arn)
        .futureValue shouldBe None
    }
  }

  "GET GroupSummariesForClient" should {

    val client = Client("123456780", "friendly0")

    "return groups successfully" in {
      //given
      val groupSummaries = Seq(
        GroupSummary("groupId", "groupName", 33, 9)
      )

      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/client/${client.enrolmentKey}/groups"
      val mockJsonResponseBody = Json.toJson(groupSummaries).toString
      val mockResponse = HttpResponse.apply(OK, mockJsonResponseBody)

      mockHttpGetWithUrl[HttpResponse](expectedUrl, mockResponse)

      //and
      val expectedTransformedResponse = Some(
        groupSummaries
      )

      //then
      connector
        .getGroupsForClient(arn, client.enrolmentKey)
        .futureValue shouldBe expectedTransformedResponse
    }

    "return None 404 if no groups found" in {
      //given
      val mockResponse = HttpResponse.apply(NOT_FOUND, "")
      mockHttpGet[HttpResponse](mockResponse)

      //then
      connector
        .getGroupsForClient(arn, client.enrolmentKey)
        .futureValue shouldBe None
    }

    "return None for any other HTTP response code" in {
      //given
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      mockHttpGet[HttpResponse](mockResponse)

      //then
      connector
        .getGroupsForClient(arn, client.enrolmentKey)
        .futureValue shouldBe None
    }
  }

  "GET GroupSummariesForTeamMember" should {

    val agentUser = AgentUser("id", "Name")

    "return groups successfully" in {
      //given
      val groupSummaries = Seq(
        GroupSummary("groupId", "groupName", 33, 9)
      )

      val agentUserId = agentUser.id

      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/team-member/$agentUserId/groups"
      val mockJsonResponseBody = Json.toJson(groupSummaries).toString
      val mockResponse = HttpResponse.apply(OK, mockJsonResponseBody)

      mockHttpGetWithUrl[HttpResponse](expectedUrl, mockResponse)

      //and
      val expectedTransformedResponse = Some(
        groupSummaries
      )

      //then
      connector
        .getGroupsForTeamMember(arn, agentUser)
        .futureValue shouldBe expectedTransformedResponse
    }

    "return None 404 if no groups found" in {
      //given
      val mockResponse = HttpResponse.apply(NOT_FOUND, "")
      mockHttpGet[HttpResponse](mockResponse)

      //then
      connector
        .getGroupsForTeamMember(arn, agentUser)
        .futureValue shouldBe None
    }

    "return None for any other HTTP response code" in {
      //given
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      mockHttpGet[HttpResponse](mockResponse)

      //then
      connector
        .getGroupsForTeamMember(arn, agentUser)
        .futureValue shouldBe None
    }
  }

  "GET Group" should {

    "return successfully" in {
      //given
      val anyDate = LocalDate.of(1970, 1, 1).atStartOfDay()

      val groupId = 234234
      val agent = AgentUser("agentId", "Bob Builder")
      val accessGroup =
        AccessGroup(arn,
                    "groupName",
                    anyDate,
                    anyDate,
                    agent,
                    agent,
                    Some(Set(agent)),
                    Some(Set(Client("service~key~value", "friendly"))))

      val expectedUrl =
        s"http://localhost:9447/agent-permissions/groups/$groupId"
      val mockJsonResponseBody = Json.toJson(accessGroup).toString
      val mockResponse = HttpResponse.apply(OK, mockJsonResponseBody)

      mockHttpGetWithUrl[HttpResponse](expectedUrl, mockResponse)

      //and
      val expectedTransformedResponse = Some(accessGroup)

      //then
      connector
        .getGroup(groupId.toString)
        .futureValue shouldBe expectedTransformedResponse
    }

    "return None for any other HTTP response code" in {

      //given
      val groupId = "234234"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      mockHttpGet[HttpResponse](mockResponse)

      //then
      connector
        .getGroup(groupId.toString)
        .futureValue shouldBe None
    }
  }

  "PATCH update Group" should {

    "return Done when response code is OK" in {

      val groupId = "234234"
      val groupRequest = UpdateAccessGroupRequest(Some("name of group"))
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId"
      val mockResponse = HttpResponse.apply(OK, "response Body")
      mockHttpPATCH[UpdateAccessGroupRequest, HttpResponse](url,
                                                            groupRequest,
                                                            mockResponse)
      connector.updateGroup(groupId, groupRequest).futureValue shouldBe Some(Done)
    }

    "return None when it fails" in {

      val groupId = "234234"
      val groupRequest = UpdateAccessGroupRequest(Some("name of group"))
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      mockHttpPATCH[UpdateAccessGroupRequest, HttpResponse](url,
                                                            groupRequest,
                                                            mockResponse)

      //then
      connector.updateGroup(groupId, groupRequest).futureValue shouldBe None
    }

  }

  "DELETE Group" should {

    "return Done when response code is OK" in {

      val groupId = "234234"
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId"
      val mockResponse = HttpResponse.apply(OK, "response Body")
      mockHttpDELETE[HttpResponse](url, mockResponse)
      connector.deleteGroup(groupId).futureValue shouldBe Some(Done)
    }

    "return None when it fails" in {

      val groupId = "234234"
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "OH NOES!")
      mockHttpDELETE[HttpResponse](url, mockResponse)

      //then
      connector.deleteGroup(groupId).futureValue shouldBe None
    }

  }

  "GET groupNameCheck" should {
    "return true if the name is available for the ARN" in {

      val groupName = URLEncoder.encode("my fav%& clients", UTF_8.name)
      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/group-name-check?name=$groupName"

      val mockResponse = HttpResponse.apply(OK, "")

      mockHttpGetWithUrl[HttpResponse](expectedUrl, mockResponse)

      connector.groupNameCheck(arn, groupName).futureValue shouldBe true
    }

    "return false if the name already exists for the ARN" in {

      val groupName = URLEncoder.encode("my fav%& clients", UTF_8.name)
      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/group-name-check?name=$groupName"

      val mockResponse = HttpResponse.apply(CONFLICT, "")

      mockHttpGetWithUrl[HttpResponse](expectedUrl, mockResponse)

      connector.groupNameCheck(arn, groupName).futureValue shouldBe false
    }

    "return false when it fails" in {

      val groupName = URLEncoder.encode("my fav%& clients", UTF_8.name)
      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/group-name-check?name=$groupName"

      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "OH NOES!")

      mockHttpGetWithUrl[HttpResponse](expectedUrl, mockResponse)

      //then
      connector.groupNameCheck(arn, groupName).futureValue shouldBe false
    }
  }

  "Is Arn Allowed" when {

    s"backend returns $OK" should {
      "return true" in {
        val expectedUrl =
          s"http://localhost:9447/agent-permissions/arn-allowed"

        val mockResponse = HttpResponse.apply(OK, "")

        mockHttpGetWithUrl[HttpResponse](expectedUrl, mockResponse)

        connector.isArnAllowed.futureValue shouldBe true
      }
    }

    s"backend returns non-$OK" should {
      "return false" in {
        val expectedUrl =
          s"http://localhost:9447/agent-permissions/arn-allowed"

        val mockResponse = HttpResponse.apply(FORBIDDEN, "")

        mockHttpGetWithUrl[HttpResponse](expectedUrl, mockResponse)

        connector.isArnAllowed.futureValue shouldBe false
      }
    }
  }
}
