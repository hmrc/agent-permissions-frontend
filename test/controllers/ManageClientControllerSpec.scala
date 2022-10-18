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
import helpers.Css.{H1, H2}
import helpers.{BaseSpec, Css}
import models.DisplayClient
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, await, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services.{ClientService, GroupService, GroupServiceImpl}
import uk.gov.hmrc.agentmtdidentifiers.model.{Client, OptedInReady}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys


class ManageClientControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector
    : AgentUserClientDetailsConnector =
    mock[AgentUserClientDetailsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]
  implicit val mockManageClientService: ClientService = mock[ClientService]

  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

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

  val controller: ManageClientController =
    fakeApplication.injector.instanceOf[ManageClientController]

  val fakeClients: Seq[Client] =
    List.tabulate(3)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))

  val fakeClientWithNoFriendlyName: Client = Client(s"HMRC-MTD-VAT~VRN~123456789", "")

  val displayClientWithNoFrieldyName: DisplayClient = DisplayClient.fromClient(fakeClientWithNoFriendlyName)

  val clientWithoutNameId: String = displayClientWithNoFrieldyName.id

  val displayClients: Seq[DisplayClient] =
    fakeClients.map(DisplayClient.fromClient(_))

  val displayClientsIds: Seq[String] =
    displayClients.map(client =>
      client.id)

  val clientId: String = displayClientsIds.head

  val groupSummaries = Seq(
    GroupSummary("groupId", "groupName", 33, 9),
    GroupSummary("groupId-1", "groupName-1", 3, 0)
  )

  val enrolmentKey: String = "HMRC-MTD-VAT~VRN~123456780"

  s"GET ${routes.ManageClientController.showAllClients.url}" should {

    "render the manage clients list with no query params" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectOptInStatusOk(arn)(OptedInReady)
      expectGetClients(arn)(fakeClients)

      //when
      val result = controller.showAllClients()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Manage clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage clients"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 4
      th.get(0).text() shouldBe "Client reference"
      th.get(1).text() shouldBe "Tax reference"
      th.get(2).text() shouldBe "Tax service"
      th.get(3).text() shouldBe "Actions"

      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")

      trs.size() shouldBe 3
    }

    "render the manage clients list with search params" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectOptInStatusOk(arn)(OptedInReady)
      expectGetClients(arn)(fakeClients)

      implicit val requestWithQueryParams = FakeRequest(GET,
        routes.ManageClientController.showAllClients.url +
          "?submit=filter&search=friendly1&filter="
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showAllClients()(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Filter results for 'friendly1' Manage clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage clients"
      html.select(H2).text() shouldBe "Filter results for 'friendly1'"

      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 1
    }

    "render with filter that matches nothing" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectOptInStatusOk(arn)(OptedInReady)
      expectGetClients(arn)(fakeClients)

      //there are none of these HMRC-CGT-PD in the setup clients. so expect no results back
      val NON_MATCHING_FILTER = "HMRC-CGT-PD"
      implicit val requestWithQueryParams = FakeRequest(GET,
        routes.ManageTeamMemberController.showAllTeamMembers.url +
          s"?submit=filter&search=friendly1&filter=$NON_MATCHING_FILTER"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showAllClients()(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Filter results for 'friendly1' and 'Capital Gains Tax on UK Property account' Manage clients - Agent services account - GOV.UK"
      html.select(Css.H1).text shouldBe "Manage clients"
      html.select(H2).text shouldBe "No clients found"

      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 0
    }

    "redirect to baseUrl when CLEAR FILTER is clicked" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectOptInStatusOk(arn)(OptedInReady)
      expectGetClients(arn)(fakeClients)

      //and we have CLEAR filter in query params
      implicit val requestWithQueryParams = FakeRequest(GET,
        routes.ManageClientController.showAllClients.url +
          s"?submit=clear"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showAllClients()(requestWithQueryParams)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get
        .shouldBe(routes.ManageClientController.showAllClients.url)
    }

  }

  s"GET ${routes.ManageClientController.showClientDetails(clientId).url}" should {

    "render the clients details page with NO GROUPS" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectOptInStatusOk(arn)(OptedInReady)
      expectGetGroupsForClientSuccess(arn, enrolmentKey, None)
      expectGetClients(arn)(fakeClients)

      //when
      val result = controller.showClientDetails(clientId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Client details - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Client details"

      html.body.text().contains("Not assigned to an access group") shouldBe true
    }

    "render the clients details page with list of groups" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectOptInStatusOk(arn)(OptedInReady)
      expectGetGroupsForClientSuccess(arn, enrolmentKey, Some(groupSummaries))
      expectGetClients(arn)(fakeClients)

      //when
      val result = controller.showClientDetails(clientId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Client details - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Client details"

      html.body.text().contains("Not assigned to an access group") shouldBe false

    }

  }

  s"GET ${routes.ManageClientController.showUpdateClientReference(clientId).url}" should {

    "render update_client_reference with existing client reference" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectOptInStatusOk(arn)(OptedInReady)
      expectGetClients(arn)(fakeClients)
      //when
      val result = controller.showUpdateClientReference(clientId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Update client reference - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Update client reference"

      html.body.select("input#clientRef").attr("value") shouldBe "friendly0"
    }

    "render update_client_reference without a client reference" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectOptInStatusOk(arn)(OptedInReady)

      val fakeClientWithoutFriendlyName = fakeClients.head.copy(friendlyName = "")

      expectGetClients(arn)(Seq(fakeClientWithoutFriendlyName))

      //when
      val result = controller.showUpdateClientReference(DisplayClient.fromClient(fakeClientWithoutFriendlyName).id)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Update client reference - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Update client reference"

      html.body.select("input#clientRef").attr("value") shouldBe ""
    }

  }

  s"POST ${routes.ManageClientController.submitUpdateClientReference(clientId).url}" should {

    s"redirect to ${routes.ManageClientController.showClientReferenceUpdatedComplete(clientId)} and save client reference" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectOptInStatusOk(arn)(OptedInReady)
      //await(sessionCacheRepo.putSession(CLIENT_REFERENCE, "The New Name"))
      expectGetClients(arn)(fakeClients)

      //when
      val result = controller.submitUpdateClientReference(clientId)(request)

      //then
      status(result) shouldBe OK

    }

    "display errors for update_client_details" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectOptInStatusOk(arn)(OptedInReady)
      expectGetClients(arn)(fakeClients)

      //when
      val result = controller.submitUpdateClientReference(clientId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Error: Update client reference - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Update client reference"
    }
  }

  s"GET ${routes.ManageClientController.showClientReferenceUpdatedComplete(clientId).url}" should {

    "render client_details_complete with new client reference" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectOptInStatusOk(arn)(OptedInReady)
      await(sessionCacheRepo.putSession(CLIENT_REFERENCE, "The New Name"))
      expectGetClients(arn)(fakeClients)

      //when
      val result = controller.showClientReferenceUpdatedComplete(clientId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Client reference updated - Agent services account - GOV.UK"
      html
        .select(Css.confirmationPanelH1)
        .text() shouldBe "Tax reference: ending in 6780 Client reference updated to The New Name"
    }

  }

}
