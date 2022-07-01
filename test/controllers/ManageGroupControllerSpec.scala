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
import helpers.Css._
import helpers.{BaseSpec, Css}
import models.DisplayClient
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{NOT_FOUND, OK, SEE_OTHER}
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services.GroupService
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, OptedInReady}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

import java.time.LocalDate

class ManageGroupControllerSpec extends BaseSpec {


  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]

  lazy val sessionCacheRepo: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)
  val groupId = "xyz"
  private val agentUser: AgentUser = AgentUser(RandomStringUtils.random(5), "Rob the Agent")
  val accessGroup = AccessGroup(
    arn,
    "Bananas",
    LocalDate.of(2020, 3, 10).atStartOfDay(),
    null,
    agentUser, agentUser, None, None)

  override def moduleWithOverrides = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepo)
      bind(classOf[GroupService]).toInstance(mockGroupService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val controller = fakeApplication.injector.instanceOf[ManageGroupController]

  s"GET ${routes.ManageGroupController.showManageGroups}" should {

    "render correctly the manage groups page" in {

      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      val groupSummaries = (1 to 3).map(i =>
        GroupSummary(s"groupId$i", s"name ${i}", i * 3, i * 4)
      )
      val unassignedClients = (1 to 8).map(i =>
        DisplayClient(s"hmrcRef$i", s"name$i", s"taxService$i", "")
      )
      val summaries = Some((groupSummaries, unassignedClients))
      expectGetGroupSummarySuccess(arn, summaries)

      //when
      val result = controller.showManageGroups()(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Manage access groups - Manage Agent Permissions - GOV.UK"
      html.select(H1).text() shouldBe "Manage access groups"
      html.select("p#info").get(0).text() shouldBe "The team members in the group will be able to manage the tax affairs of clients in the group"

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
      unassignedClientsTab.select("a").attr("href") shouldBe "#clients-panel"

      //verify the tab panel contents
      val groupsPanel = html.select(tabPanelWithIdOf("groups-panel"))
      groupsPanel.select("h2").text() shouldBe "Access groups"
      val groups = groupsPanel.select("dl.govuk-summary-list")
      groups.size() shouldBe 3

      val unassignedClientsPanel = html.select(tabPanelWithIdOf("clients-panel"))
      unassignedClientsPanel.select("h2").text() shouldBe "Unassigned clients"
      val clientsTh = unassignedClientsPanel.select("table th")
      clientsTh.size() shouldBe 3
      clientsTh.get(0).text() shouldBe "Client name"
      clientsTh.get(1).text() shouldBe "Tax reference"
      clientsTh.get(2).text() shouldBe "Tax service"

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
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val summaries = None
      expectGetGroupSummarySuccess(arn, summaries)

      //when
      val result = controller.showManageGroups()(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Manage access groups - Manage Agent Permissions - GOV.UK"
      html.select(H1).text() shouldBe "Manage access groups"
      html.select("p#info").get(0).text() shouldBe "The team members in the group will be able to manage the tax affairs of clients in the group"

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
      unassignedClientsTab.select("a").attr("href") shouldBe "#clients-panel"

      //verify the tab panel contents
      val groupsPanel = html.select(tabPanelWithIdOf("groups-panel"))
      groupsPanel.select("h2").text() shouldBe "Access groups"
      groupsPanel.select("h3").text() shouldBe "No groups found"
      groupsPanel.select("a").text() shouldBe "Create new access group"
      groupsPanel.select("a").attr("href") shouldBe routes.GroupController.showGroupName.url
      groupsPanel.select("a").hasClass("govuk-button") shouldBe true

      val unassignedClientsPanel = html.select(tabPanelWithIdOf("clients-panel"))
      unassignedClientsPanel.select("h2").text() shouldBe "Unassigned clients"
      unassignedClientsPanel.select("h3").text() shouldBe "No unassigned clients found"
      val table = unassignedClientsPanel.select("table")
      table.size() shouldBe 0

      val backlink = html.select(backLink)
      backlink.size() shouldBe 1
      backlink.attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
      backlink.text() shouldBe "Back"
    }
  }

  s"GET ${routes.ManageGroupController.showManageGroupClients(groupId)}" should {

    "render correctly the manage group clients page" in {

      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.showManageGroupClients(groupId)(request)

      //when
      status(result) shouldBe OK

      //then
      val html = Jsoup.parse(contentAsString(result))
      html.body.text shouldBe "showManageGroupClients not yet implemented xyz"
    }
  }

  s"GET ${routes.ManageGroupController.showManageGroupTeamMembers(groupId)}" should {

    "render correctly the manage group clients page" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      //when
      val result = controller.showManageGroupTeamMembers(groupId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.body.text shouldBe "showManageGroupTeamMembers not yet implemented xyz"
    }
  }

  s"GET ${routes.ManageGroupController.showRenameGroup(groupId)}" should {

    "render correctly the manage groups page" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectGetGroupSuccess(groupId, Some(accessGroup))

      //when
      val result = controller.showRenameGroup(groupId)(request)

      //then
      status(result) shouldBe OK

      //and
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Rename group - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Rename group"
      html.select(Css.form).attr("action") shouldBe routes.ManageGroupController.submitRenameGroup(groupId).url
      html.select(Css.labelFor("name")).text() shouldBe "What do you want to call this access group?"
      html.select(Css.form + " input[name=name]").size() shouldBe 1
      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }

    "render NOT_FOUND when no group is found for this group id" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectGetGroupSuccess(groupId, Option.empty[AccessGroup])

      //when
      val result = controller.showRenameGroup(groupId)(request)

      status(result) shouldBe NOT_FOUND
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Access group not found - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Access group not found"
      html.select(Css.paragraphs).text() shouldBe "Please check the url or return to the Manage groups page"
      html.select(Css.linkStyledAsButton).text() shouldBe "Back to manage groups page"
      html.select(Css.linkStyledAsButton).attr("href") shouldBe routes.ManageGroupController.showManageGroups.url
    }
  }

  s"POST ${routes.ManageGroupController.submitRenameGroup(groupId)}" should {

    "redirect to confirmation page with when posting a valid group name" in {

      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetGroupSuccess(groupId, Some(accessGroup))

      implicit val request = FakeRequest("POST", routes.ManageGroupController.submitRenameGroup(groupId).url)
        .withFormUrlEncodedBody("name" -> "New Group Name")
        .withHeaders("Authorization" -> s"Bearer whatever")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_RENAMED_FROM, accessGroup.groupName))

      //when
      val result = controller.submitRenameGroup(groupId)(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.ManageGroupController.showGroupRenamed(groupId).url
    }

    "redirect when no group is returned for this group id" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      implicit val request = FakeRequest("POST", routes.ManageGroupController.submitRenameGroup(groupId).url)
        .withFormUrlEncodedBody("name" -> "New Group Name")
        .withHeaders("Authorization" -> s"Bearer whatever")
        .withSession(SessionKeys.sessionId -> "session-x")
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectGetGroupSuccess(groupId, Option.empty[AccessGroup])

      //when
      val result = controller.submitRenameGroup(groupId)(request)

      status(result) shouldBe NOT_FOUND
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Access group not found - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Access group not found"
      html.select(Css.paragraphs).text() shouldBe "Please check the url or return to the Manage groups page"
      html.select(Css.linkStyledAsButton).text() shouldBe "Back to manage groups page"
      html.select(Css.linkStyledAsButton).attr("href") shouldBe routes.ManageGroupController.showManageGroups.url
    }
  }

  s"GET ${routes.ManageGroupController.showGroupRenamed(groupId)}" should {

    "render correctly the manage groups page" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_RENAMED_FROM, "Previous Name"))
      expectGetGroupSuccess(groupId, Some(accessGroup))

      //when
      val result = controller.showGroupRenamed(groupId)(request)

      //then
      status(result) shouldBe OK

      //and
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Access group renamed - Manage Agent Permissions - GOV.UK"
      html.select(Css.confirmationPanelH1).text() shouldBe "Access group renamed"
      html.select(Css.confirmationPanelBody).text() shouldBe "Previous Name access group renamed to Bananas"
      html.select(Css.H2).text() shouldBe "What happens next?"
      html.select(Css.paragraphs).get(0).text() shouldBe "You can rename groups any time from the ‘manage access groups’ section"
      html.select(Css.backLink).size() shouldBe 0
    }
  }

  s"GET ${routes.ManageGroupController.showDeleteGroup(groupId)}" should {

    "render correctly the DELETE group page" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectGetGroupSuccess(groupId, Some(accessGroup))

      //when
      val result = controller.showDeleteGroup(groupId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Delete group - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Delete group"
      html.select(Css.form).attr("action") shouldBe s"/agent-permissions/delete-group/${accessGroup._id}"
      html.select(Css.legend).text() shouldBe s"Are you sure you want to delete ${accessGroup.groupName} access group?"
      html.select("label[for=answer-yes]").text() shouldBe "Yes"
      html.select("label[for=answer-no]").text() shouldBe "No"
      html.select(Css.form + " input[name=answer]").size() shouldBe 2
      html.select(Css.submitButton).text() shouldBe "Continue"
    }
  }

  s"POST ${routes.ManageGroupController.submitDeleteGroup(accessGroup._id.toString)}" should {

    "render correctly the confirm DELETE group page" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)

      implicit val request = FakeRequest("POST", routes.GroupController.submitGroupName.url)
        .withFormUrlEncodedBody("answer" -> "true")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      //when
      val result = controller.submitDeleteGroup(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.body().text() shouldBe s"not implemented ${accessGroup._id}"

    }
  }

  s"GET ${routes.ManageGroupController.showManageGroupClients(groupId)}" should {

    "render correctly the manage group CLIENTS page" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      //when
      val result = controller.showManageGroupClients(groupId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.body().text() shouldBe "showManageGroupClients not yet implemented xyz"
    }
  }

  s"GET ${routes.ManageGroupController.showManageGroupTeamMembers(groupId)}" should {

    "render correctly the manage group TEAM MEMBERS page" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      //when
      val result = controller.showManageGroupTeamMembers(groupId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.body().text() shouldBe "showManageGroupTeamMembers not yet implemented xyz"
    }
  }
}
