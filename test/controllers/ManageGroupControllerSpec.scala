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
import helpers.{BaseSpec}
import models.DisplayClient
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.OK
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout}
import repository.SessionCacheRepository
import services.GroupService
import uk.gov.hmrc.agentmtdidentifiers.model.OptedInReady
import uk.gov.hmrc.auth.core.AuthConnector

class ManageGroupControllerSpec extends BaseSpec {


  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]

  lazy val sessionCacheRepo: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)
  val groupId = "xyz"

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

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      val groupSummaries = (1 to 3).map(i =>
        GroupSummary(s"groupId$i", s"name ${i}", i * 3, i * 4)
      )
      val unassignedClients = (1 to 8).map(i =>
        DisplayClient(s"hmrcRef$i", s"name$i", s"taxService$i", "")
      )
      val summaries = (groupSummaries, unassignedClients)
      expectGetGroupSummarySuccess(arn, summaries)

      val result = controller.showManageGroups()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Manage access groups - Manage Agent Permissions - GOV.UK"
      html.select(H1).text() shouldBe "Manage access groups"
      html.select("p#info").get(0).text() shouldBe "The team members in the group will be able to manage the tax affairs of clients in the group"

      //verify the tabs/tab headings first
      val tabs = html.select("li.govuk-tabs__list-item")
      tabs.size() shouldBe 2
      val accessGroupsTab = tabs.get(0)
      accessGroupsTab.hasClass("govuk-tabs__list-item--selected")  shouldBe true
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

      html.select(backLink).size() shouldBe 0



    }

    "render correctly the manage groups page when nothing returned" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val summaries = (Seq.empty[GroupSummary], Seq.empty[DisplayClient])
      expectGetGroupSummarySuccess(arn, summaries)

      val result = controller.showManageGroups()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Manage access groups - Manage Agent Permissions - GOV.UK"
      html.select(H1).text() shouldBe "Manage access groups"
      html.select("p#info").get(0).text() shouldBe "The team members in the group will be able to manage the tax affairs of clients in the group"

      //verify the tabs/tab headings first
      val tabs = html.select("li.govuk-tabs__list-item")
      tabs.size() shouldBe 2
      val accessGroupsTab = tabs.get(0)
      accessGroupsTab.hasClass("govuk-tabs__list-item--selected")  shouldBe true
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

      html.select(backLink).size() shouldBe 0

    }
  }

  s"GET ${routes.ManageGroupController.showManageGroupClients(groupId)}" should {

    "render correctly the manage group clients page" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.showManageGroupClients(groupId)(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.body.text shouldBe "showManageGroupClients not yet implemented xyz"
    }
  }

  s"GET ${routes.ManageGroupController.showManageGroupTeamMembers(groupId)}" should {

    "render correctly the manage group clients page" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.showManageGroupTeamMembers(groupId)(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.body.text shouldBe "showManageGroupTeamMembers not yet implemented xyz"
    }
  }
}
