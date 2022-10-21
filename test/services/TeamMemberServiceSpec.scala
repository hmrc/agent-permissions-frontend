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

import connectors.AgentUserClientDetailsConnector
import controllers.FILTERED_TEAM_MEMBERS
import helpers.BaseSpec
import models.TeamMember
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.UserDetails

class TeamMemberServiceSpec extends BaseSpec {

  implicit val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector =
    mock[AgentUserClientDetailsConnector]
  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

  val service = new TeamMemberServiceImpl(mockAgentUserClientDetailsConnector,sessionCacheRepo)

  val users: Seq[UserDetails] = (1 to 5)
    .map(
      i =>
        UserDetails(userId = Option(s"user$i"),
          None,
          Some(s"Name $i"),
          Some(s"bob$i@accounting.com")))

  val members: Seq[TeamMember] = users.map(TeamMember.fromUserDetails)

  "getTeamMembers" should {
    "Get TeamMembers from agentUserClientDetailsConnector and merge selected ones when no FILTERED_TEAM_MEMBERS" in {

      //given NOFILTERED_TEAM_MEMBERS in session
      expectGetTeamMembers(arn)(users)

      //when
      val teamMembers: Seq[TeamMember] = await(service.getFilteredTeamMembersElseAll(arn))

      //then
      teamMembers.size shouldBe 5
      teamMembers.head.name shouldBe "Name 1"
      teamMembers.head.userId shouldBe Some("user1")
      teamMembers.head.email shouldBe "bob1@accounting.com"
      teamMembers.head.selected shouldBe false

    }
    "Get TeamMembers from session when FILTERED_TEAM_MEMBERS exists" in {

      //given
      await(sessionCacheRepo.putSession(FILTERED_TEAM_MEMBERS, members))

      //when
      val teamMembers: Seq[TeamMember] = await(service.getFilteredTeamMembersElseAll(arn))

      //then
      teamMembers.size shouldBe 5
      teamMembers.head.name shouldBe "Name 1"
      teamMembers.head.userId shouldBe Some("user1")
      teamMembers.head.email shouldBe "bob1@accounting.com"
      teamMembers.head.selected shouldBe false

    }
  }

  "Lookup team members" should {

    "Return empty list when no ids passed in" in {

      //given
      await(sessionCacheRepo.putSession(FILTERED_TEAM_MEMBERS, members))

      //when
      val teamMembers: Seq[TeamMember] = await(service.lookupTeamMembers(arn)(None))

      //then
      teamMembers shouldBe Seq.empty[TeamMember]

    }

    "Return list of team members with passed ids" in {

      //given
      expectGetTeamMembers(arn)(users)
      //when
      val teamMembers: Seq[TeamMember] = await(service.lookupTeamMembers(arn)(Some(members.take(2).map(_.id).toList)))

      //then
      teamMembers shouldBe teamMembers.take(2)

    }
  }



}
