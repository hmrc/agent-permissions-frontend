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
import connectors.{AgentClientAuthorisationConnector, AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.actions.AuthAction
import helpers.Css.{H1, checkYourAnswersListRows}
import helpers.{BaseSpec, Css}
import models.{DisplayClient, GroupId}
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{NOT_FOUND, OK, SEE_OTHER}
import play.api.test.FakeRequest
import play.api.test.Helpers.{POST, await, contentAsString, defaultAwaitTimeout, redirectLocation}
import services.{ClientService, GroupService, SessionCacheService}
import uk.gov.hmrc.agents.accessgroups.optin.OptedInReady
import uk.gov.hmrc.agents.accessgroups.{Client, GroupSummary}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

class ManageClientControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit lazy val mockAgentClientAuthConnector: AgentClientAuthorisationConnector = mock[AgentClientAuthorisationConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]
  implicit val mockClientService: ClientService = mock[ClientService]
  implicit lazy val sessionCacheService: SessionCacheService = mock[SessionCacheService]

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction])
        .toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector, mockAgentClientAuthConnector, sessionCacheService))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[ClientService]).toInstance(mockClientService)
      bind(classOf[SessionCacheService]).toInstance(sessionCacheService)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[GroupService]).toInstance(mockGroupService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val controller: ManageClientController =
    fakeApplication.injector.instanceOf[ManageClientController]

  val fakeClients: Seq[Client] =
    List.tabulate(3)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))

  val fakeClientWithNoFriendlyName: Client = Client(s"HMRC-MTD-VAT~VRN~123456789", "")

  val displayClientWithNoFrieldyName: DisplayClient = DisplayClient.fromClient(fakeClientWithNoFriendlyName)

  val clientWithoutNameId: String = displayClientWithNoFrieldyName.id

  val displayClients: Seq[DisplayClient] = fakeClients.map(DisplayClient.fromClient(_))

  val displayClientsIds: Seq[String] =
    displayClients.map(client =>
      client.id)

  val clientId: String = displayClientsIds.head

  val groupSummaries = Seq(
    GroupSummary(GroupId.random(), "groupName", Some(33), 9),
    GroupSummary(GroupId.random(), "groupName1", Some(3), 1),
    GroupSummary(GroupId.random(), "groupName2", Some(3), 1, taxService = Some("VAT")),
  )

  val enrolmentKey: String = "HMRC-MTD-VAT~VRN~123456780"
  private val ctrlRoute: ReverseManageClientController = routes.ManageClientController

  def expectAuthOkOptedInReady(): Unit = {
    expectAuthorisationGrantsAccess(mockedAuthResponse)
    expectIsArnAllowed(allowed = true)
    expectGetSessionItem(SUSPENSION_STATUS, false)
    expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
  }

  s"GET ${ctrlRoute.showPageOfClients(None).url}" should {

    "render the manage clients list with no search " in {
      //given
      expectAuthOkOptedInReady()
      expectGetPageOfClients(arn, 1, 10)(displayClients)
      expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)

      //when
      val result = controller.showPageOfClients(None)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Manage clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage clients"
      html.select(Css.backLink).attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
      html.select(Css.backLink).text() shouldBe "Return to manage account"

      val th = html.select(Css.tableWithId("manage-clients-list")).select("thead th")
      th.size() shouldBe 4
      th.get(0).text() shouldBe "Client reference"
      th.get(1).text() shouldBe "Tax reference"
      th.get(2).text() shouldBe "Tax service"
      th.get(3).text() shouldBe "Actions"

      val trs = html.select(Css.tableWithId("manage-clients-list")).select("tbody tr")

      trs.size() shouldBe 3

      val paginationItems = html.select(Css.pagination_li)
      paginationItems.size() shouldBe 4
      paginationItems.select("a").get(0).text() shouldBe "2"
      paginationItems.select("a").get(0).attr("href") startsWith "/agent-permissions/manage-clients?page=2"

      html.select(".hmrc-report-technical-issue").text() shouldBe "Is this page not working properly? (opens in new tab)"
      html.select(".hmrc-report-technical-issue").attr("href") startsWith "http://localhost:9250/contact/report-technical-problem?newTab=true&service=AOSS"

    }

    "render the 'Showing...' text correctly when search and filter are defined" in {
      //given
      expectAuthOkOptedInReady()
      expectGetPageOfClients(arn, 1, 10)(displayClients)
      expectGetSessionItem(CLIENT_SEARCH_INPUT, "friendly")
      expectGetSessionItem(CLIENT_FILTER_INPUT, "HMRC-MTD-VAT")

      //when
      val result = controller.showPageOfClients(None)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.body().text() should include ("""Showing total of 40 clients for ‘friendly’ and ‘VAT’""")
    }

  }

  s"GET ${ctrlRoute.submitPageOfClients().url}" should {

    "render the manage clients list with search term posted" in {
      //given
      expectAuthOkOptedInReady()
      expectPutSessionItem(CLIENT_SEARCH_INPUT, "friendly1")
      expectPutSessionItem(CLIENT_FILTER_INPUT, "")

      val url = ctrlRoute.submitPageOfClients().url
      implicit val fakeRequest = FakeRequest(POST, url)
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withFormUrlEncodedBody("search"-> "friendly1", "submit"-> FILTER_BUTTON)
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.submitPageOfClients(fakeRequest)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(ctrlRoute.showPageOfClients(None).url)
    }

    "redirect to baseUrl when CLEAR FILTER is clicked" in {
      //given
      expectAuthOkOptedInReady()
      expectDeleteSessionItem(CLIENT_SEARCH_INPUT)
      expectDeleteSessionItem(CLIENT_FILTER_INPUT)
      
      //and we have CLEAR filter in query params
      implicit val fakeRequest =
        FakeRequest(POST, ctrlRoute.submitPageOfClients().url)
        .withHeaders("Authorization" -> "Bearer XYZ")
          .withFormUrlEncodedBody("submit"-> CLEAR_BUTTON)
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.submitPageOfClients(fakeRequest)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showPageOfClients(None).url
    }

    "redirect  when form is empty" in {
      //given
      expectAuthOkOptedInReady()

      //and we have CLEAR filter in query params
      implicit val fakeRequest =
        FakeRequest(POST, ctrlRoute.submitPageOfClients().url)
          .withHeaders("Authorization" -> "Bearer XYZ")
          .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.submitPageOfClients(fakeRequest)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showPageOfClients(None).url
    }

  }

  s"GET ${ctrlRoute.showClientDetails(clientId).url}" should {

    "render not found for invalid id" in {
      //given
      expectAuthOkOptedInReady()
      val invalidClientId = "invalid id"
      expectGetClientNotFound(arn)(invalidClientId)

      //when
      val result = controller.showClientDetails(invalidClientId)(request)

      //then
      status(result) shouldBe NOT_FOUND
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Client not found - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Client not found"

    }

    "render the clients details page with NO GROUPS" in {
      //given
      expectAuthOkOptedInReady()
      val expectedClient = displayClients.head
      expectGetGroupSummariesForClient(arn)(expectedClient)( groupSummaries)
      expectGetClient(arn)(expectedClient)

      //when
      val result = controller.showClientDetails(expectedClient.id)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Client details - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Client details"

      val summaryListRows = html.select(checkYourAnswersListRows)

      summaryListRows.get(0).childrenSize() shouldBe 3 // update link
      summaryListRows.get(1).childrenSize() shouldBe 2
      summaryListRows.get(2).childrenSize() shouldBe 2

      summaryListRows.get(0).text() shouldBe "Client reference friendly0 Update Client reference"
      summaryListRows.get(1).text() shouldBe "Tax reference ending in 6780"
      summaryListRows.get(2).text() shouldBe "Tax service VAT"

    }

    "render the clients details page with list of groups" in {
      //given
      expectAuthOkOptedInReady()
      val expectedClient = displayClients.head
      expectGetClient(arn)(expectedClient)
      expectGetGroupSummariesForClient(arn)(expectedClient)( groupSummaries)

      //when
      val result = controller.showClientDetails(expectedClient.id)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Client details - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Client details"

      html.body.text().contains("Not assigned to a group") shouldBe false

      val linksToGroups = html.select("main div#member-of-groups ul li a")
      linksToGroups.size() shouldBe 3
      linksToGroups.get(0).text() shouldBe "groupName"
      linksToGroups.get(0).attr("href") shouldBe
        controllers.routes.ManageGroupClientsController.showExistingGroupClients(groupSummaries.head.groupId,None, None).url

      linksToGroups.get(2).text() shouldBe "groupName2"
      linksToGroups.get(2).attr("href") shouldBe
        controllers.routes.ManageTaxGroupClientsController.showExistingGroupClients(groupSummaries(2).groupId,None,None).url}

  }

  s"GET ${ctrlRoute.showUpdateClientReference(clientId).url}" should {

    "render update_client_reference with existing client reference" in {
      //given
      expectAuthOkOptedInReady()
      val expectedClient = displayClients.head
      expectGetClient(arn)(expectedClient)
      //when
      val result = controller.showUpdateClientReference(expectedClient.id)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Update client reference - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Update client reference"

      html.body.select("input#clientRef").attr("value") shouldBe "friendly0"
    }

    "render update_client_reference for invalid id" in {
      //given
      expectAuthOkOptedInReady()
      expectGetClientNotFound(arn)("invalid")

      //when
      val caught = intercept[RuntimeException] {
        await(controller.showUpdateClientReference("invalid")(request))
      }
      caught.getMessage shouldBe "client reference supplied did not match any client"

    }

    "render update_client_reference without a client reference" in {
      //given
      expectAuthOkOptedInReady()
      val expectedClient = displayClients.head.copy(name = "")
      expectGetClient(arn)(expectedClient)

      //when
      val result = controller.showUpdateClientReference(expectedClient.id)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Update client reference - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Update client reference"

      html.body.select("input#clientRef").attr("value") shouldBe ""
    }

  }

  s"POST to UPDATE CLIENT REF at ${ctrlRoute.submitUpdateClientReference(clientId).url}" should {

    s"redirect to ${ctrlRoute.showClientReferenceUpdatedComplete(clientId)} and save client reference" in {
      //given
      expectAuthOkOptedInReady()
      val newClientReference = "whatever"
      val expectedClient = displayClients.head
      expectLookupClient(arn)(expectedClient)
      expectPutSessionItem(CLIENT_REFERENCE, newClientReference)
      expectUpdateClientReference(arn, expectedClient, newClientReference)

      implicit val request = FakeRequest(POST, ctrlRoute.submitUpdateClientReference(expectedClient.id).url)
        .withFormUrlEncodedBody("clientRef" -> newClientReference)
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.submitUpdateClientReference(expectedClient.id)(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(ctrlRoute.showClientReferenceUpdatedComplete(expectedClient.id).url)

    }

    "display errors for update_client_details" in {
      //given
      expectAuthOkOptedInReady()
      val expectedClient = displayClients.head
      expectLookupClient(arn)(expectedClient)

      //when
      val result = controller.submitUpdateClientReference(expectedClient.id)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Error: Update client reference - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Update client reference"
    }
  }

  s"GET ${ctrlRoute.showClientReferenceUpdatedComplete(clientId).url}" should {

    "render client_details_complete with new client reference" in {
      //given
      expectAuthOkOptedInReady()
      expectGetSessionItem(CLIENT_REFERENCE, "The New Name")
      val expectedClient = displayClients.head
      expectLookupClient(arn)(expectedClient)

      //when
      val result = controller.showClientReferenceUpdatedComplete(expectedClient.id)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Client reference will update shortly - Agent services account - GOV.UK"
      html
        .select(Css.confirmationPanelH1)
        .text() shouldBe "Tax reference: ending in 6780 Client reference will update shortly"
      html.select(Css.paragraphs).get(0)
        .text()shouldBe "You have asked us to update this client reference in your agent services account. We’ll update it to The New Name in the next two hours. We will not change the client reference in other HMRC online services."
    }

    s"GET showClientReferenceUpdatedComplete for invalid client id" in {
      //given
      expectAuthOkOptedInReady()
      val invalidClientId = "not found id"
      expectLookupClientNotFound(arn)(invalidClientId)

      //when
      val result = controller.showClientReferenceUpdatedComplete(invalidClientId)(request)

      //then
      status(result) shouldBe NOT_FOUND
    }

  }

}
