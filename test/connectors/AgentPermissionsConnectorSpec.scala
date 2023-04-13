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

import akka.Done
import com.google.inject.AbstractModule
import helpers.{AgentPermissionsConnectorMocks, BaseSpec, HttpClientMocks}
import models.{DisplayClient, GroupId}
import org.apache.commons.lang3.RandomStringUtils.randomAlphabetic
import play.api.Application
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.{PaginatedList, PaginationMetaData}
import uk.gov.hmrc.agents.accessgroups.{AgentUser, Client, CustomGroup, GroupSummary, TaxGroup}
import uk.gov.hmrc.agents.accessgroups.optin._
import uk.gov.hmrc.http.{HttpClient, HttpResponse, UpstreamErrorResponse}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate

class AgentPermissionsConnectorSpec extends BaseSpec with HttpClientMocks with AgentPermissionsConnectorMocks {
  
  implicit val mockHttpClient: HttpClient = mock[HttpClient]

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[HttpClient]).toInstance(mockHttpClient)
    }
  }

  override implicit lazy val fakeApplication: Application = appBuilder.build()

  val connector: AgentPermissionsConnector = fakeApplication.injector.instanceOf[AgentPermissionsConnectorImpl]
  val groupName: String = "my fav%& clients"
  val encodedGroupName = URLEncoder.encode(groupName, UTF_8.name)

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
        GroupSummary(GroupId.random(), "groupName", Some(33), 9)
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
        GroupSummary(GroupId.random(), "groupName", Some(33), 9)
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
  
  "GET all group summaries" should {

    "return successfully" in {
      //given
      val groupSummaries = Seq(
        GroupSummary(GroupId.random(), "groupName", Some(33), 9),
        GroupSummary(GroupId.random(), "VAT", None, 9, Some("VAT"))
      )

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
      val paginatedClients = PaginatedList(clients.toSeq, PaginationMetaData(
        lastPage = false,
        firstPage = true,
        totalSize = 1,
        totalPages = 1,
        pageSize = 20,
        currentPageNumber = 1,
        currentPageSize = 1
      ))
      val displayClients = Seq(DisplayClient.fromClient(Client("taxService~identKey~hmrcRef", "name")))
      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/unassigned-clients"
      val mockJsonResponseBody = Json.toJson(paginatedClients).toString
      val mockResponse = HttpResponse.apply(OK, mockJsonResponseBody)

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

      //then
      connector.unassignedClients(arn)(page = 1, pageSize = 20).futureValue shouldBe PaginatedList(
        displayClients,
        PaginationMetaData(
          lastPage = false,
          firstPage = true,
          totalSize = 1,
          totalPages = 1,
          pageSize = 20,
          currentPageNumber = 1,
          currentPageSize = 1
        )
      )
    }

    "throw an exception for any other HTTP response code" in {

      //given
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientGET[HttpResponse](mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.unassignedClients(arn)(page = 1, pageSize = 20))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "GET Paginated clients for Group" should {

    "return successfully" in {
      //given
      val groupId = GroupId.random()
      val clients = Seq(
        Client(enrolmentKey = "HMRC-MTD-IT~MTDITID~XX12345", friendlyName = "Bob"),
        Client(enrolmentKey = "HMRC-MTD-IT~MTDITID~XX12347", friendlyName = "Builder"),
      )
      val meta = PaginationMetaData(
        lastPage = false,
        firstPage = true,
        totalSize = 2,
        totalPages = 1,
        pageSize = 20,
        currentPageNumber = 1,
        currentPageSize = 2
      )
      val paginatedList = PaginatedList[Client](pageContent = clients, paginationMetaData = meta)


      val expectedUrl =
        s"http://localhost:9447/agent-permissions/group/$groupId/clients?page=1&pageSize=20"
      val mockJsonResponseBody = Json.toJson(paginatedList).toString
      val mockResponse = HttpResponse.apply(OK, mockJsonResponseBody)

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

      //and
      val expectedTransformedResponse = paginatedList

      //when
      val response = connector.getPaginatedClientsForCustomGroup(groupId)(1, 20).futureValue

      //then
      response shouldBe expectedTransformedResponse
    }

    "throw an exception for any other HTTP response code" in {

      //given
      val groupId = GroupId.random()
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientGET[HttpResponse](mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.getPaginatedClientsForCustomGroup(groupId)(1, 20))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "GET getPaginatedClientsToAddToGroup" should {

    "return successfully" in {
      //given
      val groupId = GroupId.random()
      val clients = Seq(
        Client(enrolmentKey = "HMRC-MTD-IT~MTDITID~XX12345", friendlyName = "Bob"),
        Client(enrolmentKey = "HMRC-MTD-IT~MTDITID~XX12347", friendlyName = "Builder"),
      )
      val displayClients = clients.map(DisplayClient.fromClient(_))
      val meta = PaginationMetaData(
        lastPage = false,
        firstPage = true,
        totalSize = 2,
        totalPages = 1,
        pageSize = 20,
        currentPageNumber = 1,
        currentPageSize = 2
      )

      val paginatedList = PaginatedList[DisplayClient](pageContent = displayClients, paginationMetaData = meta)
      val groupSummary = GroupSummary(GroupId.random(), "Carrots", Some(1), 1)

      val expectedUrl =
        s"http://localhost:9447/agent-permissions/group/$groupId/clients/add?page=1&pageSize=20"
      val mockJsonResponseBody = Json.toJson((groupSummary, paginatedList)).toString
      val mockResponse = HttpResponse.apply(OK, mockJsonResponseBody)

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

      //and
      val expectedTransformedResponse = paginatedList

      //when
      val response = connector.getPaginatedClientsToAddToGroup(groupId)(1, 20).futureValue

      //then
      response._2 shouldBe expectedTransformedResponse
      response._1 shouldBe groupSummary
    }

    "throw an exception for any other HTTP response code" in {

      //given
      val groupId = GroupId.random()
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientGET[HttpResponse](mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.getPaginatedClientsToAddToGroup(groupId)(1, 20))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "GET Group" should {

    "return successfully" in {
      //given
      val anyDate = LocalDate.of(1970, 1, 1).atStartOfDay()

      val groupId = GroupId.random()
      val agent = AgentUser("agentId", "Bob Builder")
      val accessGroup =
        CustomGroup(GroupId.random(),
          arn,
          "groupName",
          anyDate,
          anyDate,
          agent,
          agent,
          Set(agent),
          Set(Client("service~key~value", "friendly")))

      val expectedUrl =
        s"http://localhost:9447/agent-permissions/groups/$groupId"
      val mockJsonResponseBody = Json.toJson(accessGroup).toString
      val mockResponse = HttpResponse.apply(OK, mockJsonResponseBody)

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

      //and
      val expectedTransformedResponse = Some(accessGroup)

      //then
      connector
        .getGroup(groupId)
        .futureValue shouldBe expectedTransformedResponse
    }

    "throw an exception for any other HTTP response code" in {

      //given
      val groupId = GroupId.random()
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

      val groupId = GroupId.random()
      val groupRequest = UpdateAccessGroupRequest(Some("name of group"))
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId"
      val mockResponse = HttpResponse.apply(OK, "response Body")
      expectHttpClientPATCH[UpdateAccessGroupRequest, HttpResponse](url,
        groupRequest,
        mockResponse)
      connector.updateGroup(groupId, groupRequest).futureValue shouldBe Done
    }

    "throw exception when it fails" in {

      val groupId = GroupId.random()
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

      val groupId = GroupId.random()
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId"
      val mockResponse = HttpResponse.apply(OK, "response Body")
      expectHttpClientDELETE[HttpResponse](url, mockResponse)
      connector.deleteGroup(groupId).futureValue shouldBe Done
    }

    "throw exception when it fails" in {

      val groupId = GroupId.random()
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

      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/access-group-name-check?name=$encodedGroupName"

      val mockResponse = HttpResponse.apply(OK, "")

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

      connector.groupNameCheck(arn, groupName).futureValue shouldBe true
    }

    "return false if the name already exists for the ARN" in {

      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/access-group-name-check?name=$encodedGroupName"

      val mockResponse = HttpResponse.apply(CONFLICT, "")

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

      connector.groupNameCheck(arn, groupName).futureValue shouldBe false
    }

    "throw exception when it fails" in {

      val expectedUrl =
        s"http://localhost:9447/agent-permissions/arn/${arn.value}/access-group-name-check?name=$encodedGroupName"

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
        val expectedUrl = s"http://localhost:9447/agent-permissions/arn-allowed"

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

  "getAvailableTaxServiceClientCount" should {

    "return a Map[String, Int]] when status response is OK" in {

      val expectedData = Map("HMRC-CGT-PD" -> 93, "HMRC-MTD-VAT" -> 34, "HMRC-MTD-IT" -> 123)
      expectHttpClientGET[HttpResponse](HttpResponse.apply(
        OK,
        Json.toJson(expectedData).toString())
      )

      connector.getAvailableTaxServiceClientCount(arn).futureValue shouldBe expectedData
    }

    "throw error when status response is 5xx" in {

      expectHttpClientGET[HttpResponse](HttpResponse.apply(503, ""))

      intercept[UpstreamErrorResponse] {
        await(connector.getAvailableTaxServiceClientCount(arn))
      }
    }

  }

  "getTaxGroupClientCount" should {

    "return a Map[String, Int]] when status response is OK" in {

      val expectedData = Map("HMRC-CGT-PD" -> 93, "HMRC-MTD-VAT" -> 34, "HMRC-MTD-IT" -> 123)
      expectHttpClientGET[HttpResponse](HttpResponse.apply(
        OK,
        Json.toJson(expectedData).toString())
      )

      connector.getTaxGroupClientCount(arn).futureValue shouldBe expectedData
    }

    "throw error when status response is 5xx" in {

      expectHttpClientGET[HttpResponse](HttpResponse.apply(503, ""))

      intercept[UpstreamErrorResponse] {
        await(connector.getTaxGroupClientCount(arn))
      }
    }

  }

  "post Create Tax Service Group" should {

    "return Group Id when response code is OK/CREATED" in {

      val groupId = GroupId.random()
      val payload = CreateTaxServiceGroupRequest("Vat group", None, "MTD-VAT")
      val url = s"http://localhost:9447/agent-permissions/arn/${arn.value}/tax-group"
      val mockResponse = HttpResponse.apply(OK, s""" "$groupId" """)
      expectHttpClientPOST[CreateTaxServiceGroupRequest, HttpResponse](url, payload, mockResponse)
      connector.createTaxServiceGroup(arn)(payload).futureValue shouldBe groupId.toString
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

  "GET Tax Service Group" should {

    "return successfully" in {
      //given
      val anyDate = LocalDate.of(1970, 1, 1).atStartOfDay()

      val groupId = GroupId.random()
      val agent = AgentUser("agentId", "Bob Builder")

      val taxGroup = TaxGroup(GroupId.random(),
        arn,
        "groupName",
        anyDate,
        anyDate,
        agent,
        agent,
        Set(agent),
        "HMRC-MTD-VAT",
        automaticUpdates = true,
        Set.empty)

      val expectedUrl =
        s"http://localhost:9447/agent-permissions/tax-group/$groupId"
      val mockJsonResponseBody = Json.toJson(taxGroup).toString
      val mockResponse = HttpResponse.apply(OK, mockJsonResponseBody)

      expectHttpClientGETWithUrl[HttpResponse](expectedUrl, mockResponse)

      //and
      val expectedTransformedResponse = Some(taxGroup)

      //then
      connector
        .getTaxServiceGroup(groupId)
        .futureValue shouldBe expectedTransformedResponse
    }

    "throw an exception for any other HTTP response code" in {

      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientGET[HttpResponse](mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.getTaxServiceGroup(GroupId.random()))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "DELETE Tax Service Group" should {

    "return Done when response code is OK" in {

      //given
      val groupId = GroupId.random()
      val url = s"http://localhost:9447/agent-permissions/tax-group/$groupId"
      val mockResponse = HttpResponse.apply(OK, "response Body")

      //and
      expectHttpClientDELETE[HttpResponse](url, mockResponse)

      //then
      connector.deleteTaxGroup(groupId).futureValue shouldBe Done
    }

    "throw exception when it fails" in {

      //given
      val groupId = GroupId.random()
      val url = s"http://localhost:9447/agent-permissions/tax-group/$groupId"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "OH NOES!")
      expectHttpClientDELETE[HttpResponse](url, mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.deleteTaxGroup(groupId))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }

  }

  "PATCH update Tax Group" should {

    "return Done when response code is OK" in {

      val groupId = GroupId.random()
      val groupRequest = UpdateTaxServiceGroupRequest(groupName = Some("name of group"))
      val url = s"http://localhost:9447/agent-permissions/tax-group/$groupId"
      val mockResponse = HttpResponse.apply(OK, "response Body")
      expectHttpClientPATCH[UpdateTaxServiceGroupRequest, HttpResponse](url, groupRequest, mockResponse)
      connector.updateTaxGroup(groupId, groupRequest).futureValue shouldBe Done
    }

    "throw exception when it fails" in {

      val groupId = GroupId.random()
      val groupRequest = UpdateTaxServiceGroupRequest(Some("name of group"))
      val url = s"http://localhost:9447/agent-permissions/tax-group/$groupId"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientPATCH[UpdateTaxServiceGroupRequest, HttpResponse](url, groupRequest, mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.updateTaxGroup(groupId, groupRequest))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }

  }

  "PATCH add one team member to a custom group" should {

    "return Done when response code is OK" in {

      //given
      val groupId = GroupId.random()
      val agent = AgentUser("agentId", "Bob Builder")
      val groupRequest = AddOneTeamMemberToGroupRequest(agent)
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId/members/add"
      val mockResponse = HttpResponse.apply(OK, "response Body")
      expectHttpClientPATCH[AddOneTeamMemberToGroupRequest, HttpResponse](url, groupRequest, mockResponse)

      //when
      connector.addOneTeamMemberToGroup(groupId, groupRequest).futureValue shouldBe Done
    }

    "throw exception when it fails" in {

      //given
      val groupId = GroupId.random()
      val agent = AgentUser("agentId", "Bob Builder")
      val groupRequest = AddOneTeamMemberToGroupRequest(agent)
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId/members/add"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientPATCH[AddOneTeamMemberToGroupRequest, HttpResponse](url, groupRequest, mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.addOneTeamMemberToGroup(groupId, groupRequest))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }

  }

  "PATCH add one team member to a tax service group" should {

    "return Done when response code is OK" in {

      //given
      val groupId = GroupId.random()
      val agent = AgentUser("agentId", "Bob Builder")
      val groupRequest = AddOneTeamMemberToGroupRequest(agent)
      val url = s"http://localhost:9447/agent-permissions/tax-group/$groupId/members/add"
      val mockResponse = HttpResponse.apply(OK, "response Body")
      expectHttpClientPATCH[AddOneTeamMemberToGroupRequest, HttpResponse](url, groupRequest, mockResponse)

      //when
      connector.addOneTeamMemberToTaxGroup(groupId, groupRequest).futureValue shouldBe Done
    }

    "throw exception when it fails" in {

      //given
      val groupId = GroupId.random()
      val agent = AgentUser("agentId", "Bob Builder")
      val groupRequest = AddOneTeamMemberToGroupRequest(agent)
      val url = s"http://localhost:9447/agent-permissions/tax-group/$groupId/members/add"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientPATCH[AddOneTeamMemberToGroupRequest, HttpResponse](url, groupRequest, mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.addOneTeamMemberToTaxGroup(groupId, groupRequest))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }

  }

  "DELETE remove client from a custom group" should {

    s"return Done when response code is $NO_CONTENT" in {

      //given
      val clientId = randomAlphabetic(10)
      val groupId = GroupId.random()
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId/clients/$clientId"
      val mockResponse = HttpResponse.apply(NO_CONTENT)
      expectHttpClientDELETE[HttpResponse](url, mockResponse)

      //when
      connector.removeClientFromGroup(groupId, clientId).futureValue shouldBe Done
    }

    "throw exception when it fails" in {

      //given
      val clientId = randomAlphabetic(10)
      val groupId = GroupId.random()
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId/clients/$clientId"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR)
      expectHttpClientDELETE[HttpResponse](url, mockResponse)

      //when
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.removeClientFromGroup(groupId, clientId))
      }
      //then
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }

  }

  "DELETE remove team member from a custom group" should {

    s"return Done when response code is $NO_CONTENT" in {

      //given
      val memberId = randomAlphabetic(10)
      val groupId = GroupId.random()
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId/members/$memberId"
      val mockResponse = HttpResponse.apply(NO_CONTENT)
      expectHttpClientDELETE[HttpResponse](url, mockResponse)

      //when
      connector.removeTeamMemberFromGroup(groupId, memberId, true).futureValue shouldBe Done
    }

    "throw exception when it fails" in {

      //given
      val memberId = randomAlphabetic(10)
      val groupId = GroupId.random()
      val url = s"http://localhost:9447/agent-permissions/groups/$groupId/members/$memberId"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR)
      expectHttpClientDELETE[HttpResponse](url, mockResponse)

      //when
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.removeTeamMemberFromGroup(groupId, memberId, true))
      }
      //then
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }

  }
}
