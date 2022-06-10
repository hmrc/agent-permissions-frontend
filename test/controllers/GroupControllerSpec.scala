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
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector}
import helpers.{BaseSpec, Css}
import models.{DisplayClient, TeamMember}
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services.GroupService
import uk.gov.hmrc.agentmtdidentifiers.model.{Client, OptedInReady}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

import java.util.Base64

class GroupControllerSpec extends BaseSpec {


  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]

  lazy val sessionCacheRepo: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)

  override def moduleWithOverrides = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepo)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[GroupService]).toInstance(mockGroupService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val controller = fakeApplication.injector.instanceOf[GroupController]


  "GET /group/create-access-group" should {
    "redirect to /group/group-name" in {

      val result = controller.start()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe routes.GroupController.showGroupName.url
    }
  }

  "GET /group/group-name" should {

    "have correct layout and content" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.showGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Create an access group - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Create an access group"
      html.select(Css.form).attr("action") shouldBe "/agent-permissions/group/group-name"
      html.select(Css.labelFor("name")).text() shouldBe "What do you want to call this access group?"
      html.select(Css.form + " input[name=name]").size() shouldBe 1
      html.select(Css.SUBMIT_BUTTON).text() shouldBe "Save and continue"
    }
  }

  private val groupName = "XYZ"

  "POST /group/group-name" should {

    "redirect to confirmation page with when posting a valid group name" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)

      implicit val request = FakeRequest("POST", routes.GroupController.submitGroupName.url)
        .withFormUrlEncodedBody("name" -> "My Group Name")
        .withHeaders("Authorization" -> s"Bearer $groupName")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showConfirmGroupName.url
    }

    "render correct error messages when form not filled in" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)

      implicit val request = FakeRequest("POST", routes.GroupController.submitGroupName.url)
        .withFormUrlEncodedBody("name" -> "")
        .withHeaders("Authorization" -> s"Bearer $groupName")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Create an access group - Manage Agent Permissions - GOV.UK"
      html.select(Css.errorSummaryForField("name")).text() shouldBe "Enter an access group name"
      html.select(Css.errorForField("name")).text() shouldBe "Error: Enter an access group name"

    }

    "render correct error messages when name exceeds 32 chars" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)

      implicit val request = FakeRequest("POST", routes.GroupController.submitGroupName.url)
        .withFormUrlEncodedBody("name" -> RandomStringUtils.random(33))
        .withHeaders("Authorization" -> s"Bearer $groupName")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Create an access group - Manage Agent Permissions - GOV.UK"
      html.select(Css.errorSummaryForField("name")).text() shouldBe "Access group name must be 38 characters or fewer"
      html.select(Css.errorForField("name")).text() shouldBe "Error: Access group name must be 38 characters or fewer"
    }
  }

  "GET /group/confirm-name" should {

    "have correct layout and content" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))

      val result = controller.showConfirmGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Confirm group name for " + groupName + " - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Confirm group name for " + groupName
      html.select(Css.form).attr("action") shouldBe "/agent-permissions/group/confirm-name"
      html.select(Css.legend).text() shouldBe "Is the access group name correct?"
      html.select("label[for=answer-yes]").text() shouldBe "Yes"
      html.select("label[for=answer-no]").text() shouldBe "No"
      html.select(Css.form + " input[name=answer]").size() shouldBe 2
      html.select(Css.SUBMIT_BUTTON).text() shouldBe "Continue"
    }

    "redirect to /group/group-name when there is no groupName in the session" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.showConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showGroupName.url
    }
  }

  "POST /group/confirm-name" should {
    "render correct error messages when nothing is submitted" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)

      implicit val request = FakeRequest("POST", routes.GroupController.submitConfirmGroupName.url)
        .withFormUrlEncodedBody("answer" -> "")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Confirm group name for " + groupName + " - Manage Agent Permissions - GOV.UK"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if the access group name is correct"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if the access group name is correct"

    }

    "redirect to /group/group-name when there is no name in session" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)

      implicit val request = FakeRequest("POST", routes.GroupController.submitConfirmGroupName.url)
        .withFormUrlEncodedBody("answer" -> "true")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showGroupName.url
    }

    "redirect to add-clients page when confirm group name 'yes' selected" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)

      implicit val request = FakeRequest("POST", routes.GroupController.submitGroupName.url)
        .withFormUrlEncodedBody("name" -> groupName, "answer" -> "true")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.GroupController.showAddClients.url)
    }

    "redirect to /group/group-name when confirm group name 'no' selected" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)

      implicit val request = FakeRequest("POST", routes.GroupController.submitConfirmGroupName.url)
        .withFormUrlEncodedBody("name" -> groupName, "answer" -> "false")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.GroupController.showGroupName.url)
    }
  }

  s"GET ${routes.GroupController.showAddClients.url}" should {

    "render with clients" in {

      val fakeClients = (1 to 10)
        .map(i => {
          Client(
            s"HMRC-MTD-VAT~VRN~12345678$i",
            s"friendly$i",
          )
        })
      val displayClients = fakeClients.map(client => DisplayClient.fromClient(client))

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubGetClients(arn)(displayClients)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

      val result = controller.showAddClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select clients - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"
      val th = html.select(Css.tableWithId("client-list-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Client name"
      th.get(2).text() shouldBe "Tax reference"
      th.get(3).text() shouldBe "Tax service"
      val trs = html.select(Css.tableWithId("client-list-table")).select("tbody tr")

      trs.size() shouldBe 10
      //first row
      trs.get(0).select("td").get(1).text() shouldBe "friendly1"
      trs.get(0).select("td").get(2).text() shouldBe "123456781"
      trs.get(0).select("td").get(3).text() shouldBe "HMRC-MTD-VAT"

      //last row
      trs.get(9).select("td").get(1).text() shouldBe "friendly10"
      trs.get(9).select("td").get(2).text() shouldBe "1234567810"
      trs.get(9).select("td").get(3).text() shouldBe "HMRC-MTD-VAT"
    }

    "render with No CLIENTS" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubGetClients(arn)(Seq.empty)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

      val result = controller.showAddClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Select clients - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe s"Select clients"
      val th = html.select(Css.tableWithId("client-list-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Client name"
      th.get(2).text() shouldBe "Tax reference"
      th.get(3).text() shouldBe "Tax service"
      val trs = html.select(Css.tableWithId("client-list-table")).select("tbody tr")
      trs.size() shouldBe 0

    }

    "redirect when no group name is in session" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.showAddClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showGroupName.url
    }
  }

  "POST add clients to list" should {

    s"save selected clients to session and redirect to ${routes.GroupController.showReviewClientsToAdd.url}" in {
      val fakeClients =
        List.tabulate(3)(i =>
          Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))
      val displayClients = fakeClients.map(client => DisplayClient.fromClient(client))

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubGetClients(arn)(displayClients)
      val encodedDisplayClients = displayClients.map(client => Base64.getEncoder.encodeToString(Json.toJson(client).toString.getBytes))

      implicit val request = FakeRequest("POST", routes.GroupController.submitAddClients.url)
        .withFormUrlEncodedBody("clients[]" -> encodedDisplayClients.head, "clients[]" -> encodedDisplayClients.last)
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, "XYZ"))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

      val result = controller.submitAddClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showReviewClientsToAdd.url
      val storedClients = await(sessionCacheRepo.getFromSession(GROUP_CLIENTS_SELECTED))
      storedClients.get.toList shouldBe List(displayClients.head.copy(selected = true), displayClients.last.copy(selected = true))
    }

    "show an error when POSTED without clients" in {

      val fakeClients =
        List.tabulate(3)(i =>
          Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))
      val displayClients = fakeClients.map(client => DisplayClient.fromClient(client))

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubGetClients(arn)(displayClients)

      implicit val request = FakeRequest("POST", routes.GroupController.submitAddClients.url)
        .withFormUrlEncodedBody()
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, "XYZ"))


      val result = controller.submitAddClients()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Error: Select clients - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"
      html.select(Css.errorSummaryForField("clients")).text() shouldBe "You must add at least one client"
    }
  }

  s"GET ${routes.GroupController.showReviewClientsToAdd.url}" should {

    "render with selected clients" in {

      val selectedClients = (1 to 10)
        .map(i => {
          DisplayClient(
            s"1234567$i",
            s"client name $i",
            s"tax service $i",
            true
          )
        })

      stubAuthorisationGrantAccess(mockedAuthResponse)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))
      await(sessionCacheRepo.putSession(GROUP_CLIENTS_SELECTED, selectedClients))

      val result = controller.showReviewClientsToAdd()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Select clients - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe s"You have selected 10 clients"

      val th = html.select(Css.tableWithId("client-list-table")).select("thead th")
      th.size() shouldBe 3
      th.get(0).text() shouldBe "Client name"
      th.get(1).text() shouldBe "Tax reference"
      th.get(2).text() shouldBe "Tax service"
      val trs = html.select(Css.tableWithId("client-list-table")).select("tbody tr")
      trs.size() shouldBe 10
      //first row
      trs.get(0).select("td").get(0).text() shouldBe "client name 1"
      trs.get(0).select("td").get(1).text() shouldBe "12345671"
      trs.get(0).select("td").get(2).text() shouldBe "tax service 1"

      //last row
      trs.get(9).select("td").get(0).text() shouldBe "client name 10"
      trs.get(9).select("td").get(1).text() shouldBe "123456710"
      trs.get(9).select("td").get(2).text() shouldBe "tax service 10"

      html.select("a#change-selected-clients").attr("href") shouldBe routes.GroupController.showAddClients.url
      html.select("a#add-team-members").attr("href") shouldBe routes.GroupController.showAddTeamMembers.url
      html.select("a#add-team-members").hasClass("govuk-button")
    }
  }

  s"GET ${routes.GroupController.showAddTeamMembers.url}" should {

    "render with team members" in {
      val fakeTeamMembers = (1 to 10)
        .map(i => {
          TeamMember(
            s"John $i",
            s"john$i@abc.com",
          )
        })

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubGetTeamMembers(arn)(fakeTeamMembers)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

      val result = controller.showAddTeamMembers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select team members - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"

      val th = html.select(Css.tableWithId("client-list-table")).select("thead th")
      th.size() shouldBe 3
      th.get(1).text() shouldBe "Name"
      th.get(2).text() shouldBe "Email"
      val trs = html.select(Css.tableWithId("client-list-table")).select("tbody tr")
      trs.size() shouldBe 10
      //first row
      trs.get(0).select("td").get(1).text() shouldBe "John 1"
      trs.get(0).select("td").get(2).text() shouldBe "john1@abc.com"

      //last row
      trs.get(9).select("td").get(1).text() shouldBe "John 10"
      trs.get(9).select("td").get(2).text() shouldBe "john10@abc.com"
    }

    "render with No team members" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubGetTeamMembers(arn)(Seq.empty)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

      val result = controller.showAddTeamMembers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select team members - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"
      val th = html.select(Css.tableWithId("client-list-table")).select("thead th")
      th.size() shouldBe 3
      th.get(1).text() shouldBe "Name"
      th.get(2).text() shouldBe "Email"
      val trs = html.select(Css.tableWithId("client-list-table")).select("tbody tr")
      trs.size() shouldBe 0

    }

    "redirect when no group name is in session" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.showAddTeamMembers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showGroupName.url
    }
  }
}
