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
import helpers.Css.H1
import helpers.{BaseSpec, Css}
import models.DisplayClient
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.libs.json.Json
import play.api.test.Helpers.{GET, await, contentAsString, defaultAwaitTimeout, redirectLocation}
import play.api.test.FakeRequest
import repository.SessionCacheRepository
import services.{GroupService, ManageClientService}
import uk.gov.hmrc.agentmtdidentifiers.model.{Client, Enrolment, Identifier, OptedInReady}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

import java.util.Base64

class ManageClientControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector
    : AgentUserClientDetailsConnector =
    mock[AgentUserClientDetailsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]
  implicit val mockManageClientService: ManageClientService = mock[ManageClientService]

  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction])
        .toInstance(new AuthAction(mockAuthConnector, env, conf))
      bind(classOf[AgentPermissionsConnector])
        .toInstance(mockAgentPermissionsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepo)
      bind(classOf[AgentUserClientDetailsConnector])
        .toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[GroupService]).toInstance(
        new GroupService(mockAgentUserClientDetailsConnector, sessionCacheRepo, mockAgentPermissionsConnector))
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

  val encodedClientWithNoName: String = Base64.getEncoder.encodeToString(Json.toJson(displayClientWithNoFrieldyName).toString.getBytes)

  val displayClients: Seq[DisplayClient] =
    fakeClients.map(DisplayClient.fromClient(_))

  val encodedDisplayClients: Seq[String] =
    displayClients.map(client =>
      Base64.getEncoder.encodeToString(Json.toJson(client).toString.getBytes))

  val clientId: String = encodedDisplayClients.head

  val groupSummaries = Seq(
    GroupSummary("groupId", "groupName", 33, 9),
    GroupSummary("groupId-1", "groupName-1", 3, 0)
  )

  val enrolment: Enrolment = Enrolment("HMRC-MTD-VAT","Activated","friendly0",Seq(Identifier("VRN","123456780")))

  s"GET ${routes.ManageClientController.showAllClients.url}" should {

    "render the manage clients list with no query params" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)
      stubGetClientsOk(arn)(fakeClients)

      //when
      val result = controller.showAllClients()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Manage clients - Manage Agent Permissions - GOV.UK"
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
      stubOptInStatusOk(arn)(OptedInReady)
      stubGetClientsOk(arn)(fakeClients)

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

      html.title() shouldBe "Manage clients - Manage Agent Permissions - GOV.UK"
      html.select(H1).text() shouldBe "Manage clients"

      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 1
    }

    "redirect to baseUrl when CLEAR FILTER is clicked" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)
      stubGetClientsOk(arn)(fakeClients)

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
      stubOptInStatusOk(arn)(OptedInReady)
      expectGetGroupsForClientSuccess(arn, enrolment, None)

      //when
      val result = controller.showClientDetails(clientId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Client details - Manage Agent Permissions - GOV.UK"
      html.select(H1).text() shouldBe "Client details"

      html.body.text().contains("Not assigned to an access group") shouldBe true
    }

    "render the clients details page with list of groups" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)
      expectGetGroupsForClientSuccess(arn, enrolment, Some(groupSummaries))

      //when
      val result = controller.showClientDetails(clientId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Client details - Manage Agent Permissions - GOV.UK"
      html.select(H1).text() shouldBe "Client details"

      html.body.text().contains("Not assigned to an access group") shouldBe false

    }

  }

  s"GET ${routes.ManageClientController.showUpdateClientReference(clientId).url}" should {

    "render update_client_reference with existing client reference" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)
      //when
      val result = controller.showUpdateClientReference(clientId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Update client reference - Manage Agent Permissions - GOV.UK"
      html.select(H1).text() shouldBe "Update client reference"

      html.body.select("input#clientRef").attr("value") shouldBe "friendly0"
    }

    "render update_client_reference without a client reference" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)

      //when
      val result = controller.showUpdateClientReference(encodedClientWithNoName)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Update client reference - Manage Agent Permissions - GOV.UK"
      html.select(H1).text() shouldBe "Update client reference"

      html.body.select("input#clientRef").attr("value") shouldBe ""

    }

  }

  s"POST ${routes.ManageClientController.submitUpdateClientReference(clientId).url}" should {

    s"redirect to ${routes.ManageClientController.showClientReferenceUpdatedComplete(clientId)} and save client reference" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)
      //await(sessionCacheRepo.putSession(CLIENT_REFERENCE, "The New Name"))

      //when
      val result = controller.submitUpdateClientReference(clientId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

    }

    "display errors for update_client_details" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)

      //when
      val result = controller.submitUpdateClientReference(clientId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Error: Update client reference - Manage Agent Permissions - GOV.UK"
      html.select(H1).text() shouldBe "Update client reference"

    }

  }


  s"GET ${routes.ManageClientController.showClientReferenceUpdatedComplete(clientId).url}" should {

    "render client_details_complete with new client reference" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)
      await(sessionCacheRepo.putSession(CLIENT_REFERENCE, "The New Name"))

      //when
      val result = controller.showClientReferenceUpdatedComplete(clientId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Client reference updated - Manage Agent Permissions - GOV.UK"
      html
        .select(Css.confirmationPanelH1)
        .text() shouldBe "Tax reference: ending in 6780 Client reference updated to The New Name"
    }

  }

}
