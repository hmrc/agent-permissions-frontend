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

package controllers

import com.google.inject.AbstractModule
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.GroupType.{CUSTOM, TAX_SERVICE}
import controllers.actions.AuthAction
import helpers.Css.{H1, checkYourAnswersListRows}
import helpers.{BaseSpec, Css}
import models.{GroupId, TeamMember}
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.test.FakeRequest
import play.api.test.Helpers.{POST, contentAsString, defaultAwaitTimeout, redirectLocation}
import services.{GroupService, SessionCacheService, TeamMemberService}
import uk.gov.hmrc.agents.accessgroups.optin.OptedInReady
import uk.gov.hmrc.agents.accessgroups.{AgentUser, GroupSummary, UserDetails}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

class ManageTeamMemberControllerSpec extends BaseSpec {

  implicit lazy val authConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val agentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val agentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit lazy val groupService: GroupService = mock[GroupService]
  implicit lazy val teamMemberService: TeamMemberService = mock[TeamMemberService]
  implicit lazy val sessionCacheService: SessionCacheService = mock[SessionCacheService]

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(authConnector, env, conf, agentPermissionsConnector))
      bind(classOf[AgentPermissionsConnector]).toInstance(agentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(agentUserClientDetailsConnector)
      bind(classOf[GroupService]).toInstance(groupService)
      bind(classOf[SessionCacheService]).toInstance(sessionCacheService)
      bind(classOf[TeamMemberService]).toInstance(teamMemberService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val controller: ManageTeamMemberController = fakeApplication.injector.instanceOf[ManageTeamMemberController]

  val agentUsers: Set[AgentUser] = (1 to 5).map(i => AgentUser(id = s"John $i", name = s"John $i name")).toSet

  val userDetails: Seq[UserDetails] = (1 to 5)
    .map { i =>
      UserDetails(
        Some(s"John $i"),
        Some("User"),
        Some(s"John $i name"),
        Some(s"john$i@abc.com")
      )
    }

  val teamMembers: Seq[TeamMember] = userDetails.map(TeamMember.fromUserDetails)

  val memberId: String = teamMembers.head.id

  val groupSummaries = Seq(
    GroupSummary(GroupId.random(), "groupName", Some(33), 9),
    GroupSummary(GroupId.random(), "groupName1", Some(3), 1),
    GroupSummary(GroupId.random(), "groupName2", Some(3), 1, taxService = Some("VAT")),
  )
  private val ctrlRoute: ReverseManageTeamMemberController = routes.ManageTeamMemberController

  s"GET ${ctrlRoute.showPageOfTeamMembers(None).url}" should {

    "render the manage team members list" in {
      //given
      val searchTerm = "ab"
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetPageOfTeamMembers(arn)(teamMembers)
      expectGetSessionItem(TEAM_MEMBER_SEARCH_INPUT, searchTerm)

      //when
      val result = controller.showPageOfTeamMembers(Some(1))(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Filter results for ‘$searchTerm’ Manage team members’ access groups - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage team members’ access groups"

      html.select(Css.inputTextWithId("search")).attr("value") shouldBe searchTerm

      html.select("#filter-button").text() shouldBe "Apply filter" // ‘filter’ (singular) not ‘filters’ - APB-7104
      html.select("#clear-button").text() shouldBe "Clear filter"

      val th = html.select(Css.tableWithId("members")).select("thead th")
      th.size() shouldBe 3
      th.get(0).text() shouldBe "Name"
      th.get(1).text() shouldBe "Email"
      th.get(2).text() shouldBe "Actions"

      val trs = html.select(Css.tableWithId("members")).select("tbody tr")
      trs.size() shouldBe 5

      val paginationItems = html.select(Css.pagination_li)
      paginationItems.size() shouldBe 4
      paginationItems.select("a").get(0).text() shouldBe "2"
      paginationItems.select("a").get(0).attr("href") startsWith "/agent-permissions/manage-team-members?page=2"
    }

  }

  s"POST ${ctrlRoute.submitPageOfTeamMembers().url}" should {

    "redirect and clear filter when CLEAR FILTER is clicked" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectDeleteSessionItem(TEAM_MEMBER_SEARCH_INPUT)

      //and we have CLEAR filter in query params
      implicit val requestWithQueryParams = FakeRequest(POST, ctrlRoute.submitPageOfTeamMembers().url)
        .withFormUrlEncodedBody("submit" -> CLEAR_BUTTON)
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.submitPageOfTeamMembers()(requestWithQueryParams)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get.shouldBe(ctrlRoute.showPageOfTeamMembers(None).url)
    }

    "go to correct page when FILTER_BUTTON is clicked" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      val dude = "dude"
      expectPutSessionItem(TEAM_MEMBER_SEARCH_INPUT, dude)

      //and we have CLEAR filter in query params
      implicit val requestWithQueryParams = FakeRequest(POST, ctrlRoute.submitPageOfTeamMembers().url)
        .withFormUrlEncodedBody(
          "submit" -> FILTER_BUTTON,
          "search" -> dude
        )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.submitPageOfTeamMembers()(requestWithQueryParams)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get.shouldBe(ctrlRoute.showPageOfTeamMembers(None).url)
    }
  }

  s"GET ${ctrlRoute.showTeamMemberDetails(memberId).url}" should {

    "render the team member details page with NO GROUPS" in {
      //given
      val teamMember = teamMembers.head
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectLookupTeamMember(arn)(teamMember)
      expectGetGroupSummariesForTeamMember(arn)(teamMember)(Seq.empty)

      //when
      val result = controller.showTeamMemberDetails(teamMember.id)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Team member details - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Team member details"

      html.body.text().contains("Not in any access groups") shouldBe true

      html.select(checkYourAnswersListRows).get(0).text() shouldBe "Name John 1 name"
      html.select(checkYourAnswersListRows).get(1).text() shouldBe "Email john1@abc.com"
      html.select(checkYourAnswersListRows).get(2).text() shouldBe "Role Administrator - Can manage access groups and client details."
    }

    "render the team member details page with a list of groups" in {
      //given
      val teamMember = teamMembers.head
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectLookupTeamMember(arn)(teamMember)
      expectGetGroupSummariesForTeamMember(arn)(teamMember)(groupSummaries)

      //when
      val result = controller.showTeamMemberDetails(memberId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Team member details - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Team member details"

      html.body.text().contains("Not assigned to a group") shouldBe false

      val linksToGroups = html.select("main div#member-of-groups ul li a")
      linksToGroups.size() shouldBe 3
      linksToGroups.get(0).text() shouldBe "groupName"
      linksToGroups.get(0).attr("href") shouldBe
        controllers.routes.ManageGroupTeamMembersController.showExistingGroupTeamMembers(groupSummaries.head.groupId, CUSTOM, None).url

      linksToGroups.get(2).text() shouldBe "groupName2"
      linksToGroups.get(2).attr("href") shouldBe
        controllers.routes.ManageGroupTeamMembersController.showExistingGroupTeamMembers(groupSummaries(2).groupId, TAX_SERVICE, None).url

    }
  }

}
