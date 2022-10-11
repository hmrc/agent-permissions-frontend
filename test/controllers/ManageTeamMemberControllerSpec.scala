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

package controllers

import com.google.inject.AbstractModule
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector, GroupSummary}
import helpers.Css.{H1, H2}
import helpers.{BaseSpec, Css}
import models.TeamMember
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services.{GroupService, GroupServiceImpl}
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, OptedInReady, UserDetails}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

class ManageTeamMemberControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector
    : AgentUserClientDetailsConnector =
    mock[AgentUserClientDetailsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]

  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction])
        .toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector))
      bind(classOf[AgentPermissionsConnector])
        .toInstance(mockAgentPermissionsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepo)
      bind(classOf[AgentUserClientDetailsConnector])
        .toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[GroupService]).toInstance(
        new GroupServiceImpl(mockAgentUserClientDetailsConnector, sessionCacheRepo, mockAgentPermissionsConnector))
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val controller: ManageTeamMemberController =
    fakeApplication.injector.instanceOf[ManageTeamMemberController]

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
    GroupSummary("groupId", "groupName", 33, 9),
    GroupSummary("groupId-1", "groupName-1", 3, 0)
  )

  s"GET ${routes.ManageTeamMemberController.showAllTeamMembers.url}" should {

    "render the manage team members list" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      stubOptInStatusOk(arn)(OptedInReady)
      stubGetTeamMembersOk(arn)(userDetails)

      //when
      val result = controller.showAllTeamMembers()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Manage team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage team members"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 4
      th.get(0).text() shouldBe "Name"
      th.get(1).text() shouldBe "Email"
      th.get(2).text() shouldBe "Role"
      th.get(3).text() shouldBe "Actions"

      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 5
    }

    "render the manage team members list with query params" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubOptInStatusOk(arn)(OptedInReady)
      stubGetTeamMembersOk(arn)(userDetails)

      implicit val requestWithQueryParams = FakeRequest(GET,
        routes.ManageTeamMemberController.showAllTeamMembers.url +
          "?submit=filter&search=john1&filter="
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showAllTeamMembers()(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Filter results for 'john1' Manage team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage team members"
      html.select(H2).text shouldBe "Filter results for 'john1'"

      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 1
    }

    "redirect to baseUrl when CLEAR FILTER is clicked" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubOptInStatusOk(arn)(OptedInReady)
      stubGetTeamMembersOk(arn)(userDetails)
      //and we have CLEAR filter in query params
      implicit val requestWithQueryParams = FakeRequest(GET,
        routes.ManageTeamMemberController.showAllTeamMembers.url +
          "?submit=clear"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showAllTeamMembers()(requestWithQueryParams)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get
        .shouldBe(routes.ManageTeamMemberController.showAllTeamMembers.url)
    }

  }

  s"GET ${routes.ManageTeamMemberController.showTeamMemberDetails(memberId).url}" should {

    "render the team member details page with NO GROUPS" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubOptInStatusOk(arn)(OptedInReady)
      expectGetGroupsForTeamMemberSuccess(arn, agentUsers.last, None)
      stubGetTeamMembersOk(arn)(userDetails)

      //when
      val result = controller.showTeamMemberDetails(memberId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Team member details - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Team member details"

      html.body.text().contains("Not assigned to an access group") shouldBe true
    }

    "render the clients details page with list of groups" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubOptInStatusOk(arn)(OptedInReady)
      expectGetGroupsForTeamMemberSuccess(arn, agentUsers.last, Some(groupSummaries))
      stubGetTeamMembersOk(arn)(userDetails)

      //when
      val result = controller.showTeamMemberDetails(memberId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Team member details - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Team member details"

      html.body.text().contains("Not assigned to an access group") shouldBe false

    }
  }


}
