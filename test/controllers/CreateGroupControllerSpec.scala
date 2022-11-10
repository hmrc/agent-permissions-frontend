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
import helpers.Css.{H1, H2, paragraphs}
import helpers.{BaseSpec, Css}
import models.{AddTeamMembersToGroup, DisplayClient, TeamMember}
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, redirectLocation}
import play.api.test.{FakeRequest, Helpers}
import services.{ClientService, GroupService, SessionCacheService, TeamMemberService}
import uk.gov.hmrc.agentmtdidentifiers.model.{Client, OptedInReady, UserDetails}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys
import repository.SessionCacheRepository

class CreateGroupControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]
  implicit val mockClientService: ClientService = mock[ClientService]
  implicit val mockTeamService: TeamMemberService = mock[TeamMemberService]
  implicit val mockSessionCacheService: SessionCacheService = mock[SessionCacheService]
  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)
  private val groupName = "XYZ"

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[ClientService]).toInstance(mockClientService)
      bind(classOf[TeamMemberService]).toInstance(mockTeamService)
      bind(classOf[GroupService]).toInstance(mockGroupService)
      bind(classOf[SessionCacheService]).toInstance(mockSessionCacheService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val controller: CreateGroupController =
    fakeApplication.injector.instanceOf[CreateGroupController]

  val fakeClients: Seq[Client] = List.tabulate(3)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))

  val displayClients: Seq[DisplayClient] = fakeClients.map(DisplayClient.fromClient(_))

  val displayClientsIds: Seq[String] = displayClients.map(_.id)

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

  private val ctrlRoute: ReverseCreateGroupController = routes.CreateGroupController

  s"GET /" should {

    s"redirect to ${ctrlRoute.showGroupName.url}" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectDeleteSessionItems(sessionKeys)

      val result = controller.start()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showGroupName.url
    }
  }

  s"GET ${ctrlRoute.showGroupName.url}" should {

    "have correct layout and content and existing session keys should be cleared" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, "My Shiny Group")
      expectDeleteSessionItems(sessionKeys)

      val result = controller.showGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Create an access group - Agent services account - GOV.UK"
      html.select(Css.backLink).attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
      html.select(Css.backLink).text() shouldBe "Back"
      html.select(Css.H1).text() shouldBe "Create an access group"
      html.select(Css.form).attr("action") shouldBe "/agent-permissions/create-access-group-name"
      html.select(Css.labelFor("name")).text() shouldBe "What do you want to call this access group?"
      html.select(Css.form + " input[name=name]").size() shouldBe 1
      html.select(Css.submitButton).text() shouldBe "Continue"

      html.select(".hmrc-report-technical-issue").text() shouldBe "Is this page not working properly? (opens in new tab)"
      html.select(".hmrc-report-technical-issue").attr("href") startsWith "http://localhost:9250/contact/report-technical-problem?newTab=true&service=AOSS"
    }

  }

  s"POST ${ctrlRoute.showGroupName.url}" should {

    "redirect to confirmation page with when posting a valid group name" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      val groupName = "My Group Name"
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitGroupName.url)
          .withFormUrlEncodedBody("name" -> groupName)
          .withHeaders("Authorization" -> s"Bearer $groupName")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectPutSessionItem(GROUP_NAME, groupName)
      expectPutSessionItem(GROUP_NAME_CONFIRMED, false)

      val result = controller.submitGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showConfirmGroupName.url
    }

    "render correct error messages when form not filled in" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitGroupName.url)
          .withFormUrlEncodedBody("name" -> "")
          .withHeaders("Authorization" -> s"Bearer $groupName")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)

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
      expectIsArnAllowed(allowed = true)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitGroupName.url)
          .withFormUrlEncodedBody("name" -> RandomStringUtils.randomAlphanumeric(33))
          .withHeaders("Authorization" -> s"Bearer $groupName")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)

      val result = controller.submitGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Create an access group - Agent services account - GOV.UK"
      html
        .select(Css.errorSummaryForField("name"))
        .text() shouldBe "Access group name must be 32 characters or fewer"
      html
        .select(Css.errorForField("name"))
        .text() shouldBe "Error: Access group name must be 32 characters or fewer"
    }
  }

  "GET /group/confirm-name" should {

    "have correct layout and content" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)

      val result = controller.showConfirmGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Confirm access group name - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Confirm access group name"
      html.select(Css.form).attr("action") shouldBe "/agent-permissions/confirm-access-group-name"
      html.select(Css.legend).text() shouldBe s"Is the access group name ‘$groupName’ correct?"
      html.select("label[for=answer]").text() shouldBe "Yes"
      html.select("label[for=answer-no]").text() shouldBe "No"
      html.select(Css.form + " input[name=answer]").size() shouldBe 2
      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }

    "redirect to /group/group-name when there is no groupName in the session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME) // <-- We are testing this. no group in session

      val result = controller.showConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showGroupName.url
    }
  }

  "POST /group/confirm-name" should {
    "render correct error messages when nothing is submitted" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      implicit val request =
        FakeRequest("POST", ctrlRoute.submitConfirmGroupName.url)
          .withFormUrlEncodedBody("answer" -> "")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Confirm access group name - Agent services account - GOV.UK"
      html
        .select(Css.errorSummaryForField("answer"))
        .text() shouldBe "Select yes if the access group name is correct"
      html
        .select(Css.errorForField("answer"))
        .text() shouldBe "Error: Select yes if the access group name is correct"

    }

    "redirect to /group/group-name when there is no name in session" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      implicit val request =
        FakeRequest("POST", ctrlRoute.submitConfirmGroupName.url)
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME) // <-- We are testing this. no group in session

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showGroupName.url
    }

    s"redirect to ${ctrlRoute.showAccessGroupNameExists.url} when the access group name already exists" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGroupNameCheck(ok = false)(arn, groupName)

      implicit val request =
        FakeRequest("POST", ctrlRoute.submitConfirmGroupName.url)
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showAccessGroupNameExists.url
    }

    "redirect to add-clients page when Confirm access group name 'yes' selected" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGroupNameCheck(ok = true)(arn, groupName)

      implicit val request =
        FakeRequest("POST", ctrlRoute.submitGroupName.url)
          .withFormUrlEncodedBody("name" -> groupName, "answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectPutSessionItem(GROUP_NAME_CONFIRMED, true)

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(
        ctrlRoute.showSelectClients.url)
    }

    "redirect to /group/group-name when Confirm access group name 'no' selected" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      implicit val request = FakeRequest("POST", ctrlRoute.submitConfirmGroupName.url)
        .withFormUrlEncodedBody("name" -> groupName, "answer" -> "false")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(
        ctrlRoute.showGroupName.url)
    }
  }

  s"GET ${ctrlRoute.showAccessGroupNameExists.url}" should {

    "display the right content" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)

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
        .attr("href") shouldBe s"${ctrlRoute.showGroupName.url}"
    }
  }

  s"GET selected clients on ${ctrlRoute.showSelectClients.url}" should {

    "render with clients from client service" in {

      val fakeClients = (1 to 10).map { i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i") }

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetFilteredClientsFromService(arn)(fakeClients.map(c => DisplayClient.fromClient(c)))
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetSessionItemNone(RETURN_URL)
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)
      expectGetSessionItemNone(CLIENT_SEARCH_INPUT)

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
      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")

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
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetFilteredClientsFromService(arn)(List.empty) // <- return empty list
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetSessionItemNone(RETURN_URL)
      expectGetSessionItem(CLIENT_SEARCH_INPUT, "blah")
      expectGetSessionItem(CLIENT_FILTER_INPUT, "HMRC-MTD-VAT")

      val result = controller.showSelectClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html
        .title() shouldBe s"Filter results for 'blah' and 'VAT' Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe s"Select clients"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 0
      html.select(Css.H2).text() shouldBe "No clients found"
      html.select(Css.paragraphs).get(1).text() shouldBe "Update your filters and try again or clear your filters to see all your clients"
    }

    "render correct back link when navigating from check your answers page" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetFilteredClientsFromService(arn)(List.empty) // <- return empty list
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetSessionItem(RETURN_URL, ctrlRoute.showCheckYourAnswers.url)
      expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)

      val result = controller.showSelectClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Select clients - Agent services account - GOV.UK"
      html.select(Css.backLink).attr("href") shouldBe ctrlRoute.showCheckYourAnswers.url

    }

    "redirect when no group name is in session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME) // <- NO GROUP NAME IN SESSION

      val result = controller.showSelectClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showGroupName.url
    }
  }

  s"POST ${ctrlRoute.submitSelectedClients.url}" should {

    "save selected clients to session" when {

      s"button is Continue and redirect to ${ctrlRoute.showReviewSelectedClients.url}" in {
        // given
        implicit val request =
          FakeRequest("POST", ctrlRoute.submitSelectedClients.url)
            .withFormUrlEncodedBody(
              "clients[0]" -> displayClients.head.id,
              "clients[1]" -> displayClients.last.id,
              "search" -> "",
              "filter" -> "",
              "submit" -> CONTINUE_BUTTON
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
        expectGetSessionItem(GROUP_NAME, "XYZ")
        expectGetSessionItem(SELECTED_CLIENTS, Seq.empty)
        expectSaveSelectedOrFilteredClients(arn)
        expectGetSessionItem(SELECTED_CLIENTS, displayClients)

        val result = controller.submitSelectedClients()(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe ctrlRoute.showReviewSelectedClients.url
      }

      s"button is Continue with selected in session and redirect to ${ctrlRoute.showReviewSelectedClients.url}" in {
        // given
        implicit val request =
          FakeRequest("POST", ctrlRoute.submitSelectedClients.url)
            .withFormUrlEncodedBody(
              "clients[]" -> "",
              "search" -> "",
              "filter" -> "",
              "submit" -> CONTINUE_BUTTON
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
        expectGetSessionItem(GROUP_NAME, "XYZ")
        expectGetSessionItem(SELECTED_CLIENTS, displayClients)
        expectSaveSelectedOrFilteredClients(arn)
        expectGetSessionItem(SELECTED_CLIENTS, displayClients) // nothing de-selected

        val result = controller.submitSelectedClients()(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe ctrlRoute.showReviewSelectedClients.url
      }

      s"button is Filter and redirect to ${ctrlRoute.showSelectClients.url}" in {


        implicit val request =
          FakeRequest("POST", ctrlRoute.submitSelectedClients.url)
            .withSession(SessionKeys.sessionId -> "session-x")
            .withFormUrlEncodedBody(
              "clients[0]" -> displayClients.head.id,
              "clients[1]" -> displayClients.last.id,
              "search" -> displayClients.head.name,
              "filter" -> "",
              "submit" -> FILTER_BUTTON
            )

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
        expectGetSessionItem(GROUP_NAME, "XYZ")
        expectGetSessionItem(SELECTED_CLIENTS, Seq.empty)
        expectSaveSelectedOrFilteredClients(arn)

        val result = controller.submitSelectedClients()(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe ctrlRoute.showSelectClients.url

      }

      s"button is Clear and redirect to ${ctrlRoute.showSelectClients.url}" in {

        implicit val request =
          FakeRequest("POST", ctrlRoute.submitSelectedClients.url)
            .withFormUrlEncodedBody(
              "clients[0]" -> displayClients.head.id,
              "clients[1]" -> displayClients.last.id,
              "search" -> "",
              "filter" -> "",
              "submit" -> CLEAR_BUTTON
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
        expectGetSessionItem(GROUP_NAME, "XYZ")
        expectGetSessionItem(SELECTED_CLIENTS, Seq.empty)
        expectSaveSelectedOrFilteredClients(arn)

        val result = controller.submitSelectedClients()(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe ctrlRoute.showSelectClients.url

      }
    }

    "display error when button is Continue, no selected clients in session and no clients were selected" in {

      // given
      implicit val request = FakeRequest(
        "POST",
        ctrlRoute.submitSelectedClients.url
      ).withFormUrlEncodedBody(
        "clients" -> "",
        "search" -> "",
        "filter" -> "",
        "submit" -> CONTINUE_BUTTON
      ).withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, "XYZ")
      expectGetSessionItem(SELECTED_CLIENTS, Seq.empty)
      expectGetFilteredClientsFromService(arn)(displayClients)

      // when
      val result = controller.submitSelectedClients()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"
      html.select(Css.errorSummaryForField("clients"))

    }

    "display error when button is Continue, selected clients in session but ALL clients deselected" in {
      // given
      implicit val request = FakeRequest(
        "POST",
        ctrlRoute.submitSelectedClients.url
      ).withFormUrlEncodedBody(
        "clients" -> "",
        "search" -> "",
        "filter" -> "",
        "submit" -> CONTINUE_BUTTON
      ).withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, "XYZ")
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectSaveSelectedOrFilteredClients(arn)
      expectGetSessionItem(SELECTED_CLIENTS, Seq.empty)
      expectGetAllClientsFromService(arn)(displayClients)
      expectGetSessionItemNone(RETURN_URL)

      // when
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients)) // hasPreSelected is true
      val result = controller.submitSelectedClients()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"
      html.select(Css.errorSummaryForField("clients"))

    }

    "NOT display error when button is Filter and no filter term was provided" in {

      // given
      implicit val request = FakeRequest(
        "POST",
        ctrlRoute.submitSelectedClients.url
      ).withFormUrlEncodedBody(
        "clients" -> "",
        "search" -> "",
        "filter" -> "",
        "submit" -> FILTER_BUTTON
      ).withSession(SessionKeys.sessionId -> "session-x")


      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, "XYZ")
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectSaveSelectedOrFilteredClients(arn)

      // when
      val result = controller.submitSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(ctrlRoute.showSelectClients.url)

    }

    "redirect to createGroup when POSTED without groupName in Session" in {

      // given
      implicit val request = FakeRequest(
        "POST",
        ctrlRoute.submitSelectedClients.url
      ).withFormUrlEncodedBody(
        "filter" -> "",
        "submit" -> FILTER_BUTTON
      ).withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItemNone(GROUP_NAME) // <-- We are testing this. no group in session
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)


      // when
      val result = controller.submitSelectedClients()(request)

      // then
      status(result) shouldBe SEE_OTHER
      Helpers.redirectLocation(result) shouldBe Some(ctrlRoute.showGroupName.url)
    }

  }

  s"GET ${ctrlRoute.showReviewSelectedClients.url}" should {

    "render with selected clients" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)

      val result = controller.showReviewSelectedClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Review selected clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe s"You have selected 3 clients"
      html.select(Css.backLink).attr("href") shouldBe ctrlRoute.showSelectClients.url

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 3
      th.get(0).text() shouldBe "Client reference"
      th.get(1).text() shouldBe "Tax reference"
      th.get(2).text() shouldBe "Tax service"
      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")

      trs.size() shouldBe 3
      // first row
      trs.get(0).select("td").get(0).text() shouldBe "friendly0"
      trs.get(0).select("td").get(1).text() shouldBe "ending in 6780"
      trs.get(0).select("td").get(2).text() shouldBe "VAT"

      // last row
      trs.get(2).select("td").get(0).text() shouldBe "friendly2"
      trs.get(2).select("td").get(1).text() shouldBe "ending in 6782"
      trs.get(2).select("td").get(2).text() shouldBe "VAT"

      html.select("a#change-selected-clients").attr("href").size shouldBe 0
      html.select("a#add-team-members").attr("href").size shouldBe 0

      val form = html.select(Css.form)
      form.attr("action") shouldBe "/agent-permissions/clients-selected"
      val answerRadios = html.select(Css.radioButtonsField("answer-radios"))
      answerRadios.select("label[for=answer]").text() shouldBe "Yes, add or remove clients"
      answerRadios.select("label[for=answer-no]").text() shouldBe "No, continue to next section"
    }

    "redirect when no group name is in session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME) // <-- We are testing this. no group in session

      val result = controller.showReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showGroupName.url
    }

    "redirect to SELECT CLIENTS page when no selected clients are in the session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetSessionItemNone(SELECTED_CLIENTS)

      val result = controller.showReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSelectClients.url
    }
  }

  s"POST ${ctrlRoute.submitReviewSelectedClients.url}" should {

    s"redirect to '${ctrlRoute.showSelectTeamMembers.url}' page with answer 'false'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers()}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(RETURN_URL)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSelectTeamMembers.url
    }

    s"redirect to RETURN URL when it's in the session page with answer 'false'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers()}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")
      val EXPECTED_RETURN_URL = ctrlRoute.showCheckYourAnswers.url

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(RETURN_URL, EXPECTED_RETURN_URL)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectDeleteSessionItem(RETURN_URL)

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe EXPECTED_RETURN_URL
    }


    s"redirect to '${ctrlRoute.showSelectClients.url}' page with answer 'true'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${ctrlRoute.submitReviewSelectedTeamMembers}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectDeleteSessionItems(clientFilteringKeys)

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSelectClients.url
    }

    s"render errors when no radio button selected" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${ctrlRoute.submitReviewSelectedTeamMembers}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Review selected clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 3 clients"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you need to add or remove selected clients"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you need to add or remove selected clients"

    }
  }

  s"GET ${ctrlRoute.showSelectTeamMembers.url}" should {

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

    "render team members when filter is not applied" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetSessionItemNone(TEAM_MEMBER_SEARCH_INPUT)
      expectGetSessionItemNone(RETURN_URL)
      expectGetFilteredTeamMembersElseAll(arn)(teamMembers)

      val result = controller.showSelectTeamMembers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"
      html
        .select(Css.backLink)
        .attr("href") shouldBe ctrlRoute.showReviewSelectedClients.url

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
      expectIsArnAllowed(allowed = true)

      expectGetSessionItem(TEAM_MEMBER_SEARCH_INPUT, "John")
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetSessionItemNone(RETURN_URL)
      expectGetFilteredTeamMembersElseAll(arn)(teamMembers)

      val result = controller.showSelectTeamMembers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Filter results for 'John' Select team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"

      html.select(H2).text() shouldBe "Filter results for 'John'"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Name"
      th.get(2).text() shouldBe "Email"
      th.get(3).text() shouldBe "Role"

      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")

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
      expectIsArnAllowed(allowed = true)
      expectGetSessionItemNone(TEAM_MEMBER_SEARCH_INPUT)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetSessionItemNone(RETURN_URL)
      expectGetFilteredTeamMembersElseAll(arn)(Seq.empty) // <- no team members returned from session

      val result = controller.showSelectTeamMembers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"

      // No table
      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 0
      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 0

      // Not found content
      html.select(Css.H2).text() shouldBe "No team members found"
      html.select(paragraphs).get(1).text() shouldBe "Update your filters and try again or clear your filters to see all your team members"

    }

    "render correct back link when coming from check you answers page" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItemNone(TEAM_MEMBER_SEARCH_INPUT)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetFilteredTeamMembersElseAll(arn)(teamMembers)
      // <-- we expect RETURN_URL to be the backLink url
      expectGetSessionItem(RETURN_URL, ctrlRoute.showCheckYourAnswers.url)

      val result = controller.showSelectTeamMembers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select team members - Agent services account - GOV.UK"
      html.select(Css.backLink).attr("href") shouldBe ctrlRoute.showCheckYourAnswers.url

    }

    "redirect when no group name is in session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME) // <- NO GROUP NAME IN SESSION

      val result = controller.showSelectTeamMembers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showGroupName.url
    }
  }

  s"POST to ${ctrlRoute.submitSelectedTeamMembers.url}" should {

    "save selected team members to session" when {

      s"button is Continue and redirect to ${ctrlRoute.showReviewSelectedTeamMembers.url}" in {

        implicit val request =
          FakeRequest("POST",
            ctrlRoute.submitSelectedTeamMembers.url)
            .withSession(SessionKeys.sessionId -> "session-x")
            .withFormUrlEncodedBody(
              "members[]" -> teamMembersIds.head,
              "members[]" -> teamMembersIds.last,
              "submit" -> CONTINUE_BUTTON
            )
        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
        expectGetSessionItem(GROUP_NAME, "XYZ")
        expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty) // with no preselected
        val formData = AddTeamMembersToGroup(
          members = Some(List(teamMembersIds.head, teamMembersIds.last)),
          submit = CONTINUE_BUTTON
        )
        expectSaveSelectedOrFilteredTeamMembers(arn)(CONTINUE_BUTTON, formData)
        expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers) // check after save selected

        val result = controller.submitSelectedTeamMembers()(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe ctrlRoute.showReviewSelectedTeamMembers.url
      }

      s"button is Filter and redirect to ${ctrlRoute.showSelectTeamMembers.url}" in {

        implicit val request =
          FakeRequest("POST",
            ctrlRoute.submitSelectedTeamMembers.url)
            .withSession(SessionKeys.sessionId -> "session-x")
            .withFormUrlEncodedBody(
              "members[]" -> teamMembersIds.head,
              "members[]" -> teamMembersIds.last,
              "search" -> "10",
              "submit" -> FILTER_BUTTON
            )

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
        expectGetSessionItem(GROUP_NAME, "XYZ")
        expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty)
        val form = AddTeamMembersToGroup(
          search = Some("10"),
          members = Some(List(teamMembersIds.head, teamMembersIds.last)),
          submit = FILTER_BUTTON
        )
        expectSaveSelectedOrFilteredTeamMembers(arn)(FILTER_BUTTON, form)


        val result = controller.submitSelectedTeamMembers()(request)

        status(await(result)) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe ctrlRoute.showSelectTeamMembers.url

      }


    }

    "display error when button is Continue, no team members were selected" in {

      // given
      implicit val request = FakeRequest("POST", ctrlRoute.submitSelectedTeamMembers.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody(
        "members" -> "",
        "search" -> "",
        "submit" -> CONTINUE_BUTTON
      )

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, "XYZ")
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty)
      expectGetFilteredTeamMembersElseAll(arn)(teamMembers)

      // when
      val result = controller.submitSelectedTeamMembers()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Select team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"
      html.select(Css.errorSummaryLinkWithHref("#members")).text() shouldBe "You must select at least one team member"
      html.select(Css.errorForField("clients")).text() shouldBe "Error: You must select at least one team member"

    }

    "not show any errors when button is Filter and no filter term was provided" in {

      // given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest("POST", ctrlRoute.submitSelectedTeamMembers.url)
          .withSession(SessionKeys.sessionId -> "session-x")
          .withFormUrlEncodedBody(
            "members" -> "",
            "search" -> "",
            "submit" -> FILTER_BUTTON
          )

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, "XYZ")
      val form = AddTeamMembersToGroup(submit = FILTER_BUTTON)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      expectSaveSelectedOrFilteredTeamMembers(arn)(FILTER_BUTTON, form)

      // when
      val result = controller.submitSelectedTeamMembers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(ctrlRoute.showSelectTeamMembers.url)
    }

    "redirect to createGroup when POSTED without groupName in Session" in {

      // given
      implicit val request = FakeRequest(
        "POST",
        ctrlRoute.submitSelectedTeamMembers.url
      ).withFormUrlEncodedBody("submit" -> CONTINUE_BUTTON)
        .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME)

      // when
      val result = controller.submitSelectedTeamMembers()(request)

      // then
      status(result) shouldBe SEE_OTHER
      Helpers.redirectLocation(result) shouldBe Some(ctrlRoute.showGroupName.url)
    }
  }

  s"GET ${ctrlRoute.showReviewSelectedTeamMembers.url}" should {

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
      expectIsArnAllowed(allowed = true)

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)

      expectGetSessionItem(SELECTED_TEAM_MEMBERS, selectedTeamMembers)

      val result = controller.showReviewSelectedTeamMembers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Review selected team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe s"You have selected 5 team members"
      html.select(Css.backLink).attr("href") shouldBe ctrlRoute.showSelectTeamMembers.url

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 3
      th.get(0).text() shouldBe "Name"
      th.get(1).text() shouldBe "Email"
      th.get(2).text() shouldBe "Role"
      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 5
      // first row
      trs.get(0).select("td").get(0).text() shouldBe "team member 1"
      trs.get(0).select("td").get(1).text() shouldBe "x1@xyz.com"
      trs.get(0).select("td").get(2).text() shouldBe "Administrator"

      // last row
      trs.get(4).select("td").get(0).text() shouldBe "team member 5"
      trs.get(4).select("td").get(1).text() shouldBe "x5@xyz.com"
      trs.get(4).select("td").get(2).text() shouldBe "Administrator"

      val answerRadios = html.select(Css.radioButtonsField("answer-radios"))
      answerRadios.select("label[for=answer]").text() shouldBe "Yes, add or remove team members"
      answerRadios.select("label[for=answer-no]").text() shouldBe "No, continue to next section"
    }

    "redirect when no group name is in session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME) // <- NO GROUP NAME IN SESSION

      val result = controller.showReviewSelectedTeamMembers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showGroupName.url
    }
  }

  s"POST ${ctrlRoute.submitReviewSelectedTeamMembers.url}" should {

    s"redirect to '${ctrlRoute.showCheckYourAnswers.url}' page with answer 'false'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      val result = controller.submitReviewSelectedTeamMembers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showCheckYourAnswers.url
    }

    s"redirect to '${ctrlRoute.showSelectTeamMembers.url}'" +
      s" page with answer 'true'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      val result = controller.submitReviewSelectedTeamMembers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSelectTeamMembers.url
    }

    s"render errors when no radio button selected" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers()}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      val result = controller.submitReviewSelectedTeamMembers()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Review selected team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 10 team members"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you need to add or remove selected team members"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you need to add or remove selected team members"

    }
  }

  s"GET ${ctrlRoute.showCheckYourAnswers.url}" should {

    "render correctly check you answers page" in {

      val selectedTeamMembers =
        (1 to 5).map(
          i =>
            TeamMember(s"team member $i",
              s"x$i@xyz.com",
              Some(s"1234 $i"),
              None,
              selected = true))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, selectedTeamMembers)

      val result = controller.showCheckYourAnswers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Check your selection - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Check your selection"
      html.select(Css.caption).text() shouldBe "XYZ access group"
      html.select(Css.paragraphs).text() shouldBe "Confirm clients and team members selected for this access group"
      html.select(Css.backLink).attr("href") shouldBe ctrlRoute.showReviewSelectedTeamMembers.url

      val cyaRows = html.select(Css.checkYourAnswersListRows)
      cyaRows.size() shouldBe 2
      cyaRows.get(0).select("dt").text() shouldBe "Clients"
      cyaRows.get(0).select("dd").get(0).text() shouldBe "3"
      cyaRows.get(0).select("dd").get(1).text() shouldBe "Change Clients"

      cyaRows.get(1).select("dt").text() shouldBe "Team members"
      cyaRows.get(1).select("dd").get(0).text() shouldBe "5"
      cyaRows.get(1).select("dd").get(1).text() shouldBe "Change Team members"

      html.select(Css.insetText).text() shouldBe "The team members you have selected will have permission to view and manage the tax affairs of all the clients in this access group"
      html.select(Css.form).attr("action") shouldBe ctrlRoute.submitCheckYourAnswers.url
      html.select(Css.submitButton).text() shouldBe "Confirm access group"

    }

    "redirect when no group name is in session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME) // <- NO GROUP NAME IN SESSION

      val result = controller.showCheckYourAnswers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showGroupName.url
    }
  }

  s"POST to ${ctrlRoute.submitCheckYourAnswers.url}" should {

    "redirect to Group Name page if no group name in session" in {

      // given no GROUP_NAME in session
      implicit val request = FakeRequest("POST", ctrlRoute.submitCheckYourAnswers.url)
          .withFormUrlEncodedBody()
          .withSession(SessionKeys.sessionId -> "session-x")
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME)

      // when
      val result = controller.submitCheckYourAnswers()(request)

      // then
      status(result) shouldBe SEE_OTHER
      Helpers
        .redirectLocation(result)
        .get shouldBe ctrlRoute.showGroupName.url
    }

    "make expected call to agent-permissions api to create group " in {
      // given
      implicit val request = FakeRequest("POST", ctrlRoute.submitCheckYourAnswers.url)
        .withFormUrlEncodedBody()
        .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, "whatever")
      expectCreateGroup(arn)("whatever")

      // when
      val result = controller.submitCheckYourAnswers()(request)

      // then
      status(result) shouldBe SEE_OTHER
      Helpers
        .redirectLocation(result)
        .get shouldBe ctrlRoute.showGroupCreated.url
    }
  }

  s"GET ${ctrlRoute.redirectToEditClients.url}" should {

    "redirect to select-clients" in {

      expectPutSessionItem(RETURN_URL, ctrlRoute.showCheckYourAnswers.url)

      val result = controller.redirectToEditClients()(request)

      status(result) shouldBe SEE_OTHER

    }
  }

  s"GET ${ctrlRoute.showGroupCreated.url}" should {

    "render correctly the confirmation page" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(NAME_OF_GROUP_CREATED, groupName)

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
