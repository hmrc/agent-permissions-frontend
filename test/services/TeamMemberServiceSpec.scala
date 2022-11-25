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
import controllers.{CLEAR_BUTTON, CONTINUE_BUTTON, CURRENT_PAGE_TEAM_MEMBERS, FILTERED_TEAM_MEMBERS, FILTER_BUTTON, SELECTED_TEAM_MEMBERS, TEAM_MEMBER_SEARCH_INPUT, teamMemberFilteringKeys}
import helpers.BaseSpec
import models.{AddTeamMembersToGroup, TeamMember}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.{PaginatedList, PaginationMetaData, UserDetails}

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
        TeamMember("Name 1", "bob1@accounting.com", Some("user1"), None, selected = true),
        TeamMember("Name 2", "bob2@accounting.com", Some("user2"), None, selected = true),
        TeamMember("Name 3", "bob3@accounting.com", Some("user3"), None, selected = true),
        TeamMember("Name 4", "bob4@accounting.com", Some("user4"), None, selected = true),
        TeamMember("Name 5", "bob5@accounting.com", Some("user5"), None, selected = true),
        TeamMember("Name 1", "bob1@accounting.com", Some("user1"), None),
        TeamMember("Name 2", "bob2@accounting.com", Some("user2"), None)
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
        TeamMember("Name 1", "bob1@accounting.com", Some("user1"), None, selected = true),
        TeamMember("Name 2", "bob2@accounting.com", Some("user2"), None, selected = true),
        TeamMember("Name 3", "bob3@accounting.com", Some("user3"), None, selected = true),
        TeamMember("Name 4", "bob4@accounting.com", Some("user4"), None, selected = true),
        TeamMember("Name 5", "bob5@accounting.com", Some("user5"), None, selected = true),
        TeamMember("Name 1", "bob1@accounting.com", Some("user1"), None),
        TeamMember("Name 2", "bob2@accounting.com", Some("user2"), None)
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
      expectGetTeamMembers(arn)(users)
      // this is being called twice. not very efficient
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, members.take(2), 2)
      expectGetSessionItem(FILTERED_TEAM_MEMBERS, members.takeRight(1))

      val expectedPutSelected = List(
        TeamMember("Name 1", "bob1@accounting.com", Some("user1"), None, selected = true),
        TeamMember("Name 2", "bob2@accounting.com", Some("user2"), None, selected = true),
        TeamMember("Name 3", "bob3@accounting.com", Some("user3"), None, selected = true),
        TeamMember("Name 4", "bob4@accounting.com", Some("user4"), None, selected = true),
        TeamMember("Name 5", "bob5@accounting.com", Some("user5"), None, selected = true),
        TeamMember("Name 1", "bob1@accounting.com", Some("user1"), None),
        TeamMember("Name 2", "bob2@accounting.com", Some("user2"), None)
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
          TeamMember("Name 1", "bob1@accounting.com", Some("user1"), None, selected = true),
          TeamMember("Name 2", "bob2@accounting.com", Some("user2"), None, selected = true)
        )
      expectDeleteSessionItem(TEAM_MEMBER_SEARCH_INPUT)
      expectPutSessionItem(SELECTED_TEAM_MEMBERS, expectedSelectedTeamMembers)

      //when
      await(service.saveSelectedOrFilteredTeamMembers(FILTER_BUTTON)(arn)(formData))

    }
  }

  "getPageOfTeamMembers" should {

    "return correct page of team members unfiltered with no search input" in {

      val users: Seq[UserDetails] = (1 to 17).map(
        i =>
          UserDetails(userId = Option(s"user$i"),
            None,
            Some(s"Name $i"),
            Some(s"bob$i@accounting.com")))
      val members: Seq[TeamMember] = users.map(TeamMember.fromUserDetails)

      val pageSize = 5
      expectGetTeamMembers(arn)(users)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, members.take(3))
      expectGetSessionItemNone(TEAM_MEMBER_SEARCH_INPUT)
      val expectedPage: Seq[TeamMember] = members.take(3).map(_.copy(selected = true)) ++ members.slice(3, pageSize)
      expectPutSessionItem(CURRENT_PAGE_TEAM_MEMBERS, expectedPage)
      val page: PaginatedList[TeamMember] = await(service.getPageOfTeamMembers(arn)(1, pageSize))

      page.paginationMetaData shouldBe PaginationMetaData(false, true, users.length, 4, pageSize, 1, pageSize)
      page.pageContent shouldBe
        List(TeamMember("Name 1", "bob1@accounting.com", Some("user1"), None, true),
          TeamMember("Name 2", "bob2@accounting.com", Some("user2"), None, true),
          TeamMember("Name 3", "bob3@accounting.com", Some("user3"), None, true),
          TeamMember("Name 4", "bob4@accounting.com", Some("user4"), None, false),
          TeamMember("Name 5", "bob5@accounting.com", Some("user5"), None, false),
        )

    }

    "return correct page of team members filtered with search input" in {

      val users: Seq[UserDetails] = (1 to 17).map(
        i =>
          UserDetails(userId = Option(s"user$i"),
            None,
            Some(s"Name $i"),
            Some(s"bob$i@accounting.com")))
      val members: Seq[TeamMember] = users.map(TeamMember.fromUserDetails)

      val pageSize = 5
      expectGetTeamMembers(arn)(users)
      expectGetSessionItemNone(SELECTED_TEAM_MEMBERS)
      expectGetSessionItem(TEAM_MEMBER_SEARCH_INPUT, "NAME 1")
      val expectedPage: Seq[TeamMember] = members.filter(m => m.name.toLowerCase.contains("name 1")).take(pageSize)
      expectPutSessionItem(CURRENT_PAGE_TEAM_MEMBERS, expectedPage)
      val page: PaginatedList[TeamMember] = await(service.getPageOfTeamMembers(arn)(1, pageSize))

      page.paginationMetaData shouldBe PaginationMetaData(false, true, 9, 2, 5, 1, 5)
      page.pageContent shouldBe
        Seq(
          TeamMember("Name 1", "bob1@accounting.com",   Some("user1"), None ),
          TeamMember("Name 10", "bob10@accounting.com", Some("user10"), None),
          TeamMember("Name 11", "bob11@accounting.com", Some("user11"), None),
          TeamMember("Name 12", "bob12@accounting.com", Some("user12"), None),
          TeamMember("Name 13", "bob13@accounting.com", Some("user13"), None)
        )

    }
  }

  "savePageOfTeamMembers" should {

    """add to existing saved members and sort by name setting all to SELECTED = TRUE
       and delete TEAM_MEMBER_SEARCH_INPUT when search is empty""" in {

      //given
      val users: Seq[UserDetails] = (1 to 25).map(
        i =>
          UserDetails(userId = Option(s"user$i"),
            None,
            Some(s"Name $i"),
            Some(s"bob$i@accounting.com")))
      val members: Seq[TeamMember] = users.map(TeamMember.fromUserDetails)

      expectDeleteSessionItem(TEAM_MEMBER_SEARCH_INPUT)
      val existingSavedMembers = members.take(3)
      val membersToSave = members.slice(10, 15)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, existingSavedMembers)
      expectGetSessionItem(CURRENT_PAGE_TEAM_MEMBERS, members.slice(10, 20))

      val expectedNewSelectedMembers = (membersToSave ++ existingSavedMembers).map(_.copy(selected = true)).sortBy(_.name)
      expectPutSessionItem(SELECTED_TEAM_MEMBERS, expectedNewSelectedMembers)

      val formData = AddTeamMembersToGroup(members = Some(membersToSave.map(_.id).toList))
      //when
      val saved = await(service.savePageOfTeamMembers(formData))

      //then
      saved shouldBe expectedNewSelectedMembers

    }

    "delete all search keys when CONTINUE_BUTTON pushed" in {

      //given
      val users: Seq[UserDetails] = (1 to 25).map(
        i =>
          UserDetails(userId = Option(s"user$i"),
            None,
            Some(s"Name $i"),
            Some(s"bob$i@accounting.com")))
      val members: Seq[TeamMember] = users.map(TeamMember.fromUserDetails)

      expectDeleteSessionItem(TEAM_MEMBER_SEARCH_INPUT)
      val membersToSave = members.slice(10, 15)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty)
      expectGetSessionItem(CURRENT_PAGE_TEAM_MEMBERS, members.slice(10, 20))

      val expectedNewSelectedMembers = (membersToSave).map(_.copy(selected = true)).sortBy(_.name)
      expectPutSessionItem(SELECTED_TEAM_MEMBERS, expectedNewSelectedMembers)

      //and
      val formData = AddTeamMembersToGroup(submit = CONTINUE_BUTTON, members = Some(membersToSave.map(_.id).toList))
      //EXPECT
      expectDeleteSessionItems(teamMemberFilteringKeys)

      //when
      val saved = await(service.savePageOfTeamMembers(formData))

      //then
      saved shouldBe expectedNewSelectedMembers
    }


    "delete TEAM_MEMBER_SEARCH_INPUT when CLEAR_BUTTON pushed" in {

      //given
      val users: Seq[UserDetails] = (1 to 25).map(
        i =>
          UserDetails(userId = Option(s"user$i"),
            None,
            Some(s"Name $i"),
            Some(s"bob$i@accounting.com")))
      val members: Seq[TeamMember] = users.map(TeamMember.fromUserDetails)

      expectDeleteSessionItem(TEAM_MEMBER_SEARCH_INPUT)
      val membersToSave = members.slice(10, 15)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty)
      expectGetSessionItem(CURRENT_PAGE_TEAM_MEMBERS, members.slice(10, 20))

      val expectedNewSelectedMembers = (membersToSave).map(_.copy(selected = true)).sortBy(_.name)
      expectPutSessionItem(SELECTED_TEAM_MEMBERS, expectedNewSelectedMembers)

      //and
      val formData = AddTeamMembersToGroup(submit = CLEAR_BUTTON, members = Some(membersToSave.map(_.id).toList))
      //EXPECT
      expectDeleteSessionItem(TEAM_MEMBER_SEARCH_INPUT)

      //when
      val saved = await(service.savePageOfTeamMembers(formData))

      //then
      saved shouldBe expectedNewSelectedMembers
    }



  }


}
