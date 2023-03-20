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

package services

import akka.Done
import connectors._
import controllers.{CLIENT_FILTER_INPUT, CLIENT_SEARCH_INPUT, CUSTOM_GROUP, GroupType, NAME_OF_GROUP_CREATED, SELECTED_CLIENTS, SELECTED_TEAM_MEMBERS, creatingGroupKeys}
import helpers.BaseSpec
import models.TeamMember.toAgentUser
import models.{DisplayClient, TeamMember}
import org.apache.commons.lang3.RandomStringUtils
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class GroupServiceSpec extends BaseSpec {

  implicit val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector =
    mock[AgentUserClientDetailsConnector]
  implicit val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit val mockSessionCacheService: SessionCacheService = mock[SessionCacheService]

  val service = new GroupServiceImpl(
    mockAgentUserClientDetailsConnector,
    mockSessionCacheService,
    mockAgentPermissionsConnector
  )

  val userDetails: Seq[UserDetails] = (1 to 5)
    .map { i =>
      UserDetails(Some(s"John $i"), Some("User"), Some(s"John $i name"), Some(s"john$i@abc.com"))
    }

  val fakeClients: Seq[Client] = (1 to 8)
    .map(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly name $i"))

  val displayClients: Seq[DisplayClient] =
    fakeClients.map(DisplayClient.fromClient(_))

  "get group summaries" should {
    "Return groups from agentPermissionsConnector" in {

      //given
      val groupSummaries = Seq(
        GroupSummary("2", "Carrots", Some(1), 1),
        GroupSummary("3", "Potatoes", Some(1), 1),
      )

      (mockAgentPermissionsConnector
        .getGroupSummaries(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future successful groupSummaries).once()

      //when
      val summaries = await(service.getGroupSummaries(arn))

      //then
      summaries shouldBe groupSummaries
    }
  }

  "get a custom group summary" should {
    "Return summary from agentPermissionsConnector" in {

      //given
      val groupSummary = GroupSummary("2", "Carrots", Some(1), 1)

      (mockAgentPermissionsConnector
        .getCustomSummary(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(groupSummary.groupId, *, *)
        .returning(Future successful Some(groupSummary)).once()

      //when
      val summary = await(service.getCustomSummary(groupSummary.groupId))

      //then
      summary shouldBe Some(groupSummary)
    }
  }

  "get paginated group summaries" should {
    "Return first page of summaries as paginated list" in {
      //given
      val groupSummaries = (1 to 8)
        .map { i =>
          GroupSummary(s"$i", s"Carrots$i", Some(1), 1)
        }

      (mockAgentPermissionsConnector
        .getGroupSummaries(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future successful groupSummaries).once()

      //when
      val summaries = await(service.getPaginatedGroupSummaries(arn)())

      //then
      summaries shouldBe PaginatedList[GroupSummary](groupSummaries.take(5),
        PaginationMetaData(lastPage = false,
          firstPage = true,
          totalSize = 8,
          totalPages = 2,
          pageSize = 5,
          currentPageNumber = 1,
          currentPageSize = 5))
    }

    "Return second page of summaries as paginated list" in {
      //given
      val groupSummaries = (1 to 8)
        .map { i =>
          GroupSummary(s"$i", s"Carrots$i", Some(1), 1)
        }

      (mockAgentPermissionsConnector
        .getGroupSummaries(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future successful groupSummaries).once()

      //when
      val summaries = await(service.getPaginatedGroupSummaries(arn)(2))

      //then
      summaries shouldBe PaginatedList[GroupSummary](groupSummaries.takeRight(3),
        PaginationMetaData(lastPage = true,
          firstPage = false,
          totalSize = 8,
          totalPages = 2,
          pageSize = 5,
          currentPageNumber = 2,
          currentPageSize = 3))
    }

  }

  "get Paginated Clients For Custom Group" should {
    //given
    val grpId = "grp1"

    "return first page of clients as (page content, pagination data)" in {
      //given
      val paginatedListOfClients = PaginatedList[Client](fakeClients.take(5), PaginationMetaData(lastPage = true,
        firstPage = false,
        totalSize = 8,
        totalPages = 2,
        pageSize = 5,
        currentPageNumber = 1,
        currentPageSize = 5))

      expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)
      (mockAgentPermissionsConnector
        .getPaginatedClientsForCustomGroup(_: String)(_:Int, _:Int, _:Option[String], _:Option[String])(_: HeaderCarrier, _: ExecutionContext))
        .expects(grpId, *, *, *, *, *, *)
        .returning(Future successful paginatedListOfClients).once()

      val pageOfClientsInGroup = displayClients.take(5)

      //when
      val data = await(service.getPaginatedClientsForCustomGroup(groupId = grpId)(1, 5))

      val expectedData = (pageOfClientsInGroup, PaginationMetaData(lastPage = true,
        firstPage = false,
        totalSize = 8,
        totalPages = 2,
        pageSize = 5,
        currentPageNumber = 1,
        currentPageSize = 5))

      //then
      data shouldBe expectedData
    }

    "return second page of display clients as (page content, pagination data)" in {
      //given
      val paginatedListOfClients = PaginatedList[Client](fakeClients.takeRight(3), PaginationMetaData(lastPage = true,
        firstPage = false,
        totalSize = 8,
        totalPages = 2,
        pageSize = 5,
        currentPageNumber = 2,
        currentPageSize = 3))

      expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)
      (mockAgentPermissionsConnector
        .getPaginatedClientsForCustomGroup(_: String)(_:Int, _:Int, _:Option[String], _:Option[String])(_: HeaderCarrier, _: ExecutionContext))
        .expects(grpId, *, *, *, *, *, *)
        .returning(Future successful paginatedListOfClients).once()

      val pageOfClientsInGroup = displayClients.takeRight(3)


      //when
      val data = await(service.getPaginatedClientsForCustomGroup(groupId = grpId)(2, 5))
      val expectedData = (pageOfClientsInGroup, PaginationMetaData(lastPage = true,
        firstPage = false,
        totalSize = 8,
        totalPages = 2,
        pageSize = 5,
        currentPageNumber = 2,
        currentPageSize = 3))

      //then
      data shouldBe expectedData
    }
  }

  "update groups" should {
    "delegate to agentPermissionsConnector" in {

      //given
      val req = UpdateAccessGroupRequest(Some("grpName"))
      val grpId = "grp1"
      expectUpdateGroupSuccess(grpId, req)

      //when
      val done: Done = await(service.updateGroup(grpId, req))

      //then
      done shouldBe Done
    }
  }

  "get group" should {
    "delegate to agentPermissionsConnector" in {

      //given
      val grpId = "grp1"
      val agentUser = AgentUser("agent1", "Bob Smith")
      val expectedGroup = Some(CustomGroup(Arn("arn1"), "Bangers & Mash",
        LocalDateTime.MIN, LocalDateTime.MIN, agentUser, agentUser, None, None))
      expectGetGroupSuccess(grpId, expectedGroup)

      //when
      val output: Option[CustomGroup] = await(service.getGroup(grpId))

      //then
      output shouldBe expectedGroup
    }
  }

  "create group" should {
    "delegate to agentPermissionsConnector" in {

      //given
      val groupName = "Carrots"
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, Nil)
      expectGetSessionItem(SELECTED_CLIENTS, Nil)
      expectCreateGroupSuccess(arn, GroupRequest(groupName = groupName, Some(Nil), Some(Nil)))
      expectDeleteSessionItems(creatingGroupKeys)
      expectPutSessionItem(NAME_OF_GROUP_CREATED, groupName)

      //when
      val output = await(service.createGroup(arn, groupName))

      //then
      output shouldBe Done
    }
  }

  "delete group" should {
    "delegate to agentPermissionsConnector" in {

      //given
      val grpId = RandomStringUtils.randomAlphabetic(5)
      expectDeleteGroupSuccess(grpId)

      //when
      val output = await(service.deleteGroup(grpId))

      //then
      output shouldBe Done
    }
  }

  "addMembersToGroup" should {
    "delegate to agentPermissionsConnector" in {

      //given
      val grpId = RandomStringUtils.randomAlphabetic(5)
      val payload = AddMembersToAccessGroupRequest(None, None)

      (mockAgentPermissionsConnector
        .addMembersToGroup(_: String, _: AddMembersToAccessGroupRequest)(_: HeaderCarrier, _: ExecutionContext))
        .expects(grpId, payload, *, *)
        .returning(Future successful Done)

      //when
      val output = await(service.addMembersToGroup(grpId, payload))

      //then
      output shouldBe Done
    }
  }

  "groupSummariesForClient" should {
    "Return groups summaries from agentPermissionsConnector" in {

      //given
      val groupSummaries = Seq(
        GroupSummary("2", "Carrots", Some(1), 1),
        GroupSummary("3", "Potatoes", Some(1), 1),
      )
      val expectedClient = DisplayClient("hmrc", "Bob", "VAT", "ident")

      (mockAgentPermissionsConnector.getGroupsForClient(_: Arn, _: String)
      (_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, expectedClient.enrolmentKey, *, *)
        .returning(Future successful groupSummaries).once()

      //when
      val summaries = await(service.groupSummariesForClient(arn, expectedClient))

      //then
      summaries shouldBe groupSummaries


    }
  }

  "groupSummariesForTeamMember" should {
    "Return groups summaries from agentPermissionsConnector" in {

      //given
      val groupSummaries = Seq(
        GroupSummary("2", "Carrots", Some(1), 1),
        GroupSummary("3", "Potatoes", Some(1), 1),
      )
      val member = TeamMember("Bob the agent", "dont care", Some("123"))
      val agentUser = toAgentUser(member)

      (mockAgentPermissionsConnector
        .getGroupsForTeamMember(_: Arn, _: AgentUser)
        (_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, agentUser, *, *)
        .returning(Future successful Option(groupSummaries))
        .once()

      //when
      val summaries = await(service.groupSummariesForTeamMember(arn, member))

      //then
      summaries shouldBe groupSummaries


    }
  }

  "getTeamMembersFromGroup" should {
    "contact agentUserClientDetailsConnector and set items as selected based on input " in {

      //given
      val teamMembersInGroup = userDetails.take(2).reverse.map(TeamMember.fromUserDetails).toVector
      expectGetTeamMembers(arn)(userDetails)

      //when
      val output: Seq[TeamMember] = await(service.getTeamMembersFromGroup(arn)(teamMembersInGroup))

      //then
      output shouldBe teamMembersInGroup.map(_.copy(selected = true)).sortBy(_.name)
    }
  }

  "group name check" should {
    "delegate to agentPermissionsConnector" in {
      //given
      expectGroupNameCheck(ok = true)(arn, "Good name")

      //when
      val output: Boolean = await(service.groupNameCheck(arn, "Good name"))

      //then
      output shouldBe true
    }
  }

  "add team member to a group" should {
    "call patch/update on agentPermissionsConnector" in {

      //given
      val groupId = UUID.randomUUID().toString
      val agent = AgentUser("agentId", "Bob Builder")
      val payload = AddOneTeamMemberToGroupRequest(agent)

      (mockAgentPermissionsConnector
        .addOneTeamMemberToGroup(_: String, _: AddOneTeamMemberToGroupRequest)(_: HeaderCarrier, _: ExecutionContext))
        .expects(groupId, payload, *, *)
        .returning(Future successful Done)
        .once()

      //when
      val response = service.addOneMemberToGroup(groupId, payload).futureValue

      //then
      response shouldBe Done
    }

    }

  "remove client from custom group" should {
    "call DELETE on agentPermissionsConnector" in {

      //given
      val groupId = UUID.randomUUID().toString
      val clientId = UUID.randomUUID().toString

      (mockAgentPermissionsConnector
        .removeClientFromGroup(_: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(groupId, clientId, *, *)
        .returning(Future successful Done)
        .once()

      //when
      val response = service.removeClientFromGroup(groupId, clientId).futureValue

      //then
      response shouldBe Done
    }

  }

  "remove team member from custom group" should {

    "call DELETE on agentPermissionsConnector" in {

      //given
      val groupId = UUID.randomUUID().toString
      val memberId = UUID.randomUUID().toString

      (mockAgentPermissionsConnector
        .removeTeamMemberFromGroup(_: String, _: String, _: Boolean)(_: HeaderCarrier, _: ExecutionContext))
        .expects(groupId, memberId, true, *, *)
        .returning(Future successful Done)
        .once()

      //when
      val response = service.removeTeamMemberFromGroup(groupId, memberId, true).futureValue

      //then
      response shouldBe Done
    }

  }

}
