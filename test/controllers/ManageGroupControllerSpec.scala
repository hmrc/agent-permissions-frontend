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
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector, GroupSummary, UpdateAccessGroupRequest}
import helpers.Css._
import helpers.{BaseSpec, Css}
import models.{DisplayClient, TeamMember}
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import org.mongodb.scala.bson.ObjectId
import play.api.Application
import play.api.http.Status.{NOT_FOUND, OK, SEE_OTHER}
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services.{GroupService, GroupServiceImpl}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

import java.time.LocalDate

class ManageGroupControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector
    : AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val groupService: GroupService = new GroupServiceImpl(mockAgentUserClientDetailsConnector, sessionCacheRepo, mockAgentPermissionsConnector)

  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)
  val groupId = "xyz"
  private val agentUser: AgentUser =
    AgentUser(RandomStringUtils.random(5), "Rob the Agent")
  val accessGroup: AccessGroup = AccessGroup(new ObjectId(),
                                arn,
                                "Bananas",
                                LocalDate.of(2020, 3, 10).atStartOfDay(),
                                null,
                                agentUser,
                                agentUser,
                                None,
                                None)

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction])
        .toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector))
      bind(classOf[AgentPermissionsConnector])
        .toInstance(mockAgentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepo)
      bind(classOf[GroupService]).toInstance(groupService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val fakeClients: Seq[Client] =
    List.tabulate(3)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))

  val displayClients: Seq[DisplayClient] =
    fakeClients.map(DisplayClient.fromClient(_))

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

  val controller: ManageGroupController = fakeApplication.injector.instanceOf[ManageGroupController]

  s"GET ${routes.ManageGroupController.showManageGroups}" should {

    "render correctly the manage groups page" in {

      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      val groupSummaries = (1 to 3).map(i =>
        GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))
      val unassignedClients = (1 to 8).map(i =>
        DisplayClient(s"hmrcRef$i", s"name$i", s"HMRC-MTD-IT", ""))
      val summaries = Some((groupSummaries, unassignedClients))
      expectGetGroupSummarySuccess(arn, summaries)
      expectGetGroupSummarySuccess(arn, summaries)


      //when
      val result = controller.showManageGroups()(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Manage access groups - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage access groups"
      html
        .select("p#info")
        .get(0)
        .text() shouldBe "The team members in the group will be able to manage the tax affairs of clients in the group"

      //verify the tabs/tab headings first
      val tabs = html.select("li.govuk-tabs__list-item")
      tabs.size() shouldBe 2
      val accessGroupsTab = tabs.get(0)
      accessGroupsTab.hasClass("govuk-tabs__list-item--selected") shouldBe true
      accessGroupsTab.select("a").text() shouldBe "Access groups"
      accessGroupsTab.select("a").attr("href") shouldBe "#groups-panel"

      val unassignedClientsTab = tabs.get(1)
      unassignedClientsTab.hasClass("govuk-tabs__list-item--selected") shouldBe false
      unassignedClientsTab.select("a").text() shouldBe "Unassigned clients"
      unassignedClientsTab.select("a").attr("href") shouldBe "#unassigned-clients"

      //verify the tab panel contents
      val groupsPanel = html.select(tabPanelWithIdOf("groups-panel"))
      groupsPanel.select("h2").text() shouldBe "Access groups"

      val groups = groupsPanel.select("dl.govuk-summary-list")
      groups.size() shouldBe 3
      val firstGroup = groups.get(0)
      val clientsRow = firstGroup.select(".govuk-summary-list__row").get(0)
      clientsRow.select("dt").text() shouldBe "Clients"
      clientsRow.select(".govuk-summary-list__value")
        .text() shouldBe "3"
      clientsRow.select(".govuk-summary-list__actions")
        .text() shouldBe "Manage clients"
      clientsRow.select(".govuk-summary-list__actions a")
        .attr("href") shouldBe "/agent-permissions/manage-group-clients/groupId1"

      val membersRow = firstGroup.select(".govuk-summary-list__row").get(1)

      membersRow.select("dt").text() shouldBe "Team members"
      membersRow.select(".govuk-summary-list__value")
        .text() shouldBe "4"
      membersRow.select(".govuk-summary-list__actions")
        .text() shouldBe "Manage team members"
      membersRow.select(".govuk-summary-list__actions a")
        .attr("href") shouldBe "/agent-permissions/manage-group-team-members/groupId1"

      val unassignedClientsPanel =
        html.select(tabPanelWithIdOf("unassigned-clients"))
      unassignedClientsPanel.select("h2").text() shouldBe "Unassigned clients"
      val clientsTh = unassignedClientsPanel.select("table th")
      clientsTh.size() shouldBe 4
      clientsTh.get(1).text() shouldBe "Client reference"
      clientsTh.get(2).text() shouldBe "Tax reference"
      clientsTh.get(3).text() shouldBe "Tax service"

      val clientsTrs = unassignedClientsPanel.select("table tbody tr")
      clientsTrs.size() shouldBe 8

      val backlink = html.select(backLink)
      backlink.size() shouldBe 1
      backlink.attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
      backlink.text() shouldBe "Back"

    }

    "render correctly the manage groups page when nothing returned" in {

      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val summaries = None
      expectGetGroupSummarySuccess(arn, summaries)
      expectGetGroupSummarySuccess(arn, summaries)

      //when
      val result = controller.showManageGroups()(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Manage access groups - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage access groups"
      html
        .select("p#info")
        .get(0)
        .text() shouldBe "The team members in the group will be able to manage the tax affairs of clients in the group"

      //verify the tabs/tab headings first
      val tabs = html.select("li.govuk-tabs__list-item")
      tabs.size() shouldBe 2
      val accessGroupsTab = tabs.get(0)
      accessGroupsTab.hasClass("govuk-tabs__list-item--selected") shouldBe true
      accessGroupsTab.select("a").text() shouldBe "Access groups"
      accessGroupsTab.select("a").attr("href") shouldBe "#groups-panel"

      val unassignedClientsTab = tabs.get(1)
      unassignedClientsTab.hasClass("govuk-tabs__list-item--selected") shouldBe false
      unassignedClientsTab.select("a").text() shouldBe "Unassigned clients"
      unassignedClientsTab.select("a").attr("href") shouldBe "#unassigned-clients"

      //verify the tab panel contents
      val groupsPanel = html.select(tabPanelWithIdOf("groups-panel"))
      groupsPanel.select("h2").text() shouldBe "Access groups"
      groupsPanel.select("h3").text() shouldBe "No groups found"
      groupsPanel.select("a").text() shouldBe "Create new access group"
      groupsPanel
        .select("a")
        .attr("href") shouldBe routes.GroupController.showGroupName.url
      groupsPanel.select("a").hasClass("govuk-button") shouldBe true

      val unassignedClientsPanel =
        html.select(tabPanelWithIdOf("unassigned-clients"))
      unassignedClientsPanel.select("h2").text() shouldBe "Unassigned clients"
      unassignedClientsPanel
        .select("h3")
        .text() shouldBe "No unassigned clients found"
      val table = unassignedClientsPanel.select("table")
      table.size() shouldBe 0

      val backlink = html.select(backLink)
      backlink.size() shouldBe 1
      backlink.attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
      backlink.text() shouldBe "Back"
    }

    "render content when filtered clients in session" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(FILTERED_CLIENTS, displayClients))
      val groupSummaries = (1 to 3).map(i =>
        GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))
      val unassignedClients = (1 to 8).map(i =>
        DisplayClient(s"hmrcRef$i", s"name$i", s"HMRC-MTD-IT", ""))
      val summaries = Some((groupSummaries, unassignedClients))
      expectGetGroupSummarySuccess(arn, summaries)

      //when
      val result = controller.showManageGroups()(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Manage access groups - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage access groups"
      html
        .select("p#info")
        .get(0)
        .text() shouldBe "The team members in the group will be able to manage the tax affairs of clients in the group"

      //verify the tabs/tab headings first
      val tabs = html.select("li.govuk-tabs__list-item")
      tabs.size() shouldBe 2
      val accessGroupsTab = tabs.get(0)
      accessGroupsTab.hasClass("govuk-tabs__list-item--selected") shouldBe true
      accessGroupsTab.select("a").text() shouldBe "Access groups"
      accessGroupsTab.select("a").attr("href") shouldBe "#groups-panel"

      val unassignedClientsTab = tabs.get(1)
      unassignedClientsTab.hasClass("govuk-tabs__list-item--selected") shouldBe false
      unassignedClientsTab.select("a").text() shouldBe "Unassigned clients"
      unassignedClientsTab.select("a").attr("href") shouldBe "#unassigned-clients"

      //verify the tab panel contents
      val groupsPanel = html.select(tabPanelWithIdOf("groups-panel"))
      groupsPanel.select("h2").text() shouldBe "Access groups"

      val groups = groupsPanel.select("dl.govuk-summary-list")
      groups.size() shouldBe 3
      val firstGroup = groups.get(0)
      val clientsRow = firstGroup.select(".govuk-summary-list__row").get(0)
      clientsRow.select("dt").text() shouldBe "Clients"
      clientsRow.select(".govuk-summary-list__value")
        .text() shouldBe "3"
      clientsRow.select(".govuk-summary-list__actions")
        .text() shouldBe "Manage clients"
      clientsRow.select(".govuk-summary-list__actions a")
        .attr("href") shouldBe "/agent-permissions/manage-group-clients/groupId1"

      val membersRow = firstGroup.select(".govuk-summary-list__row").get(1)

      membersRow.select("dt").text() shouldBe "Team members"
      membersRow.select(".govuk-summary-list__value")
        .text() shouldBe "4"
      membersRow.select(".govuk-summary-list__actions")
        .text() shouldBe "Manage team members"
      membersRow.select(".govuk-summary-list__actions a")
        .attr("href") shouldBe "/agent-permissions/manage-group-team-members/groupId1"

      val unassignedClientsPanel =
        html.select(tabPanelWithIdOf("unassigned-clients"))
      unassignedClientsPanel.select("h2").text() shouldBe "Unassigned clients"
      val clientsTh = unassignedClientsPanel.select("table th")
      clientsTh.size() shouldBe 4
      clientsTh.get(1).text() shouldBe "Client reference"
      clientsTh.get(2).text() shouldBe "Tax reference"
      clientsTh.get(3).text() shouldBe "Tax service"

      val clientsTrs = unassignedClientsPanel.select("table tbody tr")
      clientsTrs.size() shouldBe 3

      val backlink = html.select(backLink)
      backlink.size() shouldBe 1
      backlink.attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
      backlink.text() shouldBe "Back"
    }

    "render content when filtered access groups and filtered clients in session" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(FILTERED_CLIENTS, displayClients))

      val groupSummaries = (1 to 3).map(i =>
        GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))
      await(sessionCacheRepo.putSession(FILTERED_GROUPS_INPUT, "Potato"))
      await(sessionCacheRepo.putSession(FILTERED_GROUP_SUMMARIES, groupSummaries))


      //when
      val result = controller.showManageGroups()(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Manage access groups - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage access groups"
      html
        .select("p#info")
        .get(0)
        .text() shouldBe "The team members in the group will be able to manage the tax affairs of clients in the group"

      //verify the tabs/tab headings first
      val tabs = html.select("li.govuk-tabs__list-item")
      tabs.size() shouldBe 2
      val accessGroupsTab = tabs.get(0)
      accessGroupsTab.hasClass("govuk-tabs__list-item--selected") shouldBe true
      accessGroupsTab.select("a").text() shouldBe "Access groups"
      accessGroupsTab.select("a").attr("href") shouldBe "#groups-panel"

      val unassignedClientsTab = tabs.get(1)
      unassignedClientsTab.hasClass("govuk-tabs__list-item--selected") shouldBe false
      unassignedClientsTab.select("a").text() shouldBe "Unassigned clients"
      unassignedClientsTab.select("a").attr("href") shouldBe "#unassigned-clients"

      //verify the tab panel contents
      val groupsPanel = html.select(tabPanelWithIdOf("groups-panel"))

      groupsPanel.select("h2").text() shouldBe "Access groups"
      groupsPanel.select("input#searchGroupByName").attr("value") shouldBe "Potato"
      val groups = groupsPanel.select("dl.govuk-summary-list")
      groups.size() shouldBe 3
      val firstGroup = groups.get(0)
      val clientsRow = firstGroup.select(".govuk-summary-list__row").get(0)
      clientsRow.select("dt").text() shouldBe "Clients"
      clientsRow.select(".govuk-summary-list__value")
        .text() shouldBe "3"
      clientsRow.select(".govuk-summary-list__actions")
        .text() shouldBe "Manage clients"
      clientsRow.select(".govuk-summary-list__actions a")
        .attr("href") shouldBe "/agent-permissions/manage-group-clients/groupId1"

      val membersRow = firstGroup.select(".govuk-summary-list__row").get(1)

      membersRow.select("dt").text() shouldBe "Team members"
      membersRow.select(".govuk-summary-list__value")
        .text() shouldBe "4"
      membersRow.select(".govuk-summary-list__actions")
        .text() shouldBe "Manage team members"
      membersRow.select(".govuk-summary-list__actions a")
        .attr("href") shouldBe "/agent-permissions/manage-group-team-members/groupId1"

      val unassignedClientsPanel =
        html.select(tabPanelWithIdOf("unassigned-clients"))
      unassignedClientsPanel.select("h2").text() shouldBe "Unassigned clients"
      val clientsTh = unassignedClientsPanel.select("table th")
      clientsTh.size() shouldBe 4
      clientsTh.get(1).text() shouldBe "Client reference"
      clientsTh.get(2).text() shouldBe "Tax reference"
      clientsTh.get(3).text() shouldBe "Tax service"

      val clientsTrs = unassignedClientsPanel.select("table tbody tr")
      clientsTrs.size() shouldBe 3

      val backlink = html.select(backLink)
      backlink.size() shouldBe 1
      backlink.attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
      backlink.text() shouldBe "Back"
    }
  }

  s"GET ${routes.ManageGroupController.showRenameGroup(groupId)}" should {

    "render correctly the manage groups page" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectGetGroupSuccess(groupId, Some(accessGroup))

      //when
      val result = controller.showRenameGroup(groupId)(request)

      //then
      status(result) shouldBe OK

      //and
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Rename group - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Rename group"
      html.select(Css.form).attr("action") shouldBe routes.ManageGroupController
        .submitRenameGroup(groupId)
        .url
      html
        .select(Css.labelFor("name"))
        .text() shouldBe "What do you want to call this access group?"
      html.select(Css.form + " input[name=name]").size() shouldBe 1
      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }

    "render NOT_FOUND when no group is found for this group id" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectGetGroupSuccess(groupId, Option.empty[AccessGroup])

      //when
      val result = controller.showRenameGroup(groupId)(request)

      status(result) shouldBe NOT_FOUND
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Access group not found - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Access group not found"
      html
        .select(Css.paragraphs)
        .text() shouldBe "Please check the url or return to the Manage groups page"
      html
        .select(Css.linkStyledAsButton)
        .text() shouldBe "Back to manage groups page"
      html
        .select(Css.linkStyledAsButton)
        .attr("href") shouldBe routes.ManageGroupController.showManageGroups.url
    }
  }

  s"POST ${routes.ManageGroupController.submitFilterByGroupName}" should {

    s"redirect to ${routes.ManageGroupController.showManageGroups} when search term submitted" in {

      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      val groupSummaries = (1 to 3).map(i =>
        GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))
      val unassignedClients = (1 to 8).map(i =>
        DisplayClient(s"hmrcRef$i", s"name$i", s"HMRC-MTD-IT", ""))
      val summaries = Some((groupSummaries, unassignedClients))

      expectGetGroupSummarySuccess(arn, summaries)

      implicit val request =
        FakeRequest("POST",
          routes.ManageGroupController.submitFilterByGroupName.url)
          .withFormUrlEncodedBody(
            "searchGroupByName" -> "name",
          "submitFilter" -> "submitFilter"
          )
          .withHeaders("Authorization" -> s"Bearer whatever")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitFilterByGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.ManageGroupController.showManageGroups.url

      await(sessionCacheRepo.getFromSession(FILTERED_GROUP_SUMMARIES)).get.size shouldBe 3


    }

    s"redirect to ${routes.ManageGroupController.showManageGroups} when clear button submitted" in {

      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      val groupSummaries = (1 to 3).map(i =>
        GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))

      implicit val request =
        FakeRequest("POST",
          routes.ManageGroupController.submitFilterByGroupName.url)
          .withFormUrlEncodedBody(
            "submitClear" -> "submitClear"
          )
          .withHeaders("Authorization" -> s"Bearer whatever")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(FILTERED_GROUP_SUMMARIES, groupSummaries))

      val result = controller.submitFilterByGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.ManageGroupController.showManageGroups.url

      await(sessionCacheRepo.getFromSession(FILTERED_GROUP_SUMMARIES)) shouldBe None
    }

    s"show errors when invalid submission " in {

      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      val groupSummaries = (1 to 3).map(i =>
        GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))
      val unassignedClients = (1 to 8).map(i =>
        DisplayClient(s"hmrcRef$i", s"name$i", s"HMRC-MTD-IT", ""))
      val summaries = Some((groupSummaries, unassignedClients))

      implicit val request =
        FakeRequest("POST",
          routes.ManageGroupController.submitFilterByGroupName.url)
          .withFormUrlEncodedBody(
            "searchGroupByName" -> "",
            "submitFilter" -> "submitFilter"
          )
          .withHeaders("Authorization" -> s"Bearer whatever")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      expectGetGroupSummarySuccess(arn, summaries)
      expectGetGroupSummarySuccess(arn, summaries)

      val result = controller.submitFilterByGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Error: Manage access groups - Agent services account - GOV.UK"

      html
        .select(Css.errorSummaryForField("searchGroupByName"))
        .text() shouldBe "You must enter a group name or part of it"
      html
        .select(Css.errorForField("searchGroupByName"))
        .text() shouldBe "Error: You must enter a group name or part of it"
    }
  }

  s"POST ${routes.ManageGroupController.submitRenameGroup(groupId)}" should {

    "redirect to confirmation page with when posting a valid group name" in {

      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectGetGroupSuccess(groupId, Some(accessGroup))
      expectUpdateGroupSuccess(groupId, UpdateAccessGroupRequest(Some("New Group Name"),None,None))

      implicit val request =
        FakeRequest("POST",
                    routes.ManageGroupController.submitRenameGroup(groupId).url)
          .withFormUrlEncodedBody("name" -> "New Group Name")
          .withHeaders("Authorization" -> s"Bearer whatever")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(
        sessionCacheRepo.putSession(GROUP_RENAMED_FROM, accessGroup.groupName))

      //when
      val result = controller.submitRenameGroup(groupId)(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.ManageGroupController
        .showGroupRenamed(groupId)
        .url
    }

    "redirect when no group is returned for this group id" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      implicit val request =
        FakeRequest("POST",
                    routes.ManageGroupController.submitRenameGroup(groupId).url)
          .withFormUrlEncodedBody("name" -> "New Group Name")
          .withHeaders("Authorization" -> s"Bearer whatever")
          .withSession(SessionKeys.sessionId -> "session-x")
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectGetGroupSuccess(groupId, Option.empty[AccessGroup])

      //when
      val result = controller.submitRenameGroup(groupId)(request)

      status(result) shouldBe NOT_FOUND
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Access group not found - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Access group not found"
      html
        .select(Css.paragraphs)
        .text() shouldBe "Please check the url or return to the Manage groups page"
      html
        .select(Css.linkStyledAsButton)
        .text() shouldBe "Back to manage groups page"
      html
        .select(Css.linkStyledAsButton)
        .attr("href") shouldBe routes.ManageGroupController.showManageGroups.url
    }

    "render errors when no group name is specified" in {
      //given
      implicit val request = FakeRequest("POST",
          routes.ManageGroupController.submitRenameGroup(groupId).url)
          .withFormUrlEncodedBody("name" -> "")
          .withHeaders("Authorization" -> s"Bearer whatever")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectGetGroupSuccess(groupId, Some(accessGroup))

      //when
      val result = controller.submitRenameGroup(groupId)(request)

      status(result) shouldBe OK
    }
  }

  s"GET ${routes.ManageGroupController.showGroupRenamed(groupId)}" should {

    "render correctly the manage groups page" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_RENAMED_FROM, "Previous Name"))
      expectGetGroupSuccess(groupId, Some(accessGroup))

      //when
      val result = controller.showGroupRenamed(groupId)(request)

      //then
      status(result) shouldBe OK

      //and
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Access group renamed - Agent services account - GOV.UK"
      html
        .select(Css.confirmationPanelH1)
        .text() shouldBe "Access group renamed"
      html
        .select(Css.confirmationPanelBody)
        .text() shouldBe "Previous Name access group renamed to Bananas"
      html.select(Css.H2).text() shouldBe "What happens next"
      html
        .select(Css.paragraphs)
        .get(0)
        .text() shouldBe "You can rename groups any time from the ‘manage access groups’ section"
      html.select(Css.backLink).size() shouldBe 0
    }
  }

  s"GET ${routes.ManageGroupController.showDeleteGroup(groupId)}" should {

    "render correctly the DELETE group page" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectGetGroupSuccess(groupId, Some(accessGroup))

      //when
      val result = controller.showDeleteGroup(groupId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Delete group - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Delete group"
      html
        .select(Css.form)
        .attr("action") shouldBe s"/agent-permissions/delete-group/${accessGroup._id}"
      html
        .select(Css.legend)
        .text() shouldBe s"Are you sure you want to delete ${accessGroup.groupName} access group?"
      html.select("label[for=answer-yes]").text() shouldBe "Yes"
      html.select("label[for=answer-no]").text() shouldBe "No"
      html.select(Css.form + " input[name=answer]").size() shouldBe 2
      html.select(Css.submitButton).text() shouldBe "Continue"
    }
  }

  s"POST ${routes.ManageGroupController.submitDeleteGroup(accessGroup._id.toString)}" should {

    "render correctly the confirm DELETE group page when 'yes' selected" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      implicit val request =
        FakeRequest("POST",
                    routes.ManageGroupController
                      .submitDeleteGroup(accessGroup._id.toString)
                      .url)
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
      expectDeleteGroupSuccess(accessGroup._id.toString)

      //when
      val result =
        controller.submitDeleteGroup(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe SEE_OTHER
      //and
      redirectLocation(result) shouldBe Some(
        routes.ManageGroupController.showGroupDeleted.url)

    }

    "render correctly the DASHBOARD group page when 'no' selected" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      implicit val request =
        FakeRequest("POST",
                    routes.ManageGroupController
                      .submitDeleteGroup(accessGroup._id.toString)
                      .url)
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      //when
      val result =
        controller.submitDeleteGroup(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe SEE_OTHER
      //and
      redirectLocation(result) shouldBe Some(
        routes.ManageGroupController.showManageGroups.url)

    }

    "render errors when no answer is specified" in {
      //given
      implicit val request = FakeRequest("POST",
        routes.ManageGroupController.submitDeleteGroup(groupId).url)
        .withFormUrlEncodedBody("answer" -> "")
        .withHeaders("Authorization" -> s"Bearer whatever")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectGetGroupSuccess(groupId, Some(accessGroup))

      //when
      val result = controller.submitDeleteGroup(groupId)(request)

      status(result) shouldBe OK
    }
  }

  s"GET ${routes.ManageGroupController.showGroupDeleted}" should {

    "render correctly" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_DELETED_NAME, "Rubbish"))

      //when
      val result = controller.showGroupDeleted(request)

      //then
      status(result) shouldBe OK

      //and
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Rubbish access group deleted - Agent services account - GOV.UK"
      html
        .select(Css.confirmationPanelH1)
        .text() shouldBe "Rubbish access group deleted"
      html.select(Css.H2).text() shouldBe "What happens next"
      html
        .select(Css.paragraphs)
        .get(0)
        .text() shouldBe "Clients from this group will now be visible to all team members, unless they are in other groups."
      html
        .select("a#returnToDashboard")
        .text() shouldBe "Return to manage access groups"
      html
        .select("a#returnToDashboard")
        .attr("href") shouldBe routes.ManageGroupController.showManageGroups.url
      html.select(Css.backLink).size() shouldBe 0
    }
  }

  s"POST ${routes.ManageGroupController.submitAddUnassignedClients}" should {
    s"save selected unassigned clients and redirect to ${routes.ManageGroupController.showSelectedUnassignedClients} " +
      s"when button is Continue" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubGetClientsOk(arn)(fakeClients)

      implicit val request =
        FakeRequest("POST", routes.ManageGroupController.submitAddUnassignedClients.url)
          .withFormUrlEncodedBody(
            "hasSelectedClients" -> "false",
            "clients[0]" -> displayClients.head.id,
            "clients[1]" -> displayClients.last.id,
            "search" -> "",
            "filter" -> "",
            "continue" -> "continue"
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitAddUnassignedClients()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe s"${routes.ManageGroupController.showSelectedUnassignedClients.url}"

      await(sessionCacheRepo.getFromSession(SELECTED_CLIENTS)) shouldBe Some(Seq(displayClients.head.copy(selected = true),
        displayClients.last.copy(selected = true)))

    }

    s"save selected unassigned clients and redirect to ${routes.ManageGroupController.showManageGroups}#unassigned-clients " +
      s"when button is NOT Continue" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubGetClientsOk(arn)(fakeClients)
      expectGetGroupSummarySuccess(arn, Some(Seq.empty, displayClients))


      implicit val request =
        FakeRequest("POST", routes.ManageGroupController.submitAddUnassignedClients.url)
          .withFormUrlEncodedBody(
            "hasSelectedClients" -> "false",
            "clients[0]" -> displayClients.head.id,
            "clients[1]" -> displayClients.last.id,
            "search" -> "",
            "filter" -> "VAT",
            "submitFilter" -> "submitFilter"
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitAddUnassignedClients()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe s"${routes.ManageGroupController.showManageGroups}#unassigned-clients"

      await(sessionCacheRepo.getFromSession(SELECTED_CLIENTS)) shouldBe Some(Seq(displayClients.head.copy(selected = true),
        displayClients.last.copy(selected = true)))

    }

    s"present page with errors when form validation fails" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectGetGroupSummarySuccess(arn, None)

      implicit val request =
        FakeRequest("POST", routes.ManageGroupController.submitAddUnassignedClients.url)
          .withFormUrlEncodedBody(
            "hasSelectedClients" -> "false",
            "search" -> "",
            "filter" -> "",
            "submitFilter" -> "submitFilter"
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitAddUnassignedClients()(request)

      status(result) shouldBe OK

    }

    s"present page with errors when form validation fails and filtered clients exist" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectGetGroupSummarySuccess(arn, None)

      implicit val request =
        FakeRequest("POST", routes.ManageGroupController.submitAddUnassignedClients.url)
          .withFormUrlEncodedBody(
            "hasSelectedClients" -> "false",
            "search" -> "",
            "filter" -> "",
            "continue" -> "continue"
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(FILTERED_CLIENTS, displayClients))

      val result = controller.submitAddUnassignedClients()(request)

      status(result) shouldBe OK

      await(sessionCacheRepo.getFromSession(SELECTED_CLIENTS)) shouldBe None

    }
  }

  s"GET ${routes.ManageGroupController.showSelectedUnassignedClients}" should {

    "redirect if no clients selected are in session" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectIsArnAllowed(true)
      expectAuthorisationGrantsAccess(mockedAuthResponse)

      //when
      val result = controller.showSelectedUnassignedClients(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.ManageGroupController.showManageGroups.url
    }

    "render correctly the selected unassigned clients page" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      //when
      val result = controller.showSelectedUnassignedClients(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Review selected clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 3 clients"
      html.select(Css.tableWithId("sortable-table")).select("tbody tr").size() shouldBe 3
      //and the back link should go to the unassigned clients tab
      html.select(Css.backLink).attr("href") shouldBe "/agent-permissions/manage-access-groups#unassigned-clients"

    }
  }

  s"GET ${routes.ManageGroupController.showSelectGroupsForSelectedUnassignedClients}" should {

    "render correctly the select groups for unassigned clients page" in {
      //given
      val groupSummaries = (1 to 3).map(i =>
        GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectGetGroupSummarySuccess(arn, Some((groupSummaries, Seq.empty[DisplayClient])))

      //when
      val result = controller.showSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Which access groups would you like to add the selected clients to? - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Which access groups would you like to add the selected clients to?"
      //and the back link should go to the unassigned clients tab
      html.select(Css.backLink).attr("href") shouldBe routes.ManageGroupController.showSelectedUnassignedClients.url

    }
  }

  s"POST ${routes.ManageGroupController.submitSelectGroupsForSelectedUnassignedClients}" should{

    "redirect to create group if CREATE NEW is selected" in {
      //given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", routes.ManageGroupController.submitSelectGroupsForSelectedUnassignedClients.url)
          .withFormUrlEncodedBody("createNew" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      //when
      val result = controller.submitSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showGroupName.url

    }

    "redirect to confirmation page when existing groups are selected to assign the selected clients to" in {
      //given
      val groupSummaries = (1 to 3).map(i => GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", routes.ManageGroupController.submitSelectGroupsForSelectedUnassignedClients.url)
          .withFormUrlEncodedBody("groups[0]" -> "12412312")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectGetGroupSummarySuccess(arn, Some((groupSummaries, Seq.empty[DisplayClient])))

      //when
      val result = controller.submitSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.ManageGroupController.showConfirmClientsAddedToGroups.url

    }

    "show errors when nothing selected" in {
      //given
      val groupSummaries = (1 to 3).map(i =>
        GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", routes.ManageGroupController.submitSelectGroupsForSelectedUnassignedClients.url)
          .withFormUrlEncodedBody()
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectGetGroupSummarySuccess(arn, Some((groupSummaries, Seq.empty[DisplayClient])))

      //when
      val result = controller.submitSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe OK
      //and should show errors
      val html = Jsoup.parse(contentAsString(result))
      html.select(Css.errorSummaryForField("field-wrapper")).text() shouldBe "You must select an access group or add a new group"
      html.select(Css.errorForField("field-wrapper")).text() shouldBe "You must select an access group or add a new group"


    }

    "show errors when both createNew and existing groups are selected" in {
      //given
      val groupSummaries = (1 to 3).map(i =>
        GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", routes.ManageGroupController.submitSelectGroupsForSelectedUnassignedClients.url)
          .withFormUrlEncodedBody("createNew" -> "true","groups[0]" -> "12412312")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectGetGroupSummarySuccess(arn, Some((groupSummaries, Seq.empty[DisplayClient])))

      //when
      val result = controller.submitSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe OK
      //and should show errors
      val html = Jsoup.parse(contentAsString(result))
      html.select(Css.errorSummaryForField("field-wrapper")).text() shouldBe "You cannot add to existing groups at the same time as creating a new group"
      html.select(Css.errorForField("field-wrapper")).text() shouldBe "You cannot add to existing groups at the same time as creating a new group"


    }
  }

  s"GET ${routes.ManageGroupController.showConfirmClientsAddedToGroups}" should {

    "render correctly the select groups for unassigned clients page" in {
      //given
      val groups = Seq("South West", "London")
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUPS_FOR_UNASSIGNED_CLIENTS, groups))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      //when
      val result = controller.showConfirmClientsAddedToGroups(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Clients added to access groups - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Clients added to access groups"
      val paragraphs = html.select(Css.paragraphs)
      paragraphs.get(0).text() shouldBe "You have added these clients to the following groups:"
      val listItems = html.select("ul.govuk-list li.govuk-list--item")
      listItems.size() shouldBe groups.size
      listItems.get(0).text shouldBe groups(0)
      listItems.get(1).text shouldBe groups(1)
      //and the back link should go to the unassigned clients tab
      html.select(Css.backLink).size() shouldBe 0

    }

    "redirect when no group names in session" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      //when
      val result = controller.showConfirmClientsAddedToGroups(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.ManageGroupController.showSelectGroupsForSelectedUnassignedClients.url

    }
  }

}
