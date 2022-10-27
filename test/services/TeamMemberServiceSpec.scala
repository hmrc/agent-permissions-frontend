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
import controllers.{CLEAR_BUTTON, CONTINUE_BUTTON, FILTERED_TEAM_MEMBERS, FILTER_BUTTON, SELECTED_TEAM_MEMBERS, TEAM_MEMBER_SEARCH_INPUT, teamMemberFilteringKeys}
import helpers.BaseSpec
import models.{AddTeamMembersToGroup, TeamMember}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.UserDetails

class TeamMemberServiceSpec extends BaseSpec {

  implicit val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit lazy val sessionCacheService: SessionCacheService = mock[SessionCacheService]

  val service = new TeamMemberServiceImpl(mockAgentUserClientDetailsConnector, sessionCacheService)

  val users: Seq[UserDetails] = (1 to 5)
    .map(
      i =>
        UserDetails(userId = Option(s"user$i"),
          None,
          Some(s"Name $i"),
          Some(s"bob$i@accounting.com")))

  val members: Seq[TeamMember] = users.map(TeamMember.fromUserDetails)

  "getAllTeamMembers" should {

    "Get TeamMembers from agentUserClientDetailsConnector and merge with selected team members" in {

      expectGetTeamMembers(arn)(users.takeRight(2))
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, members.take(2))

      val teamMembers: Seq[TeamMember] = await(service.getAllTeamMembers(arn))

      teamMembers shouldBe
        List(TeamMember("Name 1", "bob1@accounting.com", Some("user1"), None),
          TeamMember("Name 2", "bob2@accounting.com", Some("user2"), None),
          TeamMember("Name 4", "bob4@accounting.com", Some("user4"), None),
          TeamMember("Name 5", "bob5@accounting.com", Some("user5"), None)
        )

    }

