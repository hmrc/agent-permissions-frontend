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
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector, GroupRequest}
import helpers.Css.H1
import helpers.{BaseSpec, Css}
import models.{DisplayClient, TeamMember}
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, OK, SEE_OTHER}
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, redirectLocation}
import play.api.test.{FakeRequest, Helpers}
import repository.SessionCacheRepository
import services.{GroupService, GroupServiceImpl}
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Client, OptedInReady, UserDetails}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{SessionKeys, UpstreamErrorResponse}

class GroupControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector
    : AgentUserClientDetailsConnector =
    mock[AgentUserClientDetailsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]
  private val groupName = "XYZ"

  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

  override def moduleWithOverrides = new AbstractModule() {

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

  val controller: GroupController =
    fakeApplication.injector.instanceOf[GroupController]
//
//  val fakeClients = (1 to 10)
//    .map(i => {
//      Client(
//        s"HMRC-MTD-VAT~VRN~12345678$i",
//        s"friendly$i",
//      )
//    })

  val fakeClients: Seq[Client] =
    List.tabulate(3)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))

  val displayClients: Seq[DisplayClient] =
    fakeClients.map(DisplayClient.fromClient(_))

  val displayClientsIds: Seq[String] =
    displayClients.map(_.id)

  val users: Seq[UserDetails] = (1 to 10)
    .map { i =>
      UserDetails(
        Some(s"John $i"),
        Some("User"),
        Some("John"),
        Some(s"john$i@abc.com")
      )
    }

  val teamMembers: Seq[TeamMember] = users.map(TeamMember.fromUserDetails)

  val teamMembersIds: Seq[String] =
    teamMembers.map(_.id)

  def optedInWithGroupName(): Unit = {

    await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
    await(sessionCacheRepo.putSession(GROUP_NAME, "XYZ"))
    await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

  }

  s"GET /" should {

    s"redirect to ${routes.GroupController.showGroupName}" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      val result = controller.start()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe routes.GroupController.showGroupName.url
    }
  }

  s"GET ${routes.GroupController.showGroupName}" should {

    "have correct layout and content" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.showGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Create an access group - Agent services account - GOV.UK"
      html
        .select(Css.backLink)
        .attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
      html.select(Css.backLink).text() shouldBe "Back"
      html.select(Css.H1).text() shouldBe "Create an access group"
      html
        .select(Css.form)
        .attr("action") shouldBe "/agent-permissions/create-access-group-name"
      html
        .select(Css.labelFor("name"))
        .text() shouldBe "What do you want to call this access group?"
      html.select(Css.form + " input[name=name]").size() shouldBe 1
      html.select(Css.submitButton).text() shouldBe "Continue"
    }
  }

  s"POST ${routes.GroupController.showGroupName}" should {

    "redirect to confirmation page with when posting a valid group name" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      implicit val request =
        FakeRequest("POST", routes.GroupController.submitGroupName.url)
          .withFormUrlEncodedBody("name" -> "My Group Name")
          .withHeaders("Authorization" -> s"Bearer $groupName")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showConfirmGroupName.url
    }

    "render correct error messages when form not filled in" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      implicit val request =
        FakeRequest("POST", routes.GroupController.submitGroupName.url)
          .withFormUrlEncodedBody("name" -> "")
          .withHeaders("Authorization" -> s"Bearer $groupName")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Create an access group - Agent services account - GOV.UK"
      html
        .select(Css.errorSummaryForField("name"))
        .text() shouldBe "Enter an access group name"
      html
        .select(Css.errorForField("name"))
        .text() shouldBe "Error: Enter an access group name"

    }

    "render correct error messages when name exceeds 32 chars" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      implicit val request =
        FakeRequest("POST", routes.GroupController.submitGroupName.url)
          .withFormUrlEncodedBody("name" -> RandomStringUtils.random(33))
          .withHeaders("Authorization" -> s"Bearer $groupName")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Create an access group - Agent services account - GOV.UK"
      html
        .select(Css.errorSummaryForField("name"))
        .text() shouldBe "Access group name must be 38 characters or fewer"
      html
        .select(Css.errorForField("name"))
        .text() shouldBe "Error: Access group name must be 38 characters or fewer"
    }
  }

  "GET /group/confirm-name" should {

    "have correct layout and content" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))

      val result = controller.showConfirmGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Confirm group name for " + groupName + " - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Confirm group name for " + groupName
      html
        .select(Css.form)
        .attr("action") shouldBe "/agent-permissions/confirm-access-group-name"
      html
        .select(Css.legend)
        .text() shouldBe "Is the access group name correct?"
      html.select("label[for=answer-yes]").text() shouldBe "Yes"
      html.select("label[for=answer-no]").text() shouldBe "No"
      html.select(Css.form + " input[name=answer]").size() shouldBe 2
      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }

    "redirect to /group/group-name when there is no groupName in the session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.showConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showGroupName.url
    }
  }

  "POST /group/confirm-name" should {
    "render correct error messages when nothing is submitted" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      implicit val request =
        FakeRequest("POST", routes.GroupController.submitConfirmGroupName.url)
          .withFormUrlEncodedBody("answer" -> "")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Confirm group name for " + groupName + " - Agent services account - GOV.UK"
      html
        .select(Css.errorSummaryForField("answer"))
        .text() shouldBe "Select yes if the access group name is correct"
      html
        .select(Css.errorForField("answer"))
        .text() shouldBe "Error: Select yes if the access group name is correct"

    }

    "redirect to /group/group-name when there is no name in session" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      implicit val request =
        FakeRequest("POST", routes.GroupController.submitConfirmGroupName.url)
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showGroupName.url
    }

    s"redirect to ${routes.GroupController.showAccessGroupNameExists.url} when the access group name already exists" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      getGroupNameCheckReturns(ok = false)(arn, groupName)

      implicit val request =
        FakeRequest("POST", routes.GroupController.submitConfirmGroupName.url)
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe routes.GroupController.showAccessGroupNameExists.url
    }

    "redirect to add-clients page when confirm group name 'yes' selected" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      getGroupNameCheckReturns(ok = true)(arn, groupName)

      implicit val request =
        FakeRequest("POST", routes.GroupController.submitGroupName.url)
          .withFormUrlEncodedBody("name" -> groupName, "answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(
        routes.GroupController.showSelectClients.url)
    }

    "redirect to /group/group-name when confirm group name 'no' selected" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      implicit val request =
        FakeRequest("POST", routes.GroupController.submitConfirmGroupName.url)
          .withFormUrlEncodedBody("name" -> groupName, "answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(
        routes.GroupController.showGroupName.url)
    }
  }

  s"GET ${routes.GroupController.showAccessGroupNameExists.url}" should {

    "display content" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

      val result = controller.showAccessGroupNameExists()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Access group name already exists - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Access group name already exists"
      html
        .select(Css.paragraphs)
        .get(0)
        .text shouldBe s"You already have an access group called ’$groupName’. Please enter a new access group name."
      html
        .select(Css.linkStyledAsButton)
        .text shouldBe "Enter a new access group name"
      html
        .select(Css.linkStyledAsButton)
        .attr("href") shouldBe s"${routes.GroupController.showGroupName}"
    }
  }

  s"GET ${routes.GroupController.showSelectClients.url}" should {

    "render with es3 clients when a filter was not applied" in {

      val fakeClients = (1 to 10)
        .map { i =>
          Client(
            s"HMRC-MTD-VAT~VRN~12345678$i",
            s"friendly$i"
          )
        }

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubGetClientsOk(arn)(fakeClients)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

      val result = controller.showSelectClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Client reference"
      th.get(2).text() shouldBe "Tax reference"
      th.get(3).text() shouldBe "Tax service"
      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")

      trs.size() shouldBe 10
      // first row
      trs.get(0).select("td").get(1).text() shouldBe "friendly1"
      trs.get(0).select("td").get(2).text() shouldBe "ending in 6781"
      trs.get(0).select("td").get(3).text() shouldBe "VAT"

      // last row
      trs.get(9).select("td").get(1).text() shouldBe "friendly9"
      trs.get(9).select("td").get(2).text() shouldBe "ending in 6789"
      trs.get(9).select("td").get(3).text() shouldBe "VAT"
    }

    "render with clients held in session when a filter was applied" in {

      val fakeClients = (1 to 10)
        .map { i =>
          Client(
            s"HMRC-MTD-VAT~VRN~12345678$i",
            s"friendly$i"
          )
        }

      val displayClients = fakeClients.map(DisplayClient.fromClient(_))

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubGetClientsOk(arn)(fakeClients)

      await(sessionCacheRepo.putSession(FILTERED_CLIENTS, displayClients))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

      val result = controller.showSelectClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Client reference"
      th.get(2).text() shouldBe "Tax reference"
      th.get(3).text() shouldBe "Tax service"
      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")

      trs.size() shouldBe 10
      // first row
      trs.get(0).select("td").get(1).text() shouldBe "friendly1"
      trs.get(0).select("td").get(2).text() shouldBe "ending in 6781"
      trs.get(0).select("td").get(3).text() shouldBe "VAT"

      // last row
      trs.get(9).select("td").get(1).text() shouldBe "friendly10"
      trs.get(9).select("td").get(2).text() shouldBe "ending in 7810"
      trs.get(9).select("td").get(3).text() shouldBe "VAT"
    }

    "render with No CLIENTS" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubGetClientsOk(arn)(List.empty)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

      val result = controller.showSelectClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html
        .title() shouldBe s"Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe s"Select clients"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 0
      html.select(Css.H2).text() shouldBe "No clients found"
      html.select(Css.paragraphs).get(1).text() shouldBe "Update your filters and try again or clear your filters to see all your clients"
    }

    "redirect when no group name is in session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.showSelectClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showGroupName.url
    }
  }

  s"POST ${routes.GroupController.submitSelectedClients.url}" should {

    "save selected clients to session" when {

      s"button is Continue and redirect to ${routes.GroupController.showReviewSelectedClients.url}" in {
        // given
        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(true)
        stubGetClientsOk(arn)(fakeClients)

        implicit val request =
          FakeRequest("POST", routes.GroupController.submitSelectedClients.url)
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
        await(sessionCacheRepo.putSession(GROUP_NAME, "XYZ"))
        await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

        val result = controller.submitSelectedClients()(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe routes.GroupController.showReviewSelectedClients.url
        val storedClients =
          await(sessionCacheRepo.getFromSession(SELECTED_CLIENTS))
        storedClients.get.toList shouldBe List(
          displayClients.head.copy(selected = true),
          displayClients.last.copy(selected = true)
        )
      }

      s"button is Filter and redirect to ${routes.GroupController.showSelectClients.url}" in {

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(true)
        stubGetClientsOk(arn)(fakeClients)
        stubGetClientsOk(arn)(fakeClients)

        implicit val request =
          FakeRequest("POST", routes.GroupController.submitSelectedClients.url)
            .withFormUrlEncodedBody(
              "hasSelectedClients" -> "false",
              "clients[0]" -> displayClients.head.id,
              "clients[1]" -> displayClients.last.id,
              "search" -> "friendly0",
              "filter" -> "",
              "submitFilter" -> "submitFilter"
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
        await(sessionCacheRepo.putSession(GROUP_NAME, "XYZ"))
        await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

        val result = controller.submitSelectedClients()(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe routes.GroupController.showSelectClients.url
        val storedClients =
          await(sessionCacheRepo.getFromSession(SELECTED_CLIENTS))
        storedClients.get.toList shouldBe List(
          displayClients.head.copy(selected = true),
          displayClients.last.copy(selected = true)
        )
        val filteredClients =
          await(sessionCacheRepo.getFromSession(FILTERED_CLIENTS))
        filteredClients.get.toList shouldBe List(
          displayClients.head.copy(selected = true))
      }

      s"button is Clear and redirect to ${routes.GroupController.showSelectClients.url}" in {

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(true)
        stubGetClientsOk(arn)(fakeClients)

        implicit val request =
          FakeRequest("POST", routes.GroupController.submitSelectedClients.url)
            .withFormUrlEncodedBody(
              "hasSelectedClients" -> "false",
              "clients[0]" -> displayClients.head.id,
              "clients[1]" -> displayClients.last.id,
              "search" -> "",
              "filter" -> "",
              "submitClear" -> "submitClear"
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
        await(sessionCacheRepo.putSession(GROUP_NAME, "XYZ"))
        await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

        val result = controller.submitSelectedClients()(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe routes.GroupController.showSelectClients.url
        val storedClients =
          await(sessionCacheRepo.getFromSession(SELECTED_CLIENTS))
        storedClients.get.toList shouldBe List(
          displayClients.head.copy(selected = true),
          displayClients.last.copy(selected = true)
        )
        val filteredClients =
          await(sessionCacheRepo.getFromSession(FILTERED_CLIENTS))
        filteredClients shouldBe None
      }
    }

    "display error when button is Continue, no filtered clients, no hidden clients exist and no clients were selected" in {

      // given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubGetClientsOk(arn)(fakeClients)

      implicit val request = FakeRequest(
        "POST",
        routes.GroupController.submitSelectedClients.url
      ).withFormUrlEncodedBody(
          "hasSelectedClients" -> "false",
          "clients" -> "",
          "search" -> "",
          "filter" -> "",
          "continue" -> "continue"
        )
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, "XYZ"))

      // when
      val result = controller.submitSelectedClients()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"
      html
        .select(Css.errorSummaryForField("clients"))
      // .text() shouldBe "You must select at least one client"
      // and should have cleared the previously selected clients from the session
      await(sessionCacheRepo.getFromSession(SELECTED_CLIENTS)).isDefined shouldBe false

    }

    "display error when button is Filter and no filter term was provided" in {

      // given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubGetClientsOk(arn)(fakeClients)

      implicit val request = FakeRequest(
        "POST",
        routes.GroupController.submitSelectedClients.url
      ).withFormUrlEncodedBody(
          "hasSelectedClients" -> "false",
          "clients" -> "",
          "search" -> "",
          "filter" -> "",
          "submitFilter" -> "submitFilter"
        )
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, "XYZ"))

      // when
      val result = controller.submitSelectedClients()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"
      html
        .select(Css.ERROR_SUMMARY_LINK)
        .text() shouldBe "You must enter a tax reference, client reference or select a tax service to apply filters"
      html
        .select(Css.errorForField("filter"))
        .text() shouldBe "Error: You must enter a tax reference, client reference or select a tax service to apply filters"
      // and should have cleared the previously selected clients from the session
      await(sessionCacheRepo.getFromSession(SELECTED_CLIENTS)).isDefined shouldBe false

    }

    "redirect to createGroup when POSTED without groupName in Session" in {

      // given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      implicit val request = FakeRequest(
        "POST",
        routes.GroupController.submitSelectedClients.url
      ).withFormUrlEncodedBody(
          "hasSelectedClients" -> "false",
          "filter" -> "",
          "submitFilter" -> "submitFilter"
        )
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      // when
      val result = controller.submitSelectedClients()(request)

      // then
      status(result) shouldBe SEE_OTHER
      Helpers.redirectLocation(result) shouldBe Some(
        routes.GroupController.showGroupName.url)
    }

  }

  s"GET ${routes.GroupController.showReviewSelectedClients.url}" should {

    "render with selected clients" in {

      val selectedClients = (1 to 10)
        .map { i =>
          DisplayClient(
            s"1234567$i",
            s"client name $i",
            s"HMRC-MTD-VAT",
            s"id-key-$i",
            selected = true
          )
        }

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))
      await(
        sessionCacheRepo.putSession(SELECTED_CLIENTS, selectedClients))

      val result = controller.showReviewSelectedClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Review selected clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe s"You have selected 10 clients"
      html
        .select(Css.backLink)
        .attr("href") shouldBe routes.GroupController.showSelectClients.url

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 3
      th.get(0).text() shouldBe "Client reference"
      th.get(1).text() shouldBe "Tax reference"
      th.get(2).text() shouldBe "Tax service"
      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 10
      // first row
      trs.get(0).select("td").get(0).text() shouldBe "client name 1"
      trs.get(0).select("td").get(1).text() shouldBe "ending in 5671"
      trs.get(0).select("td").get(2).text() shouldBe "VAT"

      // last row
      trs.get(9).select("td").get(0).text() shouldBe "client name 10"
      trs.get(9).select("td").get(1).text() shouldBe "ending in 6710"
      trs.get(9).select("td").get(2).text() shouldBe "VAT"

      html
        .select("a#change-selected-clients")
        .attr("href").size shouldBe 0
      html
        .select("a#add-team-members")
        .attr("href").size shouldBe 0

      val form = html.select(Css.form)
      form.attr("action") shouldBe "/agent-permissions/clients-selected"
      val answerRadios = html.select(Css.radioButtonsField("answer"))
      answerRadios
        .select("label[for=true]")
        .text() shouldBe "Yes, add or remove team members"
      answerRadios
        .select("label[for=false]")
        .text() shouldBe "No, continue to next section"
    }

    "redirect when no group name is in session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.showReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showGroupName.url
    }

    "redirect to SELECT CLIENTS page when no selected clients are in the session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))

      val result = controller.showReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showSelectClients.url
    }
  }

  s"POST ${routes.GroupController.submitReviewSelectedClients}" should {

    s"redirect to '${routes.GroupController.showSelectTeamMembers}' page with answer 'false'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showSelectTeamMembers.url
    }

    s"redirect to '${routes.GroupController.showSelectClients}'" +
      s" page with answer 'true'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showSelectClients.url
    }

    s"render errors when no radio button selected" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Review selected clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 3 clients"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select an option"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select an option"

    }
  }

  s"GET ${routes.GroupController.showSelectTeamMembers.url}" should {

    val fakeTeamMembers = (1 to 10)
      .map { i =>
        UserDetails(
          Some(s"John $i"),
          Some("User"),
          Some("John"),
          Some(s"john$i@abc.com")
        )
      }

    val teamMembers = fakeTeamMembers.map(TeamMember.fromUserDetails)

    // TODO move all reused local vars to the top
    "render team members when filter is not applied" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubGetTeamMembersOk(arn)(fakeTeamMembers)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

      val result = controller.showSelectTeamMembers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"
      html
        .select(Css.backLink)
        .attr("href") shouldBe routes.GroupController.showReviewSelectedClients.url

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Name"
      th.get(2).text() shouldBe "Email"
      th.get(3).text() shouldBe "Role"
      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 10
      // first row
      trs.get(0).select("td").get(1).text() shouldBe "John"
      trs.get(0).select("td").get(2).text() shouldBe "john1@abc.com"
      trs.get(0).select("td").get(3).text() shouldBe "Administrator"

      // last row
      trs.get(9).select("td").get(1).text() shouldBe "John"
      trs.get(9).select("td").get(2).text() shouldBe "john10@abc.com"
      trs.get(9).select("td").get(3).text() shouldBe "Administrator"
      html.select(Css.submitButton).text() shouldBe "Continue"
    }

    "render with filtered team members held in session when a filter was applied" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubGetTeamMembersOk(arn)(users)

      await(sessionCacheRepo.putSession(FILTERED_TEAM_MEMBERS, teamMembers))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

      val result = controller.showSelectTeamMembers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Name"
      th.get(2).text() shouldBe "Email"
      th.get(3).text() shouldBe "Role"

      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")

      trs.size() shouldBe 10
      // first row
      trs.get(0).select("td").get(1).text() shouldBe "John"
      trs.get(0).select("td").get(2).text() shouldBe "john1@abc.com"
      trs.get(0).select("td").get(3).text() shouldBe "Administrator"
      // last row
      trs.get(4).select("td").get(1).text() shouldBe "John"
      trs.get(4).select("td").get(2).text() shouldBe "john5@abc.com"
      trs.get(4).select("td").get(3).text() shouldBe "Administrator"
    }

    "render with NO Team Members" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubGetTeamMembersOk(arn)(Seq.empty)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

      val result = controller.showSelectTeamMembers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"
      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Name"
      th.get(2).text() shouldBe "Email"
      th.get(3).text() shouldBe "Role"

      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 0

    }

    "redirect when no group name is in session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.showSelectTeamMembers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showGroupName.url
    }
  }

  s"POST to ${routes.GroupController.submitSelectedTeamMembers.url}" should {

    "save selected team members to session" when {

      s"button is Continue and redirect to ${routes.GroupController.showReviewSelectedTeamMembers.url}" in {

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(true)
        stubGetTeamMembersOk(arn)(users)

        implicit val request =
          FakeRequest("POST",
                      routes.GroupController.submitSelectedTeamMembers.url)
            .withFormUrlEncodedBody(
              "hasAlreadySelected" -> "false",
              "members[]" -> teamMembersIds.head,
              "members[]" -> teamMembersIds.last,
              "continue" -> "continue"
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
        await(sessionCacheRepo.putSession(GROUP_NAME, "XYZ"))
        await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

        val result = controller.submitSelectedTeamMembers()(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe routes.GroupController.showReviewSelectedTeamMembers.url
        val maybeTeamMembers =
          await(sessionCacheRepo.getFromSession(SELECTED_TEAM_MEMBERS))
        maybeTeamMembers.get shouldBe Seq(
          teamMembers.head.copy(selected = true),
          teamMembers.last.copy(selected = true)
        )
      }

      s"button is Filter and redirect to ${routes.GroupController.showSelectTeamMembers.url}" in {

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(true)
        stubGetTeamMembersOk(arn)(users)
        stubGetTeamMembersOk(arn)(users)

        implicit val request =
          FakeRequest("POST",
                      routes.GroupController.submitSelectedTeamMembers.url)
            .withFormUrlEncodedBody(
              "hasAlreadySelected" -> "false",
              "members[]" -> teamMembersIds.head,
              "members[]" -> teamMembersIds.last,
              "search" -> "10",
              "submitFilter" -> "submitFilter"
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
        await(sessionCacheRepo.putSession(GROUP_NAME, "XYZ"))
        await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

        val result = controller.submitSelectedTeamMembers()(request)

        status(await(result)) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe routes.GroupController.showSelectTeamMembers.url
        await(sessionCacheRepo.getFromSession(HIDDEN_TEAM_MEMBERS_EXIST))
        val storedTeamMembers =
          await(sessionCacheRepo.getFromSession(SELECTED_TEAM_MEMBERS))

        storedTeamMembers.get.toList shouldBe List(
          teamMembers.head.copy(selected = true),
          teamMembers.last.copy(selected = true)
        )
        val filteredTeamMembers =
          await(sessionCacheRepo.getFromSession(FILTERED_TEAM_MEMBERS))
        filteredTeamMembers.get shouldBe Seq(
          teamMembers.last.copy(selected = true))
      }

      s"button is Clear and redirect to ${routes.GroupController.showSelectTeamMembers.url}" in {

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(true)
        stubGetTeamMembersOk(arn)(users)

        implicit val request =
          FakeRequest("POST",
                      routes.GroupController.submitSelectedTeamMembers.url)
            .withFormUrlEncodedBody(
              "hasAlreadySelected" -> "false",
              "members[]" -> teamMembersIds.head,
              "members[]" -> teamMembersIds.last,
              "search" -> "1",
              "submitClear" -> "submitClear"
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
        await(sessionCacheRepo.putSession(GROUP_NAME, "XYZ"))
        await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

        val result = controller.submitSelectedTeamMembers()(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe routes.GroupController.showSelectTeamMembers.url
        val storedTeamMembers =
          await(sessionCacheRepo.getFromSession(SELECTED_TEAM_MEMBERS))
        storedTeamMembers.get.toList shouldBe List(
          teamMembers.head.copy(selected = true),
          teamMembers.last.copy(selected = true)
        )
        val filteredTeamMembers =
          await(sessionCacheRepo.getFromSession(FILTERED_TEAM_MEMBERS))
        filteredTeamMembers.isEmpty
      }
    }

    "display error when button is Continue, no team members were selected" in {

      // given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubGetTeamMembersOk(arn)(users)

      implicit val request = FakeRequest(
        "POST",
        routes.GroupController.submitSelectedTeamMembers.url
      ).withFormUrlEncodedBody(
          "hasAlreadySelected" -> "false",
          "members" -> "",
          "search" -> "",
          "continue" -> "continue"
        )
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, "XYZ"))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

      // when
      val result = controller.submitSelectedTeamMembers()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Select team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"
      html
        .select(Css.errorSummaryLinkWithHref("#members"))
        .text() shouldBe "You must select at least one team member"
      html
        .select(Css.errorForField("clients"))
        .text() shouldBe "Error: You must select at least one team member"

      // and should have cleared the previously selected clients from the session
      await(sessionCacheRepo.getFromSession(SELECTED_TEAM_MEMBERS)).isDefined shouldBe false

    }

    "display error when button is Filter and no filter term was provided" in {

      // given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubGetTeamMembersOk(arn)(users)

      implicit val request =
        FakeRequest("POST",
                    routes.GroupController.submitSelectedTeamMembers.url)
          .withFormUrlEncodedBody(
            "hasAlreadySelected" -> "false",
            "members[]" -> teamMembersIds.head,
            "members[]" -> teamMembersIds.last,
            "search" -> "",
            "submitFilter" -> "submitFilter"
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, "XYZ"))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))

      // when
      val result = controller.submitSelectedTeamMembers()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Select team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"
//      html
//        .select(Css.errorSummaryForField("search"))
//        .text() shouldBe "You must enter a name or email to apply filters"
      html
        .select(Css.errorForField("search"))
        .text() shouldBe "Error: You must enter a name or email to apply filters"

      // and should have cleared the previously selected clients from the session
      await(sessionCacheRepo.getFromSession(SELECTED_TEAM_MEMBERS)).isDefined shouldBe false

    }

    "redirect to createGroup when POSTED without groupName in Session" in {

      // given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      implicit val request = FakeRequest(
        "POST",
        routes.GroupController.submitSelectedTeamMembers.url
      ).withFormUrlEncodedBody("continue" -> "continue")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      // when
      val result = controller.submitSelectedTeamMembers()(request)

      // then
      status(result) shouldBe SEE_OTHER
      Helpers.redirectLocation(result) shouldBe Some(
        routes.GroupController.showGroupName.url)
    }
  }

  s"GET ${routes.GroupController.showReviewSelectedTeamMembers.url}" should {

    "render with selected team members" in {

      val selectedTeamMembers = (1 to 5)
        .map { i =>
          TeamMember(
            s"team member $i",
            s"x$i@xyz.com",
            Some(s"1234 $i"),
            Some("User"),
            selected = true
          )
        }

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(GROUP_NAME_CONFIRMED, true))
      await(
        sessionCacheRepo.putSession(SELECTED_TEAM_MEMBERS,
                                    selectedTeamMembers))

      val result = controller.showReviewSelectedTeamMembers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Review selected team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe s"You have selected 5 team members"
      html
        .select(Css.backLink)
        .attr("href") shouldBe routes.GroupController.showSelectTeamMembers.url

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 3
      th.get(0).text() shouldBe "Name"
      th.get(1).text() shouldBe "Email"
      th.get(2).text() shouldBe "Role"
      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 5
      // first row
      trs.get(0).select("td").get(0).text() shouldBe "team member 1"
      trs.get(0).select("td").get(1).text() shouldBe "x1@xyz.com"
      trs.get(0).select("td").get(2).text() shouldBe "Administrator"

      // last row
      trs.get(4).select("td").get(0).text() shouldBe "team member 5"
      trs.get(4).select("td").get(1).text() shouldBe "x5@xyz.com"
      trs.get(4).select("td").get(2).text() shouldBe "Administrator"

      val answerRadios = html.select(Css.radioButtonsField("answer"))
      answerRadios
        .select("label[for=true]")
        .text() shouldBe "Yes, add or remove team members"
      answerRadios
        .select("label[for=false]")
        .text() shouldBe "No, continue to next section"
    }

    "redirect when no group name is in session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.showReviewSelectedTeamMembers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showGroupName.url
    }
  }

  s"POST ${routes.GroupController.submitReviewSelectedTeamMembers}" should {

    s"redirect to '${routes.GroupController.showCheckYourAnswers}' page with answer 'false'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(SELECTED_TEAM_MEMBERS, teamMembers))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      val result = controller.submitReviewSelectedTeamMembers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showCheckYourAnswers.url
    }

    s"redirect to '${routes.GroupController.showSelectTeamMembers}'" +
      s" page with answer 'true'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(SELECTED_TEAM_MEMBERS, teamMembers))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      val result = controller.submitReviewSelectedTeamMembers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showSelectTeamMembers.url
    }

    s"render errors when no radio button selected" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(SELECTED_TEAM_MEMBERS, teamMembers))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      val result = controller.submitReviewSelectedTeamMembers()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Review selected team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 10 team members"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select an option"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select an option"

    }
  }

  s"GET ${routes.GroupController.showCheckYourAnswers.url}" should {

    "render correctly check you answers page" in {

      val selectedClients =
        (1 to 3).map(
          i =>
            DisplayClient(s"1234567$i",
                          s"client name $i",
                          s"tax service $i",
                          s"id-key-$i",
                          selected = true))
      val selectedTeamMembers =
        (1 to 5).map(
          i =>
            TeamMember(s"team member $i",
                       s"x$i@xyz.com",
                       Some(s"1234 $i"),
                       None,
                       selected = true))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(
        sessionCacheRepo.putSession(SELECTED_CLIENTS, selectedClients))
      await(
        sessionCacheRepo.putSession(SELECTED_TEAM_MEMBERS,
                                    selectedTeamMembers))

      val result = controller.showCheckYourAnswers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Check your selection - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Check your selection"
      html.select(Css.caption).text() shouldBe "XYZ access group"
      html
        .select(Css.H2)
        .text() shouldBe "Confirm clients and team members selected for this access group"
      html
        .select(Css.backLink)
        .attr("href") shouldBe routes.GroupController.showReviewSelectedTeamMembers.url

      val cyaRows = html.select(Css.checkYourAnswersListRows)
      cyaRows.size() shouldBe 2
      cyaRows.get(0).select("dt").text() shouldBe "Clients"
      cyaRows.get(0).select("dd").get(0).text() shouldBe "3"
      cyaRows.get(0).select("dd").get(1).text() shouldBe "Change"

      cyaRows.get(1).select("dt").text() shouldBe "Team members"
      cyaRows.get(1).select("dd").get(0).text() shouldBe "5"
      cyaRows.get(1).select("dd").get(1).text() shouldBe "Change"

      html
        .select(Css.insetText)
        .text() shouldBe "The team members you have selected will have permission to view and manage the tax affairs of all the clients in this access group"
      html
        .select(Css.form)
        .attr("action") shouldBe routes.GroupController.submitCheckYourAnswers.url
      html.select(Css.submitButton).text() shouldBe "Confirm access group"

    }

    "redirect when no group name is in session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.showCheckYourAnswers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showGroupName.url
    }
  }

  s"POST to ${routes.GroupController.showCheckYourAnswers.url}" should {

    "redirect to Group Name page if no group name in session" in {

      // given no GROUP_NAME in session
      implicit val request =
        FakeRequest("POST", routes.GroupController.submitCheckYourAnswers.url)
          .withFormUrlEncodedBody()
          .withSession(SessionKeys.sessionId -> "session-x")
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      // when
      val result = controller.submitCheckYourAnswers()(request)

      // then
      status(result) shouldBe SEE_OTHER
      Helpers
        .redirectLocation(result)
        .get shouldBe routes.GroupController.showGroupName.url
    }

    "make expected call to agent-permissions api to create group " in {
      // given
      val clients = (1 to 4).map(
        i =>
          DisplayClient(s"1234567$i",
                        s"client name $i",
                        s"tax service $i",
                        s"idKey$i"))
      val teamMembers = (1 to 2).map(i =>
        TeamMember(s"member $i", s"x$i@a.com", Some(s"$i"), None))
      implicit val request =
        FakeRequest("POST", routes.GroupController.submitCheckYourAnswers.url)
          .withFormUrlEncodedBody()
          .withSession(SessionKeys.sessionId -> "session-x")
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, clients))
      await(
        sessionCacheRepo.putSession(SELECTED_TEAM_MEMBERS, teamMembers))

      // and we expect the following call to the agentPermissionsConnector
      val expectedAgents =
        teamMembers.map(tm => AgentUser(tm.userId.get, tm.name))
      val expectedClients = clients.map(dc => Client(dc.enrolmentKey, dc.name))
      val expectedGroupRequest =
        GroupRequest(groupName, Some(expectedAgents), Some(expectedClients))
      expectCreateGroupSuccess(arn, expectedGroupRequest)

      // when
      val result = controller.submitCheckYourAnswers()(request)

      // then
      status(result) shouldBe SEE_OTHER
      Helpers
        .redirectLocation(result)
        .get shouldBe routes.GroupController.showGroupCreated.url
    }

    "render error page if Create fails" in {
      // given
      val clients = (1 to 4).map(
        i =>
          DisplayClient(s"1234567$i",
                        s"client name $i",
                        s"tax service $i",
                        s"idKey$i"))
      val teamMembers = (1 to 2).map(i =>
        TeamMember(s"member $i", s"x$i@a.com", Some(s"$i"), None))
      implicit val request =
        FakeRequest("POST", routes.GroupController.submitCheckYourAnswers.url)
          .withFormUrlEncodedBody()
          .withSession(SessionKeys.sessionId -> "session-x")
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, groupName))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, clients))
      await(
        sessionCacheRepo.putSession(SELECTED_TEAM_MEMBERS, teamMembers))

      // and we expect the following call to the agentPermissionsConnector
      expectCreateGroupFails(arn)

      // when
      val caught = intercept[UpstreamErrorResponse] {
        await(controller.submitCheckYourAnswers()(request))
      }
      caught.statusCode shouldBe BAD_REQUEST
    }
  }

  s"GET ${routes.GroupController.showGroupCreated.url}" should {

    "render correctly the confirmation page" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(NAME_OF_GROUP_CREATED, groupName))

      val result = controller.showGroupCreated()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Access group created - Agent services account - GOV.UK"
      html
        .select(Css.confirmationPanelH1)
        .text() shouldBe "Access group created"
      html
        .select(Css.confirmationPanelBody)
        .text() shouldBe "XYZ access group is now active"
      html.select(Css.H2).text() shouldBe "What happens next"
      html.select(Css.backLink).size() shouldBe 0

      html
        .select(Css.paragraphs)
        .get(0)
        .text shouldBe "The team members you selected can now view and manage the tax affairs of all the clients in this access group"

    }
  }

}
