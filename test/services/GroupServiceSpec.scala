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

package services

import akka.Done
import connectors.{AddMembersToAccessGroupRequest, AgentPermissionsConnector, AgentUserClientDetailsConnector, GroupSummary, UpdateAccessGroupRequest}
import helpers.BaseSpec
import models.{DisplayClient, TeamMember}
import org.apache.commons.lang3.RandomStringUtils
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Arn, UserDetails}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class GroupServiceSpec extends BaseSpec {

  implicit val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector =
    mock[AgentUserClientDetailsConnector]
  lazy val sessionCacheRepo: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)
  implicit val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]

  val service = new GroupServiceImpl(
      mockAgentUserClientDetailsConnector,
      sessionCacheRepo,
      mockAgentPermissionsConnector
    )

  val userDetails: Seq[UserDetails] = (1 to 5)
    .map { i =>
      UserDetails(Some(s"John $i"), Some("User"), Some(s"John $i name"), Some(s"john$i@abc.com"))
    }

  "get groups" should {
    "Return groups from agentPermissionsConnector" in {

      //given
      val groupSummaries = Seq(
        GroupSummary("2", "Carrots", 1, 1),
        GroupSummary("3", "Potatoes", 1, 1),
      )

      (mockAgentPermissionsConnector
        .groups(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future successful groupSummaries).once()

      //when
      val summaries = await(service.groups(arn))

      //then
      summaries shouldBe groupSummaries


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
      val expectedGroup = Some(AccessGroup(Arn("arn1"), "Bangers & Mash",
        LocalDateTime.MIN, LocalDateTime.MIN, agentUser, agentUser,None, None))
      expectGetGroupSuccess(grpId, expectedGroup)

      //when
      val output: Option[AccessGroup] = await(service.getGroup(grpId))

      //then
      output shouldBe expectedGroup
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
        GroupSummary("2", "Carrots", 1, 1),
        GroupSummary("3", "Potatoes", 1, 1),
      )
      val expectedClient = DisplayClient("hmrc","Bob","tax", "ident")

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

}