    "Get TeamMembers from agentUserClientDetailsConnector when there are NO selected team members" in {

      expectGetTeamMembers(arn)(users.takeRight(2))
      expectGetSessionItemNone(SELECTED_TEAM_MEMBERS)

      val teamMembers: Seq[TeamMember] = await(service.getAllTeamMembers(arn))

      teamMembers shouldBe
        List(
          TeamMember("Name 4", "bob4@accounting.com", Some("user4"), None),
          TeamMember("Name 5", "bob5@accounting.com", Some("user5"), None)
        )
    }
  }

  "getFilteredTeamMembersElseAll" should {
    "Get TeamMembers from agentUserClientDetailsConnector and merge selected ones when no FILTERED_TEAM_MEMBERS" in {

      //given NO FILTERED_TEAM_MEMBERS in session
      expectGetSessionItemNone(FILTERED_TEAM_MEMBERS)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, members)

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
      expectGetSessionItem(FILTERED_TEAM_MEMBERS, members)

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

  "saveSelectedOrFilteredTeamMembers" should {

    "work for CONTINUE_BUTTON" in {

      //expect
      expectGetTeamMembers(arn)(users)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, members.take(2))
      expectGetSessionItem(FILTERED_TEAM_MEMBERS, members.takeRight(1))
      val expectedPutSelected = List(
        TeamMember("Name 1", "bob1@accounting.com", Some("user1"), None, true),
        TeamMember("Name 2", "bob2@accounting.com", Some("user2"), None, true),
        TeamMember("Name 3", "bob3@accounting.com", Some("user3"), None, true),
        TeamMember("Name 4", "bob4@accounting.com", Some("user4"), None, true),
        TeamMember("Name 5", "bob5@accounting.com", Some("user5"), None, true),
        TeamMember("Name 1", "bob1@accounting.com", Some("user1"), None, false),
        TeamMember("Name 2", "bob2@accounting.com", Some("user2"), None, false)
      )
      expectDeleteSessionItems(teamMemberFilteringKeys)
      expectPutSessionItem(SELECTED_TEAM_MEMBERS, expectedPutSelected)

      val formData = AddTeamMembersToGroup(None, Some(members.map(_.id).toList), CONTINUE_BUTTON)

      //when
      await(service.saveSelectedOrFilteredTeamMembers(CONTINUE_BUTTON)(arn)(formData))

    }

    "work for CLEAR_BUTTON" in {

      //expect
      expectGetTeamMembers(arn)(users)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, members.take(2))
      expectGetSessionItem(FILTERED_TEAM_MEMBERS, members.takeRight(1))
      val expectedPutSelected = List(
        TeamMember("Name 1", "bob1@accounting.com", Some("user1"), None, true),
        TeamMember("Name 2", "bob2@accounting.com", Some("user2"), None, true),
        TeamMember("Name 3", "bob3@accounting.com", Some("user3"), None, true),
        TeamMember("Name 4", "bob4@accounting.com", Some("user4"), None, true),
        TeamMember("Name 5", "bob5@accounting.com", Some("user5"), None, true),
        TeamMember("Name 1", "bob1@accounting.com", Some("user1"), None, false),
        TeamMember("Name 2", "bob2@accounting.com", Some("user2"), None, false)
      )
      expectDeleteSessionItems(teamMemberFilteringKeys)
      expectPutSessionItem(SELECTED_TEAM_MEMBERS, expectedPutSelected)

      val formData = AddTeamMembersToGroup(None, Some(members.map(_.id).toList), CLEAR_BUTTON)

      //when
      await(service.saveSelectedOrFilteredTeamMembers(CLEAR_BUTTON)(arn)(formData))

    }

    "work for FILTER_BUTTON" in {

      //expect
      expectGetTeamMembers(arn)(users)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, members.take(2))
      expectGetSessionItem(FILTERED_TEAM_MEMBERS, members.takeRight(1))
      //TODO: this looks like a bug. We can't compare whole objects when
      //TODO: two with equal ids/names/emails might be selected or unselected
      val expectedPutSelected = List(
        TeamMember("Name 1", "bob1@accounting.com", Some("user1"), None, true),
        TeamMember("Name 2", "bob2@accounting.com", Some("user2"), None, true),
        TeamMember("Name 3", "bob3@accounting.com", Some("user3"), None, true),
        TeamMember("Name 4", "bob4@accounting.com", Some("user4"), None, true),
        TeamMember("Name 5", "bob5@accounting.com", Some("user5"), None, true),
        TeamMember("Name 1", "bob1@accounting.com", Some("user1"), None, false),
        TeamMember("Name 2", "bob2@accounting.com", Some("user2"), None, false)
      )
      expectPutSessionItem(SELECTED_TEAM_MEMBERS, expectedPutSelected)
      val searchTerm = "MY SEARCH"
      expectPutSessionItem(TEAM_MEMBER_SEARCH_INPUT, searchTerm)

      val formData = AddTeamMembersToGroup(Some(searchTerm), Some(members.map(_.id).toList), FILTER_BUTTON)

      //when
      await(service.saveSelectedOrFilteredTeamMembers(FILTER_BUTTON)(arn)(formData))

    }

    "work for FILTER_BUTTON with no SEARCH form data" in {

      //expect
      expectGetTeamMembers(arn)(users)
      expectGetSessionItemNone(SELECTED_TEAM_MEMBERS)
      expectGetSessionItemNone(FILTERED_TEAM_MEMBERS)
      val selectedTeamMemberIds = members.take(2).map(_.id).toList
      val formData = AddTeamMembersToGroup(
        None, // <-- i.e. no search term
        Some(selectedTeamMemberIds), FILTER_BUTTON
      )
      val expectedSelectedTeamMembers =
        List(
          TeamMember("Name 1", "bob1@accounting.com", Some("user1"),None,true),
          TeamMember("Name 2", "bob2@accounting.com", Some("user2"),None,true)
        )
      expectPutSessionItem(SELECTED_TEAM_MEMBERS, expectedSelectedTeamMembers)

      //when
      await(service.saveSelectedOrFilteredTeamMembers(FILTER_BUTTON)(arn)(formData))

    }
  }


}
