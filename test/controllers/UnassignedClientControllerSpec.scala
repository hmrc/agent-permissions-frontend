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
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services.{GroupService, GroupServiceImpl}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

class UnassignedClientControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector
  : AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val groupService: GroupService = new GroupServiceImpl(mockAgentUserClientDetailsConnector, sessionCacheRepo, mockAgentPermissionsConnector)

  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

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

  val fakeClients: Seq[Client] = List.tabulate(3)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))

  val displayClients: Seq[DisplayClient] = fakeClients.map(DisplayClient.fromClient(_))

  val groupSummaries: Seq[GroupSummary] = (1 to 3).map(i =>
    GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))

  val controller: UnassignedClientController = fakeApplication.injector.instanceOf[UnassignedClientController]

  s"GET ${routes.UnassignedClientController.showUnassignedClients.url}" should {

    "render unassigned clients list" in {
      // given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      expectGetUnassignedClientsSuccess(arn, displayClients)


      //when
      val result = controller.showUnassignedClients(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Unassigned clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Unassigned clients"
      html.select(Css.backLink).attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 4
      val tr = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      tr.size() shouldBe 3


    }

    "render list with client search" in {
      // given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(CLIENT_SEARCH_INPUT, "friendly1"))

      expectGetUnassignedClientsSuccess(arn, displayClients)

      //when
      val result = controller.showUnassignedClients(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Filter results for 'friendly1' Unassigned clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Unassigned clients"

      html.select(H2).text() shouldBe "Filter results for 'friendly1'"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 4
      val tr = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      tr.size() shouldBe 3

      html.select("input#search").attr("value") shouldBe "friendly1"


    }

    "render list with hidden clients exist" in {
      // given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(HIDDEN_CLIENTS_EXIST, true))

      expectGetUnassignedClientsSuccess(arn, displayClients)

      //when
      val result = controller.showUnassignedClients(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Unassigned clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Unassigned clients"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 4
      val tr = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      tr.size() shouldBe 3


    }

  }

  s"POST ${routes.UnassignedClientController.submitAddUnassignedClients.url}" should {
    s"save selected unassigned clients and redirect to ${routes.UnassignedClientController.showSelectedUnassignedClients} " +
      s"when button is Continue" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      stubGetClientsOk(arn)(fakeClients)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", routes.UnassignedClientController.submitAddUnassignedClients.url)
          .withFormUrlEncodedBody(
            "hasSelectedClients" -> "false",
            "clients[0]" -> displayClients.head.id,
            "clients[1]" -> displayClients.last.id,
            "search" -> "",
            "filter" -> "",
            "submit" -> "continue"
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitAddUnassignedClients()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe s"${routes.UnassignedClientController.showSelectedUnassignedClients.url}"

      await(sessionCacheRepo.getFromSession(SELECTED_CLIENTS)) shouldBe Some(Seq(displayClients.head.copy(selected = true),
        displayClients.last.copy(selected = true)))

    }

    s"save selected unassigned clients and redirect to ${routes.UnassignedClientController.showUnassignedClients} " +
      s"when button is NOT Continue" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      stubGetClientsOk(arn)(fakeClients)
      expectGetUnassignedClientsSuccess(arn, displayClients)


      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", routes.UnassignedClientController.submitAddUnassignedClients.url)
          .withFormUrlEncodedBody(
            "hasSelectedClients" -> "false",
            "clients[0]" -> displayClients.head.id,
            "clients[1]" -> displayClients.last.id,
            "search" -> "",
            "filter" -> "VAT",
            "submit" -> "filter"
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitAddUnassignedClients()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe s"${routes.UnassignedClientController.showUnassignedClients}"

      await(sessionCacheRepo.getFromSession(SELECTED_CLIENTS)) shouldBe Some(Seq(displayClients.head.copy(selected = true),
        displayClients.last.copy(selected = true)))

    }

    s"present page with errors when form validation fails" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetUnassignedClientsSuccess(arn, Seq.empty[DisplayClient])

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", routes.UnassignedClientController.submitAddUnassignedClients.url)
          .withFormUrlEncodedBody(
            "hasSelectedClients" -> "false",
            "search" -> "",
            "filter" -> "",
            "submit" -> "filter"
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitAddUnassignedClients()(request)

      status(result) shouldBe OK

    }

    s"present page with errors when form validation fails and filtered clients exist" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetUnassignedClientsSuccess(arn, Seq.empty[DisplayClient])

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", routes.UnassignedClientController.submitAddUnassignedClients.url)
          .withFormUrlEncodedBody(
            "hasSelectedClients" -> "false",
            "search" -> "",
            "filter" -> "",
            "submit" -> "continue"
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(FILTERED_CLIENTS, displayClients))

      val result = controller.submitAddUnassignedClients()(request)

      status(result) shouldBe OK

      await(sessionCacheRepo.getFromSession(SELECTED_CLIENTS)) shouldBe None

    }
  }

  s"GET ${routes.UnassignedClientController.showSelectedUnassignedClients.url}" should {

    "redirect if no clients selected are in session" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectIsArnAllowed(allowed = true)
      expectAuthorisationGrantsAccess(mockedAuthResponse)

      //when
      val result = controller.showSelectedUnassignedClients(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.UnassignedClientController.showUnassignedClients.url
    }

    "render html when there are groups" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      //when
      val result = controller.showSelectedUnassignedClients(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Review selected clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 3 clients"
      html.select(Css.tableWithId("sortable-table")).select("tbody tr").size() shouldBe 3
      //and the back link should go to the unassigned clients tab
      html.select(Css.backLink).attr("href") shouldBe "/agent-permissions/unassigned-clients"

      html.select("form .govuk-fieldset__legend").text() shouldBe "Do you need to add or remove selected clients?"
      val answerRadios = html.select(Css.radioButtonsField("answer-radios"))
      answerRadios
        .select("label[for=answer]")
        .text() shouldBe "Yes, add or remove clients"
      answerRadios
        .select("label[for=answer-no]")
        .text() shouldBe "No, continue to next section"
      html.select(Css.submitButton).text() shouldBe "Save and continue"

    }
  }

  s"POST ${routes.UnassignedClientController.submitSelectedUnassignedClients.url}" should {

    "redirect if no selected clients in session" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      stubOptInStatusOk(arn)(OptedInReady)

      //when
      val result = controller.submitSelectedUnassignedClients(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UnassignedClientController.showUnassignedClients.url)
    }

    s"redirect if yes to ${routes.UnassignedClientController.showUnassignedClients}" in {
      //given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", routes.UnassignedClientController.submitSelectedUnassignedClients.url)
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      stubOptInStatusOk(arn)(OptedInReady)

      //when
      val result = controller.submitSelectedUnassignedClients(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.UnassignedClientController.showUnassignedClients.url)
    }

    s"redirect if no to ${routes.UnassignedClientController.showSelectGroupsForSelectedUnassignedClients}" in {
      //given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", routes.UnassignedClientController.submitSelectedUnassignedClients.url)
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      stubOptInStatusOk(arn)(OptedInReady)

      //when
      val result = controller.submitSelectedUnassignedClients(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe
        Some(routes.UnassignedClientController.showSelectGroupsForSelectedUnassignedClients.url)
    }

    "render Review selected clients page if errors" in {
      //given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", routes.UnassignedClientController.submitSelectedUnassignedClients.url)
          .withFormUrlEncodedBody()
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      stubOptInStatusOk(arn)(OptedInReady)

      //when
      val result = controller.submitSelectedUnassignedClients(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Review selected clients - Agent services account - GOV.UK"
      html
        .select(Css.errorSummaryForField("answer"))
        .text() shouldBe "Select yes if you need to add or remove selected clients"
      html
        .select(Css.errorForField("answer"))
        .text() shouldBe "Error: Select yes if you need to add or remove selected clients"

    }

  }

  s"GET ${routes.UnassignedClientController.showSelectGroupsForSelectedUnassignedClients.url}" should {

    "render html with the available groups for these unassigned clients" in {
      //given
      val groupSummaries = (1 to 3).map(i =>
        GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSummarySuccess(arn, groupSummaries)

      //when
      val result = controller.showSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Which access groups would you like to add the selected clients to? - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Which access groups would you like to add the selected clients to?"
      //and the back link should go to the unassigned clients tab
      html.select(Css.backLink).attr("href") shouldBe routes.UnassignedClientController.showSelectedUnassignedClients.url

      //checkboxes

      val form = html.select("main form")
      val checkboxes = form.select("#available-groups .govuk-checkboxes__item")
      val checkboxLabels = checkboxes.select("label")
      val checkboxInputs = checkboxes.select("input[type='checkbox']")

      form.attr("action") shouldBe routes.UnassignedClientController.submitSelectGroupsForSelectedUnassignedClients.url
      checkboxes.size shouldBe 3
      checkboxLabels.get(0).text shouldBe "name 1"
      checkboxLabels.get(1).text shouldBe "name 2"
      checkboxLabels.get(2).text shouldBe "name 3"

      checkboxInputs.get(0).attr("name") shouldBe "groups[]"
      checkboxInputs.get(0).attr("value") shouldBe "groupId1"
      checkboxInputs.get(1).attr("name") shouldBe "groups[]"
      checkboxInputs.get(1).attr("value") shouldBe "groupId2"
      checkboxInputs.get(2).attr("name") shouldBe "groups[]"
      checkboxInputs.get(2).attr("value") shouldBe "groupId3"

      form.select("#createNew-hint").text shouldBe "or"
      val createNewCheckboxes = form.select("div#createNew .govuk-checkboxes__item")
      createNewCheckboxes.select("label").text shouldBe "Add to a new access group"
      createNewCheckboxes.select("input[type='checkbox']").attr("name") shouldBe "createNew"
      createNewCheckboxes.select("input[type='checkbox']").attr("value") shouldBe "true"

      form.select("button#continue[type=submit]").text shouldBe "Continue"

    }


    "render html when there are no available groups for these unassigned clients" in {
      //given
      val groupSummaries = Seq.empty

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSummarySuccess(arn, groupSummaries)


      //when
      val result = controller.showSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      val pageHeading = "You do not have any access groups. Add these clients to a new access group"
      html.title() shouldBe s"$pageHeading - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe pageHeading
      //and the back link should go to the unassigned clients tab
      html.select(Css.backLink).attr("href") shouldBe routes.UnassignedClientController.showSelectedUnassignedClients.url

      //checkboxes

      val form = html.select("main form")
      //shouldn't be anything in the form except the button hence checking the full html
      form.html() shouldBe "<button type=\"submit\" class=\"govuk-button\" data-module=\"govuk-button\" " +
        "id=\"continue\" name=\"createNew\" value=\"true\"> Add to a new access group </button>"


    }
  }

  s"POST ${routes.UnassignedClientController.submitSelectGroupsForSelectedUnassignedClients.url}" should {

    "redirect to create group if CREATE NEW is selected" in {
      //given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", routes.UnassignedClientController.submitSelectGroupsForSelectedUnassignedClients.url)
          .withFormUrlEncodedBody("createNew" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      //when
      val result = controller.submitSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.CreateGroupController.showGroupName.url

    }

    "redirect to confirmation page when existing groups are selected to assign the selected clients to" in {
      //given
      val groupSummaries = (1 to 3).map(i => GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST",
          routes.UnassignedClientController.submitSelectGroupsForSelectedUnassignedClients.url
        ).withFormUrlEncodedBody("groups[0]" -> groupSummaries(0).groupId)
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSummarySuccess(arn, groupSummaries)


      //when
      val result = controller.submitSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.UnassignedClientController.showConfirmClientsAddedToGroups.url

    }

    "show errors when nothing selected" in {
      //given
      val groupSummaries = (1 to 3).map(i => GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST",
          routes.UnassignedClientController.submitSelectGroupsForSelectedUnassignedClients.url)
          .withFormUrlEncodedBody()
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSummarySuccess(arn, groupSummaries)


      //when
      val result = controller.submitSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe OK
      //and should show errors
      val html = Jsoup.parse(contentAsString(result))
      html.select(Css.errorSummaryForField("groupId1"))
        .text() shouldBe "You must select an access group or add a new group"
      html.select(Css.errorForField("field-wrapper")).text() shouldBe "You must select an access group or add a new group"


    }

    "show errors when both createNew and existing groups are selected" in {
      //given
      val groupSummaries = (1 to 3).map(i => GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST",
          routes.UnassignedClientController.submitSelectGroupsForSelectedUnassignedClients.url)
          .withFormUrlEncodedBody("createNew" -> "true", "groups[0]" -> "12412312")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSummarySuccess(arn, groupSummaries)

      //when
      val result = controller.submitSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe OK
      //and should show errors
      val html = Jsoup.parse(contentAsString(result))
      html.select(Css.errorSummaryForField("groupId1"))
        .text() shouldBe "You cannot add to existing groups at the same time as creating a new group"
      html.select(Css.errorForField("field-wrapper")).text() shouldBe "You cannot add to existing groups at the same time as creating a new group"


    }
  }

  s"GET ${routes.UnassignedClientController.showConfirmClientsAddedToGroups.url}" should {

    "render correctly the select groups for unassigned clients page" in {
      //given
      val groups = Seq("South West", "London")
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUPS_FOR_UNASSIGNED_CLIENTS, groups))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

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
      listItems.get(0).text shouldBe groups.head
      listItems.get(1).text shouldBe groups(1)
      //and the back link should not be present
      html.select(Css.backLink).size() shouldBe 0

    }

    "redirect when no group names in session" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      //when
      val result = controller.showConfirmClientsAddedToGroups(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.UnassignedClientController.showSelectGroupsForSelectedUnassignedClients.url

    }
  }

}
