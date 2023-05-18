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
import connectors.{AddMembersToAccessGroupRequest, AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.actions.{AuthAction, SessionAction}
import helpers.Css._
import helpers.{BaseSpec, Css}
import models.{DisplayClient, GroupId}
import org.jsoup.Jsoup
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, redirectLocation}
import services.{ClientService, GroupService, InMemorySessionCacheService, SessionCacheService}
import uk.gov.hmrc.agents.accessgroups.optin.OptedInReady
import uk.gov.hmrc.agents.accessgroups.{Client, GroupSummary}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

class UnassignedClientControllerSpec extends BaseSpec with BeforeAndAfterEach {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]
  implicit val mockSessionService: InMemorySessionCacheService = new InMemorySessionCacheService()
  implicit val mockClientService: ClientService = mock[ClientService]

  private val ctrlRoutes: ReverseUnassignedClientController = routes.UnassignedClientController

  override def beforeEach(): Unit = {
    mockSessionService.values.clear()
  }

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[SessionCacheService]).toInstance(mockSessionService)
      bind(classOf[SessionAction]).toInstance(new SessionAction(mockSessionService))
      bind(classOf[GroupService]).toInstance(mockGroupService)
      bind(classOf[ClientService]).toInstance(mockClientService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val fakeClients: Seq[Client] = List.tabulate(3)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))

  val displayClients: Seq[DisplayClient] = fakeClients.map(DisplayClient.fromClient(_))

  val groupSummaries: Seq[GroupSummary] = (1 to 3).map(i =>
    GroupSummary(GroupId.random(), s"with none setname $i", Some(i * 3), i * 4))

  val controller: UnassignedClientController = fakeApplication.injector.instanceOf[UnassignedClientController]

  s"GET ${ctrlRoutes.showUnassignedClients().url}" should {

    "render unassigned clients list" in {
      // given
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetUnassignedClients(arn)(displayClients)
      //when
      val result = controller.showUnassignedClients()(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Clients who are not in any groups - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Clients who are not in any groups"
      html.select(Css.backLink).attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"

      val th = html.select(Css.tableWithId("multi-select-table")).select("thead th")
      th.size() shouldBe 4
      val tr = html.select(Css.tableWithId("multi-select-table")).select("tbody tr")
      tr.size() shouldBe 3


    }

    "render list with client search" in {
      // given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      await(mockSessionService.put(CLIENT_SEARCH_INPUT, "friendly1"))
      expectGetUnassignedClients(arn)(displayClients, search = Some("friendly1"))

      //when
      val result = controller.showUnassignedClients()(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Filter results for 'friendly1' Clients who are not in any groups - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Clients who are not in any groups"

      val ths = html.select(Css.tableWithId("multi-select-table")).select("thead th")
      ths.size() shouldBe 4
      val trs = html.select(Css.tableWithId("multi-select-table")).select("tbody tr")
      trs.size() shouldBe 1

      html.select("input#search").attr("value") shouldBe "friendly1"
    }

  }

  s"POST ${ctrlRoutes.submitAddUnassignedClients().url}" should {

    s"save selected unassigned clients and redirect to ${ctrlRoutes.showSelectedUnassignedClients()} " +
      s"when button is Continue and form is valid" in {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoutes.submitAddUnassignedClients().url)
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
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      await(mockSessionService.put(CURRENT_PAGE_CLIENTS, displayClients))

      val result = controller.submitAddUnassignedClients()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe s"${ctrlRoutes.showSelectedUnassignedClients().url}"

      // check that the selected clients have been added to the session cache value
      await(mockSessionService.get(SELECTED_CLIENTS)).map(_.map(_.id)) shouldBe Some(List(displayClients.head.id, displayClients.last.id))
    }

    s"""save selected unassigned clients and redirect to ${ctrlRoutes.showUnassignedClients()}
      when button is FILTER (i.e. not CONTINUE)""" in {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoutes.submitAddUnassignedClients().url)
          .withFormUrlEncodedBody(
            "clients[0]" -> displayClients.head.id,
            "clients[1]" -> displayClients.last.id,
            "search" -> "",
            "filter" -> "HMRC-MTD-VAT",
            "submit" -> FILTER_BUTTON
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      await(mockSessionService.put(CURRENT_PAGE_CLIENTS, displayClients))

      val result = controller.submitAddUnassignedClients()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe s"${ctrlRoutes.showUnassignedClients().url}"

      // check that the selected clients have been added to the session cache value
      await(mockSessionService.get(SELECTED_CLIENTS)).map(_.map(_.id)) shouldBe Some(List(displayClients.head.id, displayClients.last.id))
    }

    "present page with errors when form validation fails" in {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoutes.submitAddUnassignedClients().url)
          .withFormUrlEncodedBody(
            "search" -> "",
            "filter" -> "",
            "submit" -> CONTINUE_BUTTON
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetUnassignedClients(arn)(displayClients)
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))

      val result = controller.submitAddUnassignedClients()(request)

      status(result) shouldBe OK

    }
  }

  s"GET ${ctrlRoutes.showSelectedUnassignedClients().url}" should {

    "redirect if NO CLIENTS SELECTED are in session" in {
      //given
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      await(mockSessionService.delete(SELECTED_CLIENTS))
      expectIsArnAllowed(allowed = true)
      expectAuthorisationGrantsAccess(mockedAuthResponse)

      //when
      val result = controller.showSelectedUnassignedClients()(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoutes.showUnassignedClients().url
    }

    "render review selected clients" in {
      //given
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      await(mockSessionService.put(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      //when
      val result = controller.showSelectedUnassignedClients()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Review selected clients - Agent services account - GOV.UK"
      html.select(caption).text() shouldBe "Clients who are not in any groups"
      html.select(H1).text() shouldBe "You have selected 3 clients"
      html.select(Css.tableWithId("selected-clients")).select("tbody tr").size() shouldBe 3
      //and the back link should go to unassigned clients
      html.select(Css.backLink).attr("href") shouldBe "/agent-permissions/unassigned-clients"

      html.select("form .govuk-fieldset__legend").text() shouldBe "Do you need to select more clients?"
      val answerRadios = html.select(Css.radioButtonsField("answer-radios"))
      answerRadios
        .select("label[for=answer]")
        .text() shouldBe "Yes, select more clients"
      answerRadios
        .select("label[for=answer-no]")
        .text() shouldBe "No, continue to select groups"
      html.select(Css.submitButton).text() shouldBe "Save and continue"

    }
  }

  s"POST ${ctrlRoutes.submitSelectedUnassignedClients().url}" should {

    "redirect if no selected clients in session" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      await(mockSessionService.delete(SELECTED_CLIENTS)) // <-- we are testing this

      //when
      val result = controller.submitSelectedUnassignedClients(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(ctrlRoutes.showUnassignedClients().url)
    }

    s"redirect if yes to ${ctrlRoutes.showUnassignedClients().url}" in {
      //given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoutes.submitSelectedUnassignedClients().url)
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      await(mockSessionService.put(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      //when
      val result = controller.submitSelectedUnassignedClients(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(ctrlRoutes.showUnassignedClients().url)
    }

    s"redirect if no to ${ctrlRoutes.showSelectGroupsForSelectedUnassignedClients()}" in {
      //given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoutes.submitSelectedUnassignedClients().url)
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      await(mockSessionService.put(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      //when
      val result = controller.submitSelectedUnassignedClients(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe
        Some(ctrlRoutes.showSelectGroupsForSelectedUnassignedClients().url)
    }

    "render Review selected clients page if errors" in {
      //given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoutes.submitSelectedUnassignedClients().url)
          .withFormUrlEncodedBody()
          .withSession(SessionKeys.sessionId -> "session-x")

      await(mockSessionService.put(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))



      //when
      val result = controller.submitSelectedUnassignedClients(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Review selected clients - Agent services account - GOV.UK"
      html
        .select(Css.errorSummaryForField("answer"))
        .text() shouldBe "Select yes if you need to select more clients"
      html
        .select(Css.errorForField("answer"))
        .text() shouldBe "Error: Select yes if you need to select more clients"

    }

  }

  s"GET ${ctrlRoutes.showSelectGroupsForSelectedUnassignedClients().url}" should {

    "render html with the available groups for these unassigned clients" in {
      //given
      val groupSummaries = (1 to 3).map(i =>
        GroupSummary(GroupId.random(), s"name $i", Some(i * 3), i * 4))

      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupsForArn(arn)(groupSummaries)

      //when
      val result = controller.showSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Which custom access groups would you like to add the selected clients to? - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Which custom access groups would you like to add the selected clients to?"
      //and the back link should go to unassigned clients
      html.select(Css.backLink).attr("href") shouldBe ctrlRoutes.showSelectedUnassignedClients().url

      //checkboxes

      val form = html.select("main form")
      val checkboxes = form.select("#available-groups .govuk-checkboxes__item")
      val checkboxLabels = checkboxes.select("label")
      val checkboxInputs = checkboxes.select("input[type='checkbox']")

      form.attr("action") shouldBe ctrlRoutes.submitSelectGroupsForSelectedUnassignedClients().url
      checkboxes.size shouldBe 3
      checkboxLabels.get(0).text shouldBe "name 1"
      checkboxLabels.get(1).text shouldBe "name 2"
      checkboxLabels.get(2).text shouldBe "name 3"

      checkboxInputs.get(0).attr("name") shouldBe "groups[]"
      checkboxInputs.get(0).attr("value") shouldBe groupSummaries(0).groupId.toString
      checkboxInputs.get(1).attr("name") shouldBe "groups[]"
      checkboxInputs.get(1).attr("value") shouldBe groupSummaries(1).groupId.toString
      checkboxInputs.get(2).attr("name") shouldBe "groups[]"
      checkboxInputs.get(2).attr("value") shouldBe groupSummaries(2).groupId.toString

      form.select("button#continue[type=submit]").text shouldBe "Save and continue"

    }

    "remove any tax service groups from the available choices for these unassigned clients" in {
      //given
      val id1 = GroupId.random()
      val id2 = GroupId.random()
      val groupSummaries = List(
        GroupSummary(id1, s"custom group", Some(3), 4),
        GroupSummary(id2, s"tax service group", Some(3), 4, Some("VAT")))

      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupsForArn(arn)(groupSummaries)

      //when
      val result = controller.showSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      //checkboxes

      val form = html.select("main form")
      val checkboxes = form.select("#available-groups .govuk-checkboxes__item")
      val checkboxLabels = checkboxes.select("label")
      val checkboxInputs = checkboxes.select("input[type='checkbox']")


      checkboxes.size shouldBe 1
      checkboxLabels.get(0).text shouldBe "custom group"

      checkboxInputs.get(0).attr("name") shouldBe "groups[]"
      checkboxInputs.get(0).attr("value") shouldBe id1.toString
    }

    "render html when there are no available groups for these unassigned clients" in {
      //given
      val groupSummaries = Seq.empty

      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupsForArn(arn)(groupSummaries)

      //when
      val result = controller.showSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      val pageHeading = "You do not have any access groups"
      html.title() shouldBe s"$pageHeading - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe pageHeading
      //and the back link should go to the unassigned clients tab
      html.select(Css.backLink).attr("href") shouldBe ctrlRoutes.showSelectedUnassignedClients().url

      //checkboxes

      val form = html.select("main form")
      //shouldn't be anything in the form except the button hence checking the full html
      form.html() shouldBe "<div class=\"govuk-button-group\"> <button type=\"submit\" class=\"govuk-button\" data-module=\"govuk-button\" " +
        "id=\"continue\" name=\"createNew\" value=\"true\"> Create an access group </button> <a class=\"govuk-link\" href=\"/agent-permissions/manage-access-groups\">Go to manage access groups</a> " +
        "\n" + "</div>"


    }
  }

  s"POST ${ctrlRoutes.submitSelectGroupsForSelectedUnassignedClients().url}" should {

    "redirect to create group if CREATE NEW is selected" in {
      //given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoutes.submitSelectGroupsForSelectedUnassignedClients().url)
          .withFormUrlEncodedBody("createNew" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      //when
      val result = controller.submitSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.CreateGroupSelectNameController.showGroupName().url

    }

    "redirect to confirmation page when existing groups are selected to assign the selected clients to" in {
      //given
      val groupSummaries = (1 to 2).map(i => GroupSummary(GroupId.random(), s"name $i", Some(i * 3), i * 4))
      val expectedGroupAddedTo = groupSummaries(0)
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST",
          ctrlRoutes.submitSelectGroupsForSelectedUnassignedClients().url
        ).withFormUrlEncodedBody("groups[0]" -> expectedGroupAddedTo.groupId.toString)
          .withSession(SessionKeys.sessionId -> "session-x")

      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      await(mockSessionService.put(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupsForArn(arn)(groupSummaries)
      expectAddMembersToGroup(
        expectedGroupAddedTo.groupId,
        AddMembersToAccessGroupRequest(None, Some(fakeClients.toSet))
      )

      //when
      val result = controller.submitSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoutes.showConfirmClientsAddedToGroups().url
    }

    "show errors when nothing selected" in {
      //given
      val groupSummaries = (1 to 3).map(i => GroupSummary(GroupId.random(), s"name $i", Some(i * 3), i * 4))

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST",
          ctrlRoutes.submitSelectGroupsForSelectedUnassignedClients().url)
          .withFormUrlEncodedBody()
          .withSession(SessionKeys.sessionId -> "session-x")

      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupsForArn(arn)(groupSummaries)


      //when
      val result = controller.submitSelectGroupsForSelectedUnassignedClients(request)

      //then
      status(result) shouldBe OK
      //and should show errors
      val html = Jsoup.parse(contentAsString(result))
      html.select(Css.errorSummaryForField(groupSummaries(0).groupId.toString))
        .text() shouldBe "You must select an access group"
      html.select(Css.errorForField("field-wrapper")).text() shouldBe "You must select an access group"


    }

  }

  s"GET ${ctrlRoutes.showConfirmClientsAddedToGroups().url}" should {

    "render correctly the select groups for unassigned clients page" in {
      //given
      val groups = Seq("South West", "London")
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      await(mockSessionService.put(GROUPS_FOR_UNASSIGNED_CLIENTS, groups))
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
      await(mockSessionService.get(SELECTED_CLIENTS)) shouldBe None
    }

    "redirect when no group names in session" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      await(mockSessionService.delete(GROUPS_FOR_UNASSIGNED_CLIENTS))
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))

      //when
      val result = controller.showConfirmClientsAddedToGroups(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoutes.showSelectGroupsForSelectedUnassignedClients().url

    }
  }

  s"GET Confirm Remove a selected client on ${ctrlRoutes.showConfirmRemoveClient(None).url}" should {

    "render with selected clients" in {
      val clientToRemove = displayClients.head
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      await(mockSessionService.put(SELECTED_CLIENTS, displayClients.take(2)))
      await(mockSessionService.put(CLIENT_TO_REMOVE, clientToRemove))

      //when
      val result = controller.showConfirmRemoveClient(Option(clientToRemove.id))(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Remove friendly0 from selected clients? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe s"Remove friendly0 from selected clients?"
      html.select(Css.backLink).attr("href") shouldBe ctrlRoutes.showSelectedUnassignedClients(None, None).url

      val answerRadios = html.select(Css.radioButtonsField("answer-radios"))
      answerRadios.select("label[for=answer]").text() shouldBe "Yes"
      answerRadios.select("label[for=answer-no]").text() shouldBe "No"
    }
  }

  s"POST Remove a selected client ${ctrlRoutes.submitConfirmRemoveClient().url}" should {

    s"redirect to '${ctrlRoutes.showSelectedUnassignedClients(None, None).url}' page with answer 'true'" in {

      val clientToRemove = displayClients.head

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitConfirmRemoveClient}")
        .withFormUrlEncodedBody("answer" -> "true")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      await(mockSessionService.put(CLIENT_TO_REMOVE, clientToRemove))

      val remainingClients = displayClients.diff(Seq(clientToRemove))
      await(mockSessionService.put(SELECTED_CLIENTS, remainingClients))

      val result = controller.submitConfirmRemoveClient()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoutes.showSelectedUnassignedClients(None, None).url
    }

    s"redirect to '${ctrlRoutes.showSelectedUnassignedClients(None, None).url}' page with answer 'false'" in {

      val clientToRemove = displayClients.head

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitConfirmRemoveClient()}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      await(mockSessionService.put(SELECTED_CLIENTS, displayClients.take(2)))
      await(mockSessionService.put(CLIENT_TO_REMOVE, clientToRemove))


      val result = controller.submitConfirmRemoveClient()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoutes.showSelectedUnassignedClients(None, None).url
    }


    s"render errors when no radio button selected" in {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest(
          "POST",
          s"${controller.submitConfirmRemoveClient()}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      await(mockSessionService.put(OPT_IN_STATUS, OptedInReady))
      await(mockSessionService.put(CLIENT_TO_REMOVE, displayClients.head))
      await(mockSessionService.put(SELECTED_CLIENTS, displayClients.take(10)))

      val result = controller.submitConfirmRemoveClient()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Remove friendly0 from selected clients? - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Remove friendly0 from selected clients?"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you need to remove this client from the access group"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you need to remove this client from the access group"

    }
  }

}
