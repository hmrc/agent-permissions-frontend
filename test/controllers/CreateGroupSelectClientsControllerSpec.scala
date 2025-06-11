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
import connectors.{AgentAssuranceConnector, AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.actions.AuthAction
import helpers.Css.H1
import helpers.{BaseSpec, Css}
import models.{AddClientsToGroup, DisplayClient}
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, redirectLocation}
import play.api.test.{FakeRequest, Helpers}
import repository.SessionCacheRepository
import services.{ClientService, GroupService, SessionCacheOperationsService, SessionCacheService}
import uk.gov.hmrc.agents.accessgroups.Client
import uk.gov.hmrc.agents.accessgroups.optin.OptedInReady
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

class CreateGroupSelectClientsControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector =
    mock[AgentUserClientDetailsConnector]
  implicit lazy val mockAgentClientAuthConnector: AgentAssuranceConnector =
    mock[AgentAssuranceConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]
  implicit val mockClientService: ClientService = mock[ClientService]
  implicit val mockSessionCacheService: SessionCacheService = mock[SessionCacheService]
  implicit val mockSessionCacheOps: SessionCacheOperationsService =
    mock[
      SessionCacheOperationsService
    ] // TODO move to a ‘real’ (in-memory store) session cache service and you won't have to mock either SessionCacheService or SessionCacheServiceOperations!
  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)
  private val groupName = "XYZ"

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(
        new AuthAction(
          mockAuthConnector,
          env,
          conf,
          mockAgentPermissionsConnector,
          mockAgentClientAuthConnector,
          mockSessionCacheService
        )
      )
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[ClientService]).toInstance(mockClientService)
      bind(classOf[GroupService]).toInstance(mockGroupService)
      bind(classOf[SessionCacheService]).toInstance(mockSessionCacheService)
      bind(classOf[SessionCacheOperationsService]).toInstance(mockSessionCacheOps)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder.configure("mongodb.uri" -> mongoUri).build()

  val controller: CreateGroupSelectClientsController =
    fakeApplication.injector.instanceOf[CreateGroupSelectClientsController]

  val fakeClients: Seq[Client] = List.tabulate(25)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))

  val displayClients: Seq[DisplayClient] = fakeClients.map(DisplayClient.fromClient(_))

  val displayClientsIds: Seq[String] = displayClients.map(_.id)

  private val ctrlRoute: ReverseCreateGroupSelectClientsController = routes.CreateGroupSelectClientsController

  def expectAuthOkArnAllowedOptedInReadyWithGroupName(): Unit = {
    expectAuthorisationGrantsAccess(mockedAuthResponse)
    expectIsArnAllowed(allowed = true)
    expectGetSessionItem(SUSPENSION_STATUS, false)
    expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
    expectGetSessionItem(GROUP_TYPE, CUSTOM_GROUP)
    expectGetSessionItem(GROUP_NAME, groupName)
  }

  s"GET ${ctrlRoute.showSearchClients().url}" should {
    "render the client search page" in {
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)

      val result = controller.showSearchClients()(request)
      // then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Search for clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Search for clients"
      html.select(Css.backLink).attr("href") shouldBe "#"
      html.select(Css.backLink).text() shouldBe "Back"

      html.select(Css.labelFor("search")).text() shouldBe "Search by tax reference or client reference (optional)"

      html.select(Css.labelFor("filter")).text() shouldBe "Search by tax service (optional)"

    }

    "render the client search page with inputs saved in session" in {
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(CLIENT_SEARCH_INPUT, "Harry")
      expectGetSessionItem(CLIENT_FILTER_INPUT, "HMRC-MTD-VAT")

      val result = controller.showSearchClients()(request)
      // then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Search for clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Search for clients"
      html.select(Css.backLink).attr("href") shouldBe "#"
      html.select(Css.backLink).text() shouldBe "Back"

      html.select(Css.labelFor("search")).text() shouldBe "Search by tax reference or client reference (optional)"
      html.select("#search").attr("value") shouldBe "Harry"
      html.select(Css.labelFor("filter")).text() shouldBe "Search by tax service (optional)"
      // TODO this isn't working
      // html.select("#filter").attr("value") shouldBe "HMRC-MTD-VAT"

    }

  }

  s"POST ${ctrlRoute.submitSearchClients().url}" should {
    // TODO - using fully optional form atm, clarify expected error behaviour
    //    "render errors on client search page" in {
    //      expectAuthOkArnAllowedOptedInReadyWithGroupName()
    //      expectSaveSearch()
    //      implicit val request =
    //        FakeRequest(
    //          "POST",
    //          s"${controller.submitSearchClients()}")
    //          .withSession(SessionKeys.sessionId -> "session-x")
    //
    //      val result = controller.submitSearchClients()(request)
    //      status(result) shouldBe OK
    //    }

    "save search terms and redirect" in {
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectSaveSearch(Some("Harry"), Some("HMRC-MTD-VAT"))

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitSearchClients()}")
          .withFormUrlEncodedBody("search" -> "Harry", "filter" -> "HMRC-MTD-VAT")
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitSearchClients()(request)
      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showSelectClients(Some(1), Some(20)).url
    }

  }

  s"GET ${ctrlRoute.showSelectClients(None, None).url}" should {

    "render a page of clients" in {
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)
      expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
      expectGetPageOfClients(arn)(displayClients.take(20))

      val result = controller.showSelectClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"

      html.select(Css.backLink).text() shouldBe "Back"
      html.select(Css.backLink).attr("href") shouldBe "#"

      val th = html.select(Css.tableWithId("multi-select-table")).select("thead th")
      th.size() shouldBe 4
      th.get(0).text() shouldBe "Select client"
      th.get(1).text() shouldBe "Client reference"
      th.get(2).text() shouldBe "Tax reference"
      th.get(3).text() shouldBe "Tax service"
      val trs =
        html.select(Css.tableWithId("multi-select-table")).select("tbody tr")

      trs.size() shouldBe 20

      // first row
      trs
        .get(0)
        .select("td")
        .get(0)
        .select("label")
        .text() shouldBe "Client reference friendly0, Tax reference ending in 6780, Tax service VAT"
      trs.get(0).select("td").get(1).text() shouldBe "friendly0"
      trs.get(0).select("td").get(2).text() shouldBe "ending in 6780"
      trs.get(0).select("td").get(3).text() shouldBe "VAT"

      // last row
      trs.get(19).select("td").get(1).text() shouldBe "friendly19"
      trs.get(19).select("td").get(2).text() shouldBe "ending in 7819"
      trs.get(19).select("td").get(3).text() shouldBe "VAT"
      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }

    "render with filtered clients held in session when a filter was applied" in {
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)
      expectGetSessionItem(CLIENT_SEARCH_INPUT, "John")
      expectGetPageOfClients(arn)(displayClients)

      val result = controller.showSelectClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Filter results for ‘John’ Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"

      // TODO check if we need to add a h2 with filter/search terms
      // html.select(H2).text() shouldBe "Filter results for ‘John'"

      val th = html.select(Css.tableWithId("multi-select-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Client reference"
      th.get(2).text() shouldBe "Tax reference"
      th.get(3).text() shouldBe "Tax service"

      val trs = html.select(Css.tableWithId("multi-select-table")).select("tbody tr")

      trs.size() shouldBe 25
      // first row
      trs.get(0).select("td").get(1).text() shouldBe "friendly0"
      trs.get(0).select("td").get(2).text() shouldBe "ending in 6780"
      trs.get(0).select("td").get(3).text() shouldBe "VAT"
      // last row
      trs.get(19).select("td").get(1).text() shouldBe "friendly19"
      trs.get(19).select("td").get(2).text() shouldBe "ending in 7819"
      trs.get(19).select("td").get(3).text() shouldBe "VAT"
    }

    "render with NO Clients after a search returns no results" in {
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)
      expectGetSessionItem(CLIENT_SEARCH_INPUT, "foo")
      expectGetSessionItemNone(SELECTED_CLIENTS) // There are no selected clients
      expectGetPageOfClients(arn)(Seq.empty) // <- nothing returned from session

      val result = controller.showSelectClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "No results for ‘foo’ - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "No results for ‘foo’"
      html.select(Css.legend).text() shouldBe "Search again"

      // No table
      val th = html.select(Css.tableWithId("multi-select-table")).select("thead th")
      th.size() shouldBe 0
      val trs =
        html.select(Css.tableWithId("multi-select-table")).select("tbody tr")
      trs.size() shouldBe 0

      val buttons = html.select("button")
      buttons.size() shouldBe 1
      html.select("button").get(0).text() shouldBe "Search for clients"
      // there should not be a 'continue' button as there are NO clients currently selected (APB-7378)
    }

    "render with NO Clients after a search returns no results (but some previously selected clients)" in {
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)
      expectGetSessionItem(CLIENT_SEARCH_INPUT, "foo")
      expectGetSessionItem(SELECTED_CLIENTS, displayClients.take(1)) // There are some selected clients
      expectGetPageOfClients(arn)(Seq.empty) // <- nothing returned from session

      val result = controller.showSelectClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      val buttons = html.select("button")
      buttons.size() shouldBe 2
      html.select("button").get(0).text() shouldBe "Search for clients"
      html.select("button").get(1).text() shouldBe "Continue"
      // there SHOULD be a 'continue' button as there are SOME clients currently selected (APB-7378)
    }

    "redirect when no group name is in session" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(SUSPENSION_STATUS, false)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_TYPE, CUSTOM_GROUP)
      expectGetSessionItemNone(GROUP_NAME) // <- NO GROUP NAME IN SESSION

      val result = controller.showSelectClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.CreateGroupSelectNameController.showGroupName().url
    }
  }

  s"POST to ${ctrlRoute.submitSelectedClients().url}" should {

    "save selected clients to session" when {

      s"button is Continue and redirect to ${ctrlRoute.showReviewSelectedClients(None, None).url}" in {

        implicit val request = FakeRequest("POST", ctrlRoute.submitSelectedClients().url)
          .withSession(SessionKeys.sessionId -> "session-x")
          .withFormUrlEncodedBody(
            "clients[]" -> displayClientsIds.head,
            "clients[]" -> displayClientsIds.last,
            "submit"    -> CONTINUE_BUTTON
          )
        expectAuthOkArnAllowedOptedInReadyWithGroupName()
        expectGetSessionItem(SELECTED_CLIENTS, Seq.empty) // with no preselected
        val formData = AddClientsToGroup(
          clients = Some(List(displayClientsIds.head, displayClientsIds.last)),
          submit = CONTINUE_BUTTON
        )
        expectSavePageOfClients(formData, displayClients)

        val result = controller.submitSelectedClients()(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe ctrlRoute.showReviewSelectedClients(None, None).url
      }

      s"button is pagination_2 and redirect to ${ctrlRoute.showSelectClients(Some(2), Some(20)).url}" in {

        implicit val request =
          FakeRequest("POST", ctrlRoute.submitSelectedClients().url)
            .withSession(SessionKeys.sessionId -> "session-x")
            .withFormUrlEncodedBody(
              "clients[]" -> displayClientsIds.head,
              "clients[]" -> displayClientsIds.last,
              "submit"    -> PAGINATION_BUTTON.concat("_2")
            )

        expectAuthOkArnAllowedOptedInReadyWithGroupName()
        expectGetSessionItem(SELECTED_CLIENTS, Seq.empty) // with no preselected

        val formData = AddClientsToGroup(
          clients = Some(List(displayClientsIds.head, displayClientsIds.last)),
          submit = PAGINATION_BUTTON.concat("_2")
        )

        expectSavePageOfClients(formData, displayClients)

        val result = controller.submitSelectedClients()(request)

        status(await(result)) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe ctrlRoute.showSelectClients(Some(2), Some(20)).url

      }

      s"bad submit (not continue or page number) redirect to ${ctrlRoute.showSearchClients().url}" in {

        implicit val request =
          FakeRequest("POST", ctrlRoute.submitSelectedClients().url)
            .withSession(SessionKeys.sessionId -> "session-x")
            .withFormUrlEncodedBody(
              "clients[]" -> displayClientsIds.head,
              "clients[]" -> displayClientsIds.last,
              "submit"    -> "tamperedWithOrMissing"
            )

        expectAuthOkArnAllowedOptedInReadyWithGroupName()
        expectGetSessionItem(SELECTED_CLIENTS, Seq.empty) // with no preselected

        val formData = AddClientsToGroup(
          clients = Some(List(displayClientsIds.head, displayClientsIds.last)),
          submit = "tamperedWithOrMissing"
        )

        expectSavePageOfClients(formData, displayClients)

        val result = controller.submitSelectedClients()(request)

        status(await(result)) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe ctrlRoute.showSearchClients().url
      }

    }

    "display error when button is Continue, no clients were selected" in {

      // given
      implicit val request = FakeRequest("POST", ctrlRoute.submitSelectedClients().url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody(
          "clients[]" -> "",
          "submit"    -> CONTINUE_BUTTON
        )

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(SELECTED_CLIENTS, Seq.empty) // with no preselected
      expectGetPageOfClients(arn)(displayClients)

      // when
      val result = controller.submitSelectedClients()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"
      html.select(Css.errorSummaryLinkWithHref("#clients")).text() shouldBe "You must select at least one client"

    }

    "display error when button is Continue and DESELECTION mean that nothing is now selected" in {
      // given
      implicit val request = FakeRequest("POST", ctrlRoute.submitSelectedClients().url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody(
          "clients[]" -> "",
          "submit"    -> CONTINUE_BUTTON
        )

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      // this currently selected member will be unselected as part of the post
      expectGetSessionItem(SELECTED_CLIENTS, displayClients.take(1))
      val emptyForm = AddClientsToGroup(submit = CONTINUE_BUTTON)
      // now no selected members
      expectSavePageOfClients(emptyForm, Seq.empty[DisplayClient])
      expectGetPageOfClients(arn)(displayClients)

      // when
      val result = controller.submitSelectedClients()(request)

      // then
      status(result) shouldBe OK

      // and
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"
      html.select(Css.errorSummaryLinkWithHref("#clients")).text() shouldBe "You must select at least one client"

    }

    "redirect to createGroup when POSTED without groupName in Session" in {

      // given
      implicit val request = FakeRequest(
        "POST",
        ctrlRoute.submitSelectedClients().url
      ).withFormUrlEncodedBody("submit" -> CONTINUE_BUTTON)
        .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(SUSPENSION_STATUS, false)
      expectGetSessionItem(GROUP_TYPE, CUSTOM_GROUP)
      expectGetSessionItemNone(GROUP_NAME) // <- testing this

      // when
      val result = controller.submitSelectedClients()(request)

      // then
      status(result) shouldBe SEE_OTHER
      Helpers.redirectLocation(result) shouldBe Some(routes.CreateGroupSelectNameController.showGroupName().url)
    }
  }

  s"GET ReviewSelectedClients on ${ctrlRoute.showReviewSelectedClients(None, None).url}" should {

    "render with selected clients" in {
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(SELECTED_CLIENTS, displayClients.take(5))
      expectGetSessionItemNone(CONFIRM_CLIENTS_SELECTED)

      val result = controller.showReviewSelectedClients(None, None)(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Review selected clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe s"You have selected 5 clients"
      html.select(Css.backLink).attr("href") shouldBe "#"
      html.select(Css.backLink).text() shouldBe "Back"

      val table = html.select(Css.tableWithId("selected-clients"))
      val th = table.select("thead th")
      th.size() shouldBe 4
      th.get(0).text() shouldBe "Client reference"
      th.get(1).text() shouldBe "Tax reference"
      th.get(2).text() shouldBe "Tax service"
      th.get(3).text() shouldBe "Actions"
      val trs = table.select("tbody tr")
      trs.size() shouldBe 5
      // first row
      trs.get(0).select("td").get(0).text() shouldBe "friendly0"
      trs.get(0).select("td").get(1).text() shouldBe "ending in 6780"
      trs.get(0).select("td").get(2).text() shouldBe "VAT"
      val removeClient1 = trs.get(0).select("td").get(3).select("a")
      removeClient1.text() shouldBe "Remove friendly0"
      removeClient1.attr("href") shouldBe ctrlRoute.showConfirmRemoveClient(Some(displayClientsIds(0))).url

      // last row
      trs.get(4).select("td").get(0).text() shouldBe "friendly4"
      trs.get(4).select("td").get(1).text() shouldBe "ending in 6784"
      trs.get(4).select("td").get(2).text() shouldBe "VAT"
      val removeClient4 = trs.get(4).select("td").get(3).select("a")
      removeClient4.text() shouldBe "Remove friendly4"
      removeClient4.attr("href") shouldBe ctrlRoute.showConfirmRemoveClient(Some(displayClientsIds(4))).url

      html.select("form .govuk-fieldset__legend").text() shouldBe "Do you need to select more clients?"
      val answerRadios = html.select(Css.radioButtonsField("answer-radios"))
      answerRadios.select("label[for=answer]").text() shouldBe "Yes, select more clients"
      answerRadios.select("label[for=answer-no]").text() shouldBe "No, continue to adding team members"
    }

    "render with 0 selected clients in session" in {
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(SELECTED_CLIENTS, Seq.empty[DisplayClient])
      expectGetSessionItemNone(CONFIRM_CLIENTS_SELECTED)

      val result = controller.showReviewSelectedClients(None, None)(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Review selected clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe s"You have selected 0 clients"
      html.select(Css.backLink).attr("href") shouldBe "#"
      html.select(Css.backLink).text() shouldBe "Back"

      val table = html.select("table")
      table.size() shouldBe 0

      html.select("form .govuk-fieldset__legend").text() shouldBe "Do you need to select more clients?"
      val answerRadios = html.select(Css.radioButtonsField("answer-radios"))
      answerRadios.select("label[for=answer]").text() shouldBe "Yes, select more clients"
      answerRadios.select("label[for=answer-no]").text() shouldBe "No, continue to adding team members"
    }

    "redirect when no selected clients in session" in {
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItemNone(SELECTED_CLIENTS)

      val result = controller.showReviewSelectedClients(None, None)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSearchClients().url

    }

    "redirect when no group name is in session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(SUSPENSION_STATUS, false)
      expectGetSessionItem(GROUP_TYPE, CUSTOM_GROUP)
      expectGetSessionItemNone(GROUP_NAME) // <- NO GROUP NAME IN SESSION

      val result = controller.showReviewSelectedClients(None, None)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.CreateGroupSelectNameController.showGroupName().url
    }
  }

  s"POST ${routes.CreateGroupSelectClientsController.submitReviewSelectedClients().url}" should {

    s"redirect to ‘${routes.CreateGroupSelectTeamMembersController.showSelectTeamMembers(None, None).url}’ page with answer ‘false'" in {

      implicit val request =
        FakeRequest("POST", s"${controller.submitReviewSelectedClients()}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectPutSessionItem(CONFIRM_CLIENTS_SELECTED, false)

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.CreateGroupSelectTeamMembersController
        .showSelectTeamMembers(None, None)
        .url
    }

    s"redirect to ‘${ctrlRoute.showSearchClients().url}’ page with answer ‘true'" in {

      implicit val request =
        FakeRequest("POST", s"${controller.submitReviewSelectedClients()}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectDeleteSessionItems(clientFilteringKeys)
      expectPutSessionItem(CONFIRM_CLIENTS_SELECTED, true)

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSearchClients().url
    }

    s"redirect to ‘${ctrlRoute.showSearchClients().url}’ with no SELECTED in session" in {

      implicit val request =
        FakeRequest("POST", s"${controller.submitReviewSelectedClients()}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItemNone(SELECTED_CLIENTS)

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSearchClients().url
    }

    s"render errors when no radio button selected" in {

      implicit val request =
        FakeRequest("POST", s"${controller.submitReviewSelectedClients()}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(SELECTED_CLIENTS, displayClients.take(10))

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Review selected clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 10 clients"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you need to select more clients"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you need to select more clients"
    }

    s"render errors when continuing with 0 selected clients in session" in {

      implicit val request =
        FakeRequest("POST", s"${controller.submitReviewSelectedClients()}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(SELECTED_CLIENTS, Seq.empty[DisplayClient])
      expectPutSessionItem(CONFIRM_CLIENTS_SELECTED, false)

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Review selected clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 0 clients"
      html
        .select(Css.errorSummaryForField("answer"))
        .text() shouldBe "You have removed all clients, select at least one to continue"
      html
        .select(Css.errorForField("answer"))
        .text() shouldBe "Error: You have removed all clients, select at least one to continue"
    }
  }

  s"GET Confirm Remove a selected client on ${ctrlRoute.showConfirmRemoveClient(Some("id")).url}" should {

    "render with selected clients" in {
      val clientToRemove = displayClients.head
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(SELECTED_CLIENTS, displayClients.take(5))
      expectPutSessionItem(CLIENT_TO_REMOVE, clientToRemove)
      // when
      val result = controller.showConfirmRemoveClient(Some(clientToRemove.id))(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Remove friendly0 from selected clients? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe s"Remove friendly0 from selected clients?"
      html.select(Css.backLink).attr("href") shouldBe "#"
      html.select(Css.backLink).text() shouldBe "Back"
      val answerRadios = html.select(Css.radioButtonsField("answer-radios"))
      answerRadios.select("label[for=answer]").text() shouldBe "Yes"
      answerRadios.select("label[for=answer-no]").text() shouldBe "No"
    }
  }

  s"POST Remove a selected client ${routes.CreateGroupSelectClientsController.submitConfirmRemoveClient().url}" should {

    s"redirect to ‘${ctrlRoute.showReviewSelectedClients(None, None).url}’ page with answer ‘true'" in {

      val clientToRemove = displayClients.head

      implicit val request = FakeRequest("POST", s"${controller.submitConfirmRemoveClient}")
        .withFormUrlEncodedBody("answer" -> "true")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectGetSessionItem(CLIENT_TO_REMOVE, clientToRemove)
      val remainingClients = displayClients.diff(Seq(clientToRemove))
      expectPutSessionItem(SELECTED_CLIENTS, remainingClients)

      val result = controller.submitConfirmRemoveClient()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showReviewSelectedClients(None, None).url
    }

    s"redirect to ‘${ctrlRoute.showReviewSelectedClients(None, None).url}’ page with answer ‘false'" in {

      val clientToRemove = displayClients.head

      implicit val request =
        FakeRequest("POST", s"${controller.submitReviewSelectedClients()}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectGetSessionItem(CLIENT_TO_REMOVE, clientToRemove)

      val result = controller.submitConfirmRemoveClient()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showReviewSelectedClients(None, None).url
    }

    s"redirect to ‘${ctrlRoute.showSearchClients().url}’ with no CLIENT_TO_REMOVE in session" in {

      implicit val request =
        FakeRequest("POST", s"${controller.submitReviewSelectedClients()}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItemNone(SELECTED_CLIENTS)

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSearchClients().url
    }

    s"render errors when no radio button selected" in {

      implicit val request =
        FakeRequest("POST", s"${controller.submitConfirmRemoveClient()}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(SELECTED_CLIENTS, displayClients.take(10))
      expectGetSessionItem(CLIENT_TO_REMOVE, displayClients.head)

      val result = controller.submitConfirmRemoveClient()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Remove friendly0 from selected clients? - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Remove friendly0 from selected clients?"
      html
        .select(Css.errorSummaryForField("answer"))
        .text() shouldBe "Select yes if you need to remove this client from the access group"
      html
        .select(Css.errorForField("answer"))
        .text() shouldBe "Error: Select yes if you need to remove this client from the access group"

    }
  }
}
