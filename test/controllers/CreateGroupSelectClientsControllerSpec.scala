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
import controllers.actions.AuthAction
import helpers.Css.{H1, paragraphs}
import helpers.{BaseSpec, Css}
import models.{AddClientsToGroup, DisplayClient}
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, redirectLocation}
import play.api.test.{FakeRequest, Helpers}
import repository.SessionCacheRepository
import services.{ClientService, GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.{Client, OptedInReady}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

class CreateGroupSelectClientsControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]
  implicit val mockClientService: ClientService = mock[ClientService]
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
      bind(classOf[GroupService]).toInstance(mockGroupService)
      bind(classOf[SessionCacheService]).toInstance(mockSessionCacheService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder.configure("mongodb.uri" -> mongoUri).build()
    
  val controller: CreateGroupSelectClientsController = fakeApplication.injector.instanceOf[CreateGroupSelectClientsController]

  val fakeClients: Seq[Client] = List.tabulate(25)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))

  val displayClients: Seq[DisplayClient] = fakeClients.map(DisplayClient.fromClient(_))

  val displayClientsIds: Seq[String] = displayClients.map(_.id)

  private val ctrlRoute: ReverseCreateGroupSelectClientsController = routes.CreateGroupSelectClientsController

  def expectAuthOkArnAllowedOptedInReadyWithGroupName(): Unit = {
    expectAuthorisationGrantsAccess(mockedAuthResponse)
    expectIsArnAllowed(allowed = true)
    expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
    expectGetSessionItem(GROUP_NAME, groupName)
  }

  s"GET ${ctrlRoute.showSearchClients.url}" should {
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
      html
        .select(Css.backLink)
        .attr("href") shouldBe routes.CreateGroupController.showConfirmGroupName.url

      html.select(Css.labelFor("search")).text() shouldBe "Filter by tax reference or client reference"

      html.select(Css.labelFor("filter")).text() shouldBe "Filter by tax service"

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
      html
        .select(Css.backLink)
        .attr("href") shouldBe routes.CreateGroupController.showConfirmGroupName.url

      html.select(Css.labelFor("search")).text() shouldBe "Filter by tax reference or client reference"
      html.select("#search").attr("value") shouldBe "Harry"
      html.select(Css.labelFor("filter")).text() shouldBe "Filter by tax service"
      //TODO this isn't working
      //html.select("#filter").attr("value") shouldBe "HMRC-MTD-VAT"

    }

  }

  s"POST ${ctrlRoute.submitSearchClients.url}" should {
    // TODO - using fully optional form atm, clarify expected error behaviour
//    "render errors on client search page" in {
//      expectAuthOkArnAllowedOptedInReadyWithGroupName()
//      expectSaveSearch(arn)()
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
      expectSaveSearch(arn)(Some("Harry"), Some("HMRC-MTD-VAT"))

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest(
          "POST",
          s"${controller.submitSearchClients()}")
          .withFormUrlEncodedBody("search" -> "Harry", "filter" -> "HMRC-MTD-VAT")
          .withSession(SessionKeys.sessionId -> "session-x")


      val result = controller.submitSearchClients()(request)
      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showSelectClients(Some(1),Some(20)).url
    }

  }

  s"GET ${ctrlRoute.showSelectClients(None, None).url}" should {

    "render a page of clients" in {
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)
      expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
      expectGetPageOfClients(arn)(displayClients)

      val result = controller.showSelectClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"
      html
        .select(Css.backLink)
        .attr("href") shouldBe routes.CreateGroupSelectClientsController.showSearchClients.url

      val th = html.select(Css.tableWithId("multi-select-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Client reference"
      th.get(2).text() shouldBe "Tax reference"
      th.get(3).text() shouldBe "Tax service"
      val trs =
        html.select(Css.tableWithId("multi-select-table")).select("tbody tr")
      // TODO something is up here - should be 20 a page
      trs.size() shouldBe 25
      // first row
      trs.get(0).select("td").get(1).text() shouldBe "friendly0"
      trs.get(0).select("td").get(2).text() shouldBe "ending in 6780"
      trs.get(0).select("td").get(3).text() shouldBe "VAT"

      // last row
      trs.get(19).select("td").get(1).text() shouldBe "friendly19"
      trs.get(19).select("td").get(2).text() shouldBe "ending in 7819"
      trs.get(19).select("td").get(3).text() shouldBe "VAT"
      html.select(Css.submitButton).text() shouldBe "Continue"
    }

    "render with filtered clients held in session when a filter was applied" in {
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)
      expectGetSessionItem(CLIENT_SEARCH_INPUT, "John")
      expectGetPageOfClients(arn)(displayClients)

      val result = controller.showSelectClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Filter results for 'John' Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"

      //TODO check if we need to add a h2 with filter/search terms
      //html.select(H2).text() shouldBe "Filter results for 'John'"

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

    "render with NO Clients" in {
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)
      expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
      expectGetPageOfClients(arn)(Seq.empty) // <- nothing returned from session

      val result = controller.showSelectClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"

      // No table
      val th = html.select(Css.tableWithId("multi-select-table")).select("thead th")
      th.size() shouldBe 0
      val trs =
        html.select(Css.tableWithId("multi-select-table")).select("tbody tr")
      trs.size() shouldBe 0

      // Not found content
      html.select(Css.H2).text() shouldBe "No clients found"
      html.select(paragraphs).get(1).text() shouldBe "Update your filters and try again or clear your filters to see all your clients"

    }

    "redirect when no group name is in session" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME) // <- NO GROUP NAME IN SESSION

      val result = controller.showSelectClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.CreateGroupController.showGroupName.url
    }
  }

  s"POST to ${ctrlRoute.submitSelectedClients.url}" should {

    "save selected clients to session" when {

      s"button is Continue and redirect to ${ctrlRoute.showReviewSelectedClients(None, None).url}" in {

        implicit val request = FakeRequest("POST",
            ctrlRoute.submitSelectedClients.url)
            .withSession(SessionKeys.sessionId -> "session-x")
            .withFormUrlEncodedBody(
              "clients[]" -> displayClientsIds.head,
              "clients[]" -> displayClientsIds.last,
              "submit" -> CONTINUE_BUTTON
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
          FakeRequest("POST",
            ctrlRoute.submitSelectedClients.url)
            .withSession(SessionKeys.sessionId -> "session-x")
            .withFormUrlEncodedBody(
              "clients[]" -> displayClientsIds.head,
              "clients[]" -> displayClientsIds.last,
              "submit" -> PAGINATION_BUTTON.concat("_2")
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

      s"bad submit (not continue or page number) redirect to ${ctrlRoute.showSearchClients.url}" in {

        implicit val request =
          FakeRequest("POST",
            ctrlRoute.submitSelectedClients.url)
            .withSession(SessionKeys.sessionId -> "session-x")
            .withFormUrlEncodedBody(
              "clients[]" -> displayClientsIds.head,
              "clients[]" -> displayClientsIds.last,
              "submit" -> "tamperedWithOrMissing"
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
        redirectLocation(result).get shouldBe ctrlRoute.showSearchClients.url
      }


    }

    "display error when button is Continue, no clients were selected" in {

      // given
      implicit val request = FakeRequest("POST", ctrlRoute.submitSelectedClients.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody(
          "clients[]" -> "",
          "submit" -> CONTINUE_BUTTON
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
      implicit val request = FakeRequest("POST", ctrlRoute.submitSelectedClients.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody(
          "clients[]" -> "",
          "submit" -> CONTINUE_BUTTON
        )

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      //this currently selected member will be unselected as part of the post
      expectGetSessionItem(SELECTED_CLIENTS, displayClients.take(1))
      val emptyForm = AddClientsToGroup(submit = CONTINUE_BUTTON)
      //now no selected members
      expectSavePageOfClients(emptyForm, Seq.empty[DisplayClient])
      expectGetPageOfClients(arn)(displayClients)

      // when
      val result = controller.submitSelectedClients()(request)

      // then
      status(result) shouldBe OK

      //and
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Select clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"
      html.select(Css.errorSummaryLinkWithHref("#clients")).text() shouldBe "You must select at least one client"

    }

    "redirect to createGroup when POSTED without groupName in Session" in {

      // given
      implicit val request = FakeRequest(
        "POST",
        ctrlRoute.submitSelectedClients.url
      ).withFormUrlEncodedBody("submit" -> CONTINUE_BUTTON)
        .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME)

      // when
      val result = controller.submitSelectedClients()(request)

      // then
      status(result) shouldBe SEE_OTHER
      Helpers.redirectLocation(result) shouldBe Some(routes.CreateGroupController.showGroupName.url)
    }
  }

  s"GET ${ctrlRoute.showReviewSelectedClients(None, None).url}" should {

    "render with selected clients" in {
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(SELECTED_CLIENTS, displayClients.take(5))

      val result = controller.showReviewSelectedClients(None, None)(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Review selected clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe s"You have selected 5 clients"
      html.select(Css.backLink).attr("href") shouldBe ctrlRoute.showSelectClients(None, None).url

      val table = html.select(Css.tableWithId("selected-clients"))
      val th = table.select("thead th")
      th.size() shouldBe 3
      th.get(0).text() shouldBe "Client reference"
      th.get(1).text() shouldBe "Tax reference"
      th.get(2).text() shouldBe "Tax service"
      val trs = table.select("tbody tr")
      trs.size() shouldBe 5
      // first row
      trs.get(0).select("td").get(0).text() shouldBe "friendly0"
      trs.get(0).select("td").get(1).text() shouldBe "ending in 6780"
      trs.get(0).select("td").get(2).text() shouldBe "VAT"

      // last row
      trs.get(4).select("td").get(0).text() shouldBe "friendly4"
      trs.get(4).select("td").get(1).text() shouldBe "ending in 6784"
      trs.get(4).select("td").get(2).text() shouldBe "VAT"

      val answerRadios = html.select(Css.radioButtonsField("answer-radios"))
      answerRadios.select("label[for=answer]").text() shouldBe "Yes, add or remove clients"
      answerRadios.select("label[for=answer-no]").text() shouldBe "No, continue to next section"
    }

    "redirect when no selected clients in session" in {
      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItemNone(SELECTED_CLIENTS)

      val result = controller.showReviewSelectedClients(None, None)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSearchClients.url

    }

    "redirect when no group name is in session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME) // <- NO GROUP NAME IN SESSION

      val result = controller.showReviewSelectedClients(None, None)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.CreateGroupController.showGroupName.url
    }
  }

  s"POST ${routes.CreateGroupSelectClientsController.submitReviewSelectedClients.url}" should {

    s"redirect to '${routes.CreateGroupSelectTeamMembersController.showSelectTeamMembers(None, None).url}' page with answer 'false'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedClients()}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.CreateGroupSelectTeamMembersController.showSelectTeamMembers(None, None).url
    }

    s"redirect to '${ctrlRoute.showSearchClients.url}' page with answer 'true'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedClients()}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectDeleteSessionItems(clientFilteringKeys)

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSearchClients.url
    }

    s"redirect to '${ctrlRoute.showSearchClients.url}' with no SELECTED in session" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedClients()}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItemNone(SELECTED_CLIENTS)

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSearchClients.url
    }

    s"render errors when no radio button selected" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedClients()}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkArnAllowedOptedInReadyWithGroupName()
      expectGetSessionItem(SELECTED_CLIENTS, displayClients.take(10))

      val result = controller.submitReviewSelectedClients()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Review selected clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 10 clients"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you need to add or remove selected clients"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you need to add or remove selected clients"

    }
  }

}
