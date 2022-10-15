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
import play.api.test.Helpers.{GET, await, contentAsString, defaultAwaitTimeout, redirectLocation}
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

  s"GET ${routes.ManageGroupController.showManageGroups.url}" should {

    "render correctly the manage groups page" in {

      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      val groupSummaries = (1 to 3).map(i => GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))
      expectGetGroupSummarySuccess(arn, groupSummaries)

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
        .text() shouldBe "The team members in the group will be able to manage the tax affairs of clients in the group."

      val groups = html.select("dl.govuk-summary-list")
      groups.size() shouldBe 3

      // check first group
      html.select(H2).get(0).text() shouldBe "name 1"
      val firstGroup = groups.get(0)
      val clientsRow = firstGroup.select(".govuk-summary-list__row").get(0)
      clientsRow.select("dt").text() shouldBe "Clients"
      clientsRow.select(".govuk-summary-list__value")
        .text() shouldBe "3"
      clientsRow.select(".govuk-summary-list__actions")
        .text() shouldBe "Manage clients for name 1"
      clientsRow.select(".govuk-summary-list__actions a")
        .attr("href") shouldBe "/agent-permissions/manage-clients/groupId1"

      val membersRow = firstGroup.select(".govuk-summary-list__row").get(1)
      membersRow.select("dt").text() shouldBe "Team members"
      membersRow.select(".govuk-summary-list__value")
        .text() shouldBe "4"
      membersRow.select(".govuk-summary-list__actions")
        .text() shouldBe "Manage team members for name 1"
      membersRow.select(".govuk-summary-list__actions a")
        .attr("href") shouldBe "/agent-permissions/manage-team-members/groupId1"

      val backlink = html.select(backLink)
      backlink.size() shouldBe 1
      backlink.attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
      backlink.text() shouldBe "Back"

    }

    "render correctly the manage groups page when nothing returned" in {

      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val summaries = None
      expectGetGroupSummarySuccess(arn, Seq.empty)

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
        .text() shouldBe "The team members in the group will be able to manage the tax affairs of clients in the group."

      html.select(H2).text() shouldBe "No groups found"
      val buttonLink = html.select("a#button-link")
      buttonLink.text() shouldBe "Create new access group"
      buttonLink
        .attr("href") shouldBe routes.CreateGroupController.showGroupName.url
      buttonLink.hasClass("govuk-button") shouldBe true

      val backlink = html.select(backLink)
      backlink.size() shouldBe 1
      backlink.attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
      backlink.text() shouldBe "Back"
    }

    "render content when filtered access groups" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val expectedGroupSummaries = (1 to 3).map(i => GroupSummary(s"groupId$i", s"GroupName$i", i * 3, i * 4))
      expectGetGroupSummarySuccess(arn, expectedGroupSummaries)
      val searchTerm = expectedGroupSummaries(0).groupName
      val requestWithQuery = FakeRequest(GET,
         routes.ManageGroupController.showManageGroups.url + s"?submit=filter&search=$searchTerm")
        .withHeaders("AuthorizatiManageGroupControllerSpec.scala:229on" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showManageGroups()(requestWithQuery)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Manage access groups - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage access groups"
      html
        .select("p#info")
        .get(0)
        .text() shouldBe "The team members in the group will be able to manage the tax affairs of clients in the group."

      searchTerm shouldBe "GroupName1"
      html.select("input#search").attr("value") shouldBe searchTerm

      val groups = html.select("dl.govuk-summary-list")
      groups.size() shouldBe 1

      // check first group contents
      html.select(H2).get(0).text() shouldBe "GroupName1"
      val firstGroup = groups.get(0)
      val clientsRow = firstGroup.select(".govuk-summary-list__row").get(0)
      clientsRow.select("dt").text() shouldBe "Clients"
      clientsRow.select(".govuk-summary-list__value")
        .text() shouldBe "3"
      clientsRow.select(".govuk-summary-list__actions")
        .text() shouldBe "Manage clients for GroupName1"
      clientsRow.select(".govuk-summary-list__actions a")
        .attr("href") shouldBe "/agent-permissions/manage-clients/groupId1"

      val membersRow = firstGroup.select(".govuk-summary-list__row").get(1)
      membersRow.select("dt").text() shouldBe "Team members"
      membersRow.select(".govuk-summary-list__value").text() shouldBe "4"
      membersRow.select(".govuk-summary-list__actions")
        .text() shouldBe "Manage team members for GroupName1"
      membersRow.select(".govuk-summary-list__actions a")
        .attr("href") shouldBe "/agent-permissions/manage-team-members/groupId1"

      val backlink = html.select(backLink)
      backlink.size() shouldBe 1
      backlink.attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
      backlink.text() shouldBe "Back"
    }
  }

  s"GET ${routes.ManageGroupController.showRenameGroup(groupId).url}" should {

    "render correctly the manage groups page" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
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
      expectIsArnAllowed(allowed = true)
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

  s"POST ${routes.ManageGroupController.submitRenameGroup(groupId).url}" should {

    "redirect to confirmation page with when posting a valid group name" in {

      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(groupId, Some(accessGroup))
      expectUpdateGroupSuccess(groupId, UpdateAccessGroupRequest(Some("New Group Name"),None,None))

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
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
      expectIsArnAllowed(allowed = true)
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
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
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST",
          routes.ManageGroupController.submitRenameGroup(groupId).url)
          .withFormUrlEncodedBody("name" -> "")
          .withHeaders("Authorization" -> s"Bearer whatever")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(groupId, Some(accessGroup))

      //when
      val result = controller.submitRenameGroup(groupId)(request)

      status(result) shouldBe OK
    }
  }

  s"GET ${routes.ManageGroupController.showGroupRenamed(groupId).url}" should {

    "render correctly the manage groups page" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
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
      //we have removed the "what happens next h2 and paragraph.
      // so just check it's not there in case someone merges it back
      html.select(Css.H2).size() shouldBe 0
      html.select(Css.paragraphs).size() shouldBe 0
      html.select(Css.backLink).size() shouldBe 0
      val dashboardLink = html.select("main a#back-to-dashboard")
      dashboardLink.text() shouldBe "Return to manage access groups"
      dashboardLink.attr("href") shouldBe routes.ManageGroupController.showManageGroups.url
    }
  }

  s"GET ${routes.ManageGroupController.showDeleteGroup(groupId).url}" should {

    "render correctly the DELETE group page" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
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
        .attr("action") shouldBe s"/agent-permissions/delete-access-group/${accessGroup._id}"
      html
        .select(Css.legend)
        .text() shouldBe s"Are you sure you want to delete ${accessGroup.groupName} access group?"
      html.select("label[for=answer]").text() shouldBe "Yes"
      html.select("label[for=answer-no]").text() shouldBe "No"
      html.select(Css.form + " input[name=answer]").size() shouldBe 2
      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }
  }

  s"POST ${routes.ManageGroupController.submitDeleteGroup(accessGroup._id.toString).url}" should {

    "render correctly the confirm DELETE group page when 'yes' selected" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
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
      expectIsArnAllowed(allowed = true)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
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
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST",
        routes.ManageGroupController.submitDeleteGroup(groupId).url)
        .withFormUrlEncodedBody("answer" -> "")
        .withHeaders("Authorization" -> s"Bearer whatever")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(groupId, Some(accessGroup))

      //when
      val result = controller.submitDeleteGroup(groupId)(request)

      status(result) shouldBe OK
    }
  }

  s"GET ${routes.ManageGroupController.showGroupDeleted.url}" should {

    "render correctly" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
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

}
