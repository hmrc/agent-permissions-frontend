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
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Client, OptedInReady}
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroupSummary => GroupSummary}
import uk.gov.hmrc.http.{HttpClient, HttpResponse, UpstreamErrorResponse}

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

      expectHttpClientGET[HttpResponse](
        HttpResponse.apply(200, s""" "Opted-In_READY" """))
      connector.getOptInStatus(arn).futureValue shouldBe Some(OptedInReady)
    }
    "return None when there was an error status" in {

      expectHttpClientGET[HttpResponse](HttpResponse.apply(503, s""" "" """))
      connector.getOptInStatus(arn).futureValue shouldBe None
    }
  }

  "postOptin" should {
    "return Done when successful" in {

      expectHttpClientPOSTEmpty[HttpResponse](HttpResponse.apply(CREATED, ""))
      connector.optIn(arn, None).futureValue shouldBe Done
    }

    "return Done when already opted in" in {
      expectHttpClientPOSTEmpty[HttpResponse](HttpResponse.apply(CONFLICT, ""))
      connector.optIn(arn, None).futureValue shouldBe Done
    }

    "throw an exception when there was a problem" in {

      expectHttpClientPOSTEmpty[HttpResponse](HttpResponse.apply(503, ""))
      intercept[UpstreamErrorResponse] {
        await(connector.optIn(arn, None))
      }
    }
  }

  "postOptOut" should {
    "return Done when successful" in {

      expectHttpClientPOSTEmpty[HttpResponse](HttpResponse.apply(CREATED, ""))
      connector.optOut(arn).futureValue shouldBe Done
    }

    "return Done when already opted out" in {
      expectHttpClientPOSTEmpty[HttpResponse](HttpResponse.apply(CONFLICT, ""))
      connector.optOut(arn).futureValue shouldBe Done
    }

    "throw an exception when there was a problem" in {

      expectHttpClientPOSTEmpty[HttpResponse](HttpResponse.apply(503, ""))
      intercept[UpstreamErrorResponse] {
        await(connector.optOut(arn))
      }
    }
  }

  "post Create Group" should {

    "return Done when response code is 201 CREATED" in {

      val groupRequest = GroupRequest("name of group", None, None)
      val url =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/groups"
      val mockResponse = HttpResponse.apply(CREATED, "response Body")
      expectHttpClientPOST[GroupRequest, HttpResponse](url, groupRequest, mockResponse)
      connector.createGroup(arn)(groupRequest).futureValue shouldBe Done
    }

    "throw an exception for any other HTTP response code" in {

      val groupRequest = GroupRequest("name of group", None, None)
      val url =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/groups"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientPOST[GroupRequest, HttpResponse](url, groupRequest, mockResponse)
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.createGroup(arn)(groupRequest))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "GET GroupSummariesForClient" should {

    val client = Client("123456780", "friendly0")

    "return groups successfully" in {
      //given
      val groupSummaries = Seq(
        GroupSummary("groupId", "groupName", Some(33), 9, isCustomGroup = true)
      )

      val expectedUrl = s"http://localhost:9447/agent-permissions/arn/${arn.value}/client/${client.enrolmentKey}/groups"
      val mockJsonResponseBody = Json.toJson(groupSummaries).toString
      val mockResponse = HttpResponse.apply(OK, mockJsonResponseBody)

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

      //then
      connector.getGroupsForClient(arn, client.enrolmentKey).futureValue shouldBe groupSummaries
    }

    "return None 404 if no groups found" in {
      //given
      val mockResponse = HttpResponse.apply(NOT_FOUND, "")
      expectHttpClientGET[HttpResponse](mockResponse)

      //then
      connector.getGroupsForClient(arn, client.enrolmentKey).futureValue shouldBe Seq.empty
    }

    "throw an exception for any other HTTP response code" in {
      //given
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientGET[HttpResponse](mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.getGroupsForClient(arn, client.enrolmentKey))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "GET GroupSummariesForTeamMember" should {

    val agentUser = AgentUser("id", "Name")

    "return groups successfully" in {
      //given
      val groupSummaries = Seq(
        GroupSummary("groupId", "groupName", Some(33), 9, isCustomGroup = true)
      )

      val agentUserId = agentUser.id

      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/team-member/$agentUserId/groups"
      val mockJsonResponseBody = Json.toJson(groupSummaries).toString
      val mockResponse = HttpResponse.apply(OK, mockJsonResponseBody)

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

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
      expectHttpClientGET[HttpResponse](mockResponse)

      //then
      connector
        .getGroupsForTeamMember(arn, agentUser)
        .futureValue shouldBe None
    }

    "throw an exception for any other HTTP response code" in {
      //given
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientGET[HttpResponse](mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.getGroupsForTeamMember(arn, agentUser))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "GET custom group summaries" should {

    "return successfully" in {
      //given
      val groupSummaries = Seq(GroupSummary("groupId", "groupName", Some(33), 9, isCustomGroup = true))

      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/groupsOnly"
      val mockJsonResponseBody = Json.toJson(groupSummaries).toString
      val mockResponse = HttpResponse.apply(OK, mockJsonResponseBody)

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

      //then
      connector.groups(arn).futureValue shouldBe groupSummaries
    }

    "throw an exception for any other HTTP response code" in {

      //given
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientGET[HttpResponse](mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.groups(arn))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "GET all group summaries" should {

    "return successfully" in {
      //given
      val groupSummaries = Seq(GroupSummary("groupId", "groupName", Some(33), 9, isCustomGroup = true), GroupSummary("groupId2", "VAT", None, 9, isCustomGroup = false))

      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/all-groups"
      val mockJsonResponseBody = Json.toJson(groupSummaries).toString
      val mockResponse = HttpResponse.apply(OK, mockJsonResponseBody)

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

      //then
      connector.getGroupSummaries(arn).futureValue shouldBe groupSummaries
    }

    "throw an exception for any other HTTP response code" in {

      //given
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientGET[HttpResponse](mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.getGroupSummaries(arn))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "GET unassigned clients" should {

    "return successfully" in {
      //given
      val clients = Set(Client("taxService~identKey~hmrcRef", "name"))
      val displayClients = Seq(DisplayClient.fromClient(Client("taxService~identKey~hmrcRef", "name")))
      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/groups"
      val mockJsonResponseBody = Json.toJson(clients).toString
      val mockResponse = HttpResponse.apply(OK, mockJsonResponseBody)

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

      //then
      connector.unassignedClients(arn).futureValue shouldBe displayClients
    }

    "throw an exception for any other HTTP response code" in {

      //given
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientGET[HttpResponse](mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.unassignedClients(arn))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
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

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

      //and
      val expectedTransformedResponse = Some(accessGroup)

      //then
      connector
        .getGroup(groupId.toString)
        .futureValue shouldBe expectedTransformedResponse
    }

    "throw an exception for any other HTTP response code" in {

      //given
      val groupId = "234234"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientGET[HttpResponse](mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.getGroup(groupId))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "PATCH update Group" should {

    "return Done when response code is OK" in {

      val groupId = "234234"
      val groupRequest = UpdateAccessGroupRequest(Some("name of group"))
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId"
      val mockResponse = HttpResponse.apply(OK, "response Body")
      expectHttpClientPATCH[UpdateAccessGroupRequest, HttpResponse](url,
        groupRequest,
        mockResponse)
      connector.updateGroup(groupId, groupRequest).futureValue shouldBe Done
    }

    "throw exception when it fails" in {

      val groupId = "234234"
      val groupRequest = UpdateAccessGroupRequest(Some("name of group"))
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientPATCH[UpdateAccessGroupRequest, HttpResponse](url,
        groupRequest,
        mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.updateGroup(groupId, groupRequest))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }

  }

  "DELETE Group" should {

    "return Done when response code is OK" in {

      val groupId = "234234"
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId"
      val mockResponse = HttpResponse.apply(OK, "response Body")
      expectHttpClientDELETE[HttpResponse](url, mockResponse)
      connector.deleteGroup(groupId).futureValue shouldBe Done
    }

    "throw exception when it fails" in {

      val groupId = "234234"
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "OH NOES!")
      expectHttpClientDELETE[HttpResponse](url, mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.deleteGroup(groupId))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }

  }

  "GET groupNameCheck" should {
    "return true if the name is available for the ARN" in {

      val groupName = URLEncoder.encode("my fav%& clients", UTF_8.name)
      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/group-name-check?name=$groupName"

      val mockResponse = HttpResponse.apply(OK, "")

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

      connector.groupNameCheck(arn, groupName).futureValue shouldBe true
    }

    "return false if the name already exists for the ARN" in {

      val groupName = URLEncoder.encode("my fav%& clients", UTF_8.name)
      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/group-name-check?name=$groupName"

      val mockResponse = HttpResponse.apply(CONFLICT, "")

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

      connector.groupNameCheck(arn, groupName).futureValue shouldBe false
    }

    "throw exception when it fails" in {

      val groupName = URLEncoder.encode("my fav%& clients", UTF_8.name)
      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/group-name-check?name=$groupName"

      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "OH NOES!")

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.groupNameCheck(arn, groupName))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "Is Arn Allowed" when {

    s"backend returns $OK" should {
      "return true" in {
        val expectedUrl =
          s"http://localhost:9447/agent-permissions/arn-allowed"

        val mockResponse = HttpResponse.apply(OK, "")

        expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

        connector.isArnAllowed.futureValue shouldBe true
      }
    }

    s"backend returns non-$OK" should {
      "return false" in {
        val expectedUrl =
          s"http://localhost:9447/agent-permissions/arn-allowed"

        val mockResponse = HttpResponse.apply(FORBIDDEN, "")

        expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

        connector.isArnAllowed.futureValue shouldBe false
      }
    }
  }

  "post Create Tax Service Group" should {

    "return Group Id when response code is OK/CREATED" in {

      val payload = CreateTaxServiceGroupRequest("Vat group", None, "MTD-VAT")
      val url = s"http://localhost:9447/agent-permissions/arn/${arn.value}/tax-group"
      val mockResponse = HttpResponse.apply(OK, s""" "234234" """)
      expectHttpClientPOST[CreateTaxServiceGroupRequest, HttpResponse](url, payload, mockResponse)
      connector.createTaxServiceGroup(arn)(payload).futureValue shouldBe "234234"
    }

    "throw an exception for any other HTTP response code" in {

      val payload = CreateTaxServiceGroupRequest("name of group", None, "whatever")
      val url = s"http://localhost:9447/agent-permissions/arn/${arn.value}/tax-group"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, s""" "fail" """)
      expectHttpClientPOST[CreateTaxServiceGroupRequest, HttpResponse](url, payload, mockResponse)
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.createTaxServiceGroup(arn)(payload))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
