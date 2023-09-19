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
import helpers.Css._
import helpers.{BaseSpec, Css}
import models.{DisplayClient, GroupId}
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{NOT_FOUND, OK, SEE_OTHER}
import play.api.test.FakeRequest
import play.api.test.Helpers.{POST, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services.{ClientService, GroupService, SessionCacheService, TaxGroupService}
import uk.gov.hmrc.agentmtdidentifiers.model.PaginationMetaData
import uk.gov.hmrc.agents.accessgroups.optin.OptedInReady
import uk.gov.hmrc.agents.accessgroups.{AgentUser, Client, CustomGroup, GroupSummary, TaxGroup}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

import java.time.LocalDate

class AssistantViewOnlyControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector
    : AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit lazy val mockAgentClientAuthConnector: AgentClientAuthorisationConnector = mock[AgentClientAuthorisationConnector]
  implicit val groupService: GroupService = mock[GroupService]
  implicit val clientService: ClientService = mock[ClientService]
  implicit val taxGroupService: TaxGroupService = mock[TaxGroupService]
  implicit val sessionCacheService: SessionCacheService = mock[SessionCacheService]

  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction])
        .toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector,mockAgentClientAuthConnector,sessionCacheService))
      bind(classOf[AgentPermissionsConnector])
        .toInstance(mockAgentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepo)
      bind(classOf[GroupService]).toInstance(groupService)
      bind(classOf[TaxGroupService]).toInstance(taxGroupService)
      bind(classOf[ClientService]).toInstance(clientService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val fakeClients: Seq[Client] =
    List.tabulate(31)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))

  val displayClients: Seq[DisplayClient] =
    fakeClients.map(DisplayClient.fromClient(_))

  val groupSummaries: Seq[GroupSummary] = (1 to 3).map(i =>
    GroupSummary(GroupId.random(), s"name $i", Some(i * 3), i * 4))

  private val agentUser: AgentUser =
    AgentUser(RandomStringUtils.random(5), "Rob the Agent")

 val accessGroup: CustomGroup =
    CustomGroup(GroupId.random(),
    arn,
    "Bananas",
    LocalDate.of(2020, 3, 10).atStartOfDay(),
    null,
    agentUser,
    agentUser,
    Set.empty,
    fakeClients.toSet
    )

  val taxGroup: TaxGroup = TaxGroup(
    GroupId.random(),
    arn,
    "VAT is Bananas",
    LocalDate.of(2020, 3, 10).atStartOfDay(),
    LocalDate.of(2020, 3, 10).atStartOfDay(),
    agentUser,
    agentUser,
    Set.empty,
    "HMRC-MTD-VAT",
    automaticUpdates = true,
    Set.empty)


  val controller: AssistantViewOnlyController = fakeApplication.injector.instanceOf[AssistantViewOnlyController]
  private val ctrlRoute: ReverseAssistantViewOnlyController = routes.AssistantViewOnlyController

  def AssistantAuthOk(): Unit = {
    expectAuthorisationGrantsAccess(mockedAssistantAuthResponse)
    expectIsArnAllowed(allowed = true)
    expectGetSessionItem(SUSPENSION_STATUS, false)
    expectOptInStatusOk(arn)(OptedInReady)
  }

  s"GET ${ctrlRoute.showUnassignedClientsViewOnly().url}" should {

    "render unassigned clients list with no query params" in {
      // given
      AssistantAuthOk()
      expectGetUnassignedClients(arn)(displayClients)

      //when
      val result = controller.showUnassignedClientsViewOnly()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Other clients you can manage - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Other clients you can manage"
      html.select(backLink).attr("href") shouldBe "#"
      html.select(backLink).text() shouldBe "Back"
      html.select(paragraphs).get(0).text() shouldBe "You can manage these clients’ tax as they are not in any access groups."
      html.select("main p#pagination-showing").text() shouldBe "Showing 1 to 20 of 31 clients"
      val th = html.select(Css.tableWithId("clients")).select("thead th")
      th.size() shouldBe 3
      val tr = html.select(Css.tableWithId("clients")).select("tbody tr")
      tr.size() shouldBe 20

    }

    "render the unassigned clients list with search params" in {
      //given
      AssistantAuthOk()
      expectGetUnassignedClients(arn)(displayClients, search = Some("friendly1"))

      sessionCacheRepo.putSession(CLIENT_SEARCH_INPUT, "friendly1").futureValue

      //when
      val result = controller.showUnassignedClientsViewOnly()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Filter results for ‘friendly1’ Other clients you can manage - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Other clients you can manage"
      html.select(Css.backLink).attr("href") shouldBe "#"
      html.select(Css.backLink).text() shouldBe "Back"

      html.select(H2).text shouldBe "Filter results for ‘friendly1’"

      val trs = html.select(Css.tableWithId("clients")).select("tbody tr")
      trs.size() shouldBe 11
    }

    "render with filter that matches nothing" in {
      //given
      AssistantAuthOk()

      val NON_MATCHING_FILTER = "HMRC-CGT-PD" //there are none of these HMRC-CGT-PD in the setup clients. so expect no results back

      expectGetUnassignedClients(arn)(displayClients, search = Some("friendly1"), filter = Some(NON_MATCHING_FILTER))

      sessionCacheRepo.putSession(CLIENT_SEARCH_INPUT, "friendly1").futureValue
      sessionCacheRepo.putSession(CLIENT_FILTER_INPUT, NON_MATCHING_FILTER).futureValue

      //when
      val result = controller.showUnassignedClientsViewOnly()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Filter results for ‘friendly1’ and ‘Capital Gains Tax on UK Property account’ Other clients you can manage - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Other clients you can manage"
      html.select(backLink).attr("href") shouldBe "#"
      html.select(backLink).text() shouldBe "Back"

      html.select(H2).text shouldBe "No clients found"

      val th = html.select(Css.tableWithId("clients")).select("thead th")
      th.size() shouldBe 0
      val trs = html.select(Css.tableWithId("clients")).select("tbody tr")
      trs.size() shouldBe 0
    }
  }

  s"POST ${ctrlRoute.submitUnassignedClientsViewOnly().url}" should {

    "save search/filter terms and redirect to 1st page when ‘filter’ is clicked" in {
      //given
      AssistantAuthOk()

      val requestWithFormBody = FakeRequest(POST,
        ctrlRoute.submitUnassignedClientsViewOnly().url)
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody("submit" -> "filter", "search" -> "friendly1", "filter" -> "HMRC-MTD-IT")

      //when
      val result = controller.submitUnassignedClientsViewOnly()(requestWithFormBody)

      //then
      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(ctrlRoute.showUnassignedClientsViewOnly(None).url)

      sessionCacheRepo.getFromSession(CLIENT_SEARCH_INPUT).futureValue shouldBe Some("friendly1")
      sessionCacheRepo.getFromSession(CLIENT_FILTER_INPUT).futureValue shouldBe Some("HMRC-MTD-IT")
    }

    "clear filters from cache and redirect to base URL when ‘clear’ is clicked" in {
      //given
      AssistantAuthOk()

      val requestWithFormBody = FakeRequest(POST,
        ctrlRoute.submitUnassignedClientsViewOnly().url)
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody("submit" -> "clear", "search" -> "friendly1", "filter" -> "HMRC-MTD-IT")

      sessionCacheRepo.putSession(CLIENT_SEARCH_INPUT, "friendly1").futureValue
      sessionCacheRepo.putSession(CLIENT_FILTER_INPUT, "HMRC-MTD-IT").futureValue

      //when
      val result = controller.submitUnassignedClientsViewOnly()(requestWithFormBody)

      //then
      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(ctrlRoute.showUnassignedClientsViewOnly(None).url)

      // check that filter values have been removed
      sessionCacheRepo.getFromSession(CLIENT_SEARCH_INPUT).futureValue shouldBe None
      sessionCacheRepo.getFromSession(CLIENT_FILTER_INPUT).futureValue shouldBe None
    }

  }

  s"GET ${ctrlRoute.showExistingGroupClientsViewOnly(accessGroup.id).url}" should {

    s"render group ${accessGroup.groupName} clients list with no query params" in {
      // given
      val pageSize = 20
      AssistantAuthOk()
      expectGetGroupById(accessGroup.id, Some(accessGroup))
      val paginationData = PaginationMetaData(lastPage = false, firstPage = true, displayClients.size, 3, 20, 1, 20)
      expectGetPaginatedClientsForCustomGroup(accessGroup.id)(1, pageSize)((displayClients.take(pageSize), paginationData))

      //when
      val result = controller.showExistingGroupClientsViewOnly(accessGroup.id)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe s"${accessGroup.groupName} clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe s"${accessGroup.groupName}"
      html.select("main p#pagination-showing").text() shouldBe "Showing 1 to 20 of 31 clients"
      html.select(backLink).attr("href") shouldBe "#"
      html.select(backLink).text() shouldBe "Back"

      val th = html.select(Css.tableWithId("clients")).select("thead th")
      th.size() shouldBe 3
      val tr = html.select(Css.tableWithId("clients")).select("tbody tr")
      tr.size() shouldBe 20

    }

    "render with filled form when search/filter terms are in session" in {
      //given
      AssistantAuthOk()
      expectGetGroupById(accessGroup.id, Some(accessGroup))
      sessionCacheRepo.putSession(CLIENT_SEARCH_INPUT, "friendly1").futureValue
      expectGetPaginatedClientsForCustomGroup(accessGroup.id)(1, 20)((displayClients.take(1),PaginationMetaData(lastPage = true,firstPage = true,0,1,10,1,10)))

      //when
      val result = controller.showExistingGroupClientsViewOnly(accessGroup.id)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Filter results for ‘friendly1’ ${accessGroup.groupName} clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe s"${accessGroup.groupName}"
      html.select(backLink).attr("href") shouldBe "#"
      html.select(backLink).text() shouldBe "Back"

      html.select(H2).text shouldBe "Filter results for ‘friendly1’"


      val th = html.select(Css.tableWithId("clients")).select("thead th")
      th.size() shouldBe 3

      val trs = html.select(Css.tableWithId("clients")).select("tbody tr")
      trs.size() shouldBe 1
    }

    "render with filter that matches nothing" in {
      //given
      AssistantAuthOk()
      expectGetGroupById(accessGroup.id, Some(accessGroup))

      //there are none of these HMRC-CGT-PD in the setup clients. so expect no results back
      val NON_MATCHING_FILTER = "HMRC-CGT-PD"

      sessionCacheRepo.putSession(CLIENT_SEARCH_INPUT, "friendly1").futureValue
      sessionCacheRepo.putSession(CLIENT_FILTER_INPUT, NON_MATCHING_FILTER).futureValue

      expectGetPaginatedClientsForCustomGroup(accessGroup.id)(1, 20)((Seq.empty,PaginationMetaData(lastPage = true,firstPage = true,0,1,10,1,10)))

      //when
      val result = controller.showExistingGroupClientsViewOnly(accessGroup.id)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe s"Filter results for ‘friendly1’ and ‘Capital Gains Tax on UK Property account’ ${accessGroup.groupName} clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe s"${accessGroup.groupName}"

      html.select(H2).text shouldBe "No clients found"

      val th = html.select(Css.tableWithId("clients")).select("thead th")
      th.size() shouldBe 0
      val trs = html.select(Css.tableWithId("clients")).select("tbody tr")
      trs.size() shouldBe 0
    }

    "render NOT_FOUND when no group is found for this group id" in {
      //given
      AssistantAuthOk()
      expectGetGroupById(accessGroup.id, None)

      //when
      val result = controller.showExistingGroupClientsViewOnly(accessGroup.id)(request)

      status(result) shouldBe NOT_FOUND
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Access group not found - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Access group not found"
      html
        .select(Css.paragraphs)
        .text() shouldBe "Please check the url or return to Your account"
      html
        .select(Css.linkStyledAsButton)
        .text() shouldBe "Back to your account"
      html
        .select(Css.linkStyledAsButton)
        .attr("href") shouldBe "http://localhost:9401/agent-services-account/your-account"
    }
  }

  s"POST ${ctrlRoute.submitExistingGroupClientsViewOnly(accessGroup.id).url}" should {

    "save search/filter terms and redirect to 1st page when ‘filter’ is clicked" in {
      //given
      AssistantAuthOk()

      val requestWithFormBody = FakeRequest(POST,
        ctrlRoute.submitExistingGroupClientsViewOnly(accessGroup.id).url)
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody("submit" -> "filter", "search" -> "friendly1", "filter" -> "HMRC-MTD-IT")

      //when
      val result = controller.submitExistingGroupClientsViewOnly(accessGroup.id)(requestWithFormBody)

      //then
      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(ctrlRoute.showExistingGroupClientsViewOnly(accessGroup.id,None).url)

      sessionCacheRepo.getFromSession(CLIENT_SEARCH_INPUT).futureValue shouldBe Some("friendly1")
      sessionCacheRepo.getFromSession(CLIENT_FILTER_INPUT).futureValue shouldBe Some("HMRC-MTD-IT")
    }

    "clear filters from cache and redirect to base URL when ‘clear’ is clicked" in {
      //given
      AssistantAuthOk()

      val requestWithFormBody = FakeRequest(POST,
        ctrlRoute.submitExistingGroupClientsViewOnly(accessGroup.id).url)
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody("submit" -> "clear", "search" -> "friendly1", "filter" -> "HMRC-MTD-IT")

      sessionCacheRepo.putSession(CLIENT_SEARCH_INPUT, "friendly1").futureValue
      sessionCacheRepo.putSession(CLIENT_FILTER_INPUT, "HMRC-MTD-IT").futureValue

      //when
      val result = controller.submitExistingGroupClientsViewOnly(accessGroup.id)(requestWithFormBody)

      //then
      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(ctrlRoute.showExistingGroupClientsViewOnly(accessGroup.id,None).url)

      // check that filter values have been removed
      sessionCacheRepo.getFromSession(CLIENT_SEARCH_INPUT).futureValue shouldBe None
      sessionCacheRepo.getFromSession(CLIENT_FILTER_INPUT).futureValue shouldBe None
    }

  }


  s"GET ${ctrlRoute.showExistingTaxClientsViewOnly(taxGroup.id).url}" should {

    s"render group ${taxGroup.groupName} clients list with no query params" in {
      // given
      AssistantAuthOk()
      expectGetTaxGroupById(taxGroup.id, Some(taxGroup))
      expectGetPageOfClients(taxGroup.arn)(displayClients.take(10))

      //when
      val result = controller.showExistingTaxClientsViewOnly(taxGroup.id)(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe s"${taxGroup.groupName} clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe s"${taxGroup.groupName}"

      html.select(Css.backLink).attr("href") shouldBe "#"
      html.select(Css.backLink).text() shouldBe "Back"

      html.select(H2).size shouldBe 0

      // no filter by tax service on the form
      html.select(Css.labelFor("search")).text() shouldBe "Search by tax reference or client reference"
      html.select(Css.labelFor("filter")).size() shouldBe 0

      // table without tax service column
      val th = html.select(Css.tableWithId("clients")).select("thead th")
      th.size() shouldBe 2
      val tr = html.select(Css.tableWithId("clients")).select("tbody tr")
      tr.size() shouldBe 10


    }

    s"render group ${taxGroup.groupName} clients list with search params" in {
      //given
      AssistantAuthOk()
      expectGetTaxGroupById(taxGroup.id, Some(taxGroup))

      // ? expectPutSessionItem(CLIENT_FILTER_INPUT, taxGroup.service)
      //expectPutSessionItem(CLIENT_SEARCH_INPUT, "friendly1")
      sessionCacheRepo.putSession(CLIENT_SEARCH_INPUT, "friendly1").futureValue

      expectGetPageOfClients(taxGroup.arn)(displayClients.take(1))

      //when
      val result = controller.showExistingTaxClientsViewOnly(taxGroup.id)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // title should not mention tax service filter
      html.title() shouldBe s"Filter results for ‘friendly1’ ${taxGroup.groupName} clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe s"${taxGroup.groupName}"
      html.select(Css.backLink).attr("href") shouldBe "#"
      html.select(Css.backLink).text() shouldBe "Back"

      html.select(H2).text shouldBe "Filter results for ‘friendly1’"

      // no filter by tax service on the form
      html.select(Css.labelFor("search")).text() shouldBe "Search by tax reference or client reference"
      html.select("#search").attr("value") shouldBe "friendly1"
      html.select(Css.labelFor("filter")).size() shouldBe 0

      // table without tax service column
      val th = html.select(Css.tableWithId("clients")).select("thead th")
      th.size() shouldBe 2
      val trs = html.select(Css.tableWithId("clients")).select("tbody tr")
      trs.size() shouldBe 1
    }

    "render with search that matches nothing" in {
      //given
      AssistantAuthOk()
      expectGetTaxGroupById(taxGroup.id, Some(taxGroup))

      //expectPutSessionItem(CLIENT_FILTER_INPUT, taxGroup.service)
      sessionCacheRepo.putSession(CLIENT_SEARCH_INPUT, "nothing").futureValue //not matching any setup clients
      //expectPutSessionItem(CLIENT_SEARCH_INPUT, "nothing") //not matching any setup clients

      expectGetPageOfClients(taxGroup.arn)(Seq.empty[DisplayClient])

      //when
      val result = controller.showExistingTaxClientsViewOnly(taxGroup.id)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      // title should not mention tax service filter
      html.title() shouldBe s"Filter results for ‘nothing’ ${taxGroup.groupName} clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe s"${taxGroup.groupName}"

      html.select(H2).text shouldBe "No clients found"

      // no filter by tax service on the form
      html.select(Css.labelFor("search")).text() shouldBe "Search by tax reference or client reference"
      html.select(Css.labelFor("filter")).size() shouldBe 0

      // no table
      val th = html.select(Css.tableWithId("clients")).select("thead th")
      th.size() shouldBe 0
      val trs = html.select(Css.tableWithId("clients")).select("tbody tr")
      trs.size() shouldBe 0
    }
  }

  s"POST ${ctrlRoute.submitExistingTaxClientsViewOnly(taxGroup.id).url}" should {

    "save search ONLY and redirect to 1st page when ‘filter’ is clicked" in {
      //given
      AssistantAuthOk()
      sessionCacheRepo.putSession(CLIENT_FILTER_INPUT, "HMRC-MTD-IT").futureValue

      //expectPutSessionItem(CLIENT_SEARCH_INPUT, "friendly1")

      val requestWithFormBody = FakeRequest(POST,
        ctrlRoute.submitExistingTaxClientsViewOnly(taxGroup.id).url)
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody("submit" -> "filter", "search" -> "friendly1")

      //when
      val result = controller.submitExistingTaxClientsViewOnly(taxGroup.id)(requestWithFormBody)

      //then
      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(ctrlRoute.showExistingTaxClientsViewOnly(taxGroup.id,None).url)

      sessionCacheRepo.getFromSession(CLIENT_SEARCH_INPUT).futureValue shouldBe Some("friendly1")
      sessionCacheRepo.getFromSession(CLIENT_FILTER_INPUT).futureValue shouldBe Some("HMRC-MTD-IT") // unchanged
    }

    "clear search ONLY from cache and redirect to base URL when ‘clear’ is clicked" in {
      //given
      AssistantAuthOk()
      sessionCacheRepo.putSession(CLIENT_SEARCH_INPUT, "friendly1").futureValue
      sessionCacheRepo.putSession(CLIENT_FILTER_INPUT, "HMRC-MTD-IT").futureValue

      //expectDeleteSessionItem(CLIENT_SEARCH_INPUT)

      val requestWithFormBody = FakeRequest(POST,
        ctrlRoute.submitExistingTaxClientsViewOnly(taxGroup.id).url)
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody("submit" -> "clear", "search" -> "friendly1")

      //when
      val result = controller.submitExistingTaxClientsViewOnly(taxGroup.id)(requestWithFormBody)

      //then
      status(result) shouldBe SEE_OTHER

      redirectLocation(result) shouldBe Some(ctrlRoute.showExistingTaxClientsViewOnly(taxGroup.id,None).url)

      // check that filter values have been removed
      sessionCacheRepo.getFromSession(CLIENT_SEARCH_INPUT).futureValue shouldBe None
      sessionCacheRepo.getFromSession(CLIENT_FILTER_INPUT).futureValue shouldBe Some("HMRC-MTD-IT") // unchanged
    }

  }


}
