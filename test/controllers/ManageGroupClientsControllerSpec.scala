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
import connectors.{AddMembersToAccessGroupRequest, AgentAssuranceConnector, AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.actions.AuthAction
import helpers.Css._
import helpers.{BaseSpec, Css}
import models.{AddClientsToGroup, DisplayClient, GroupId, TeamMember}
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services._
import models.PaginationMetaData
import models.accessgroups.optin.OptedInReady
import models.accessgroups.{AgentUser, Client, CustomGroup, GroupSummary, TaxGroup, UserDetails}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

import java.time.LocalDate
import java.time.LocalDateTime.MIN
import java.util.Base64

class ManageGroupClientsControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector =
    mock[AgentUserClientDetailsConnector]
  implicit lazy val mockAgentAssuranceConnector: AgentAssuranceConnector =
    mock[AgentAssuranceConnector]
  implicit val mockSessionCacheService: SessionCacheService = mock[SessionCacheService]
  implicit val mockSessionCacheOps: SessionCacheOperationsService = mock[SessionCacheOperationsService]
  implicit lazy val mockGroupService: GroupService = mock[GroupService]
  implicit lazy val mockTaxGroupService: TaxGroupService = mock[TaxGroupService]
  implicit lazy val mockClientService: ClientService = mock[ClientService]
  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

  private val agentUser: AgentUser = AgentUser(RandomStringUtils.random(5), "Rob the Agent")
  val accessGroup: CustomGroup = CustomGroup(
    GroupId.random(),
    arn,
    "Bananas",
    LocalDate.of(2020, 3, 10).atStartOfDay(),
    null,
    agentUser,
    agentUser,
    Set.empty,
    Set.empty
  )

  val taxGroup: TaxGroup = TaxGroup(
    GroupId.random(),
    arn,
    "Bananas",
    MIN,
    MIN,
    agentUser,
    agentUser,
    Set.empty,
    "HMRC-MTD-VAT",
    automaticUpdates = true,
    Set.empty
  )

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(
        new AuthAction(
          mockAuthConnector,
          env,
          conf,
          mockAgentPermissionsConnector,
          mockAgentAssuranceConnector,
          mockSessionCacheService
        )
      )
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[SessionCacheService]).toInstance(mockSessionCacheService)
      bind(classOf[SessionCacheOperationsService]).toInstance(mockSessionCacheOps)
      bind(classOf[GroupService]).toInstance(mockGroupService)
      bind(classOf[ClientService]).toInstance(mockClientService)
      bind(classOf[TaxGroupService]).toInstance(mockTaxGroupService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder.configure("mongodb.uri" -> mongoUri).build()

  val fakeClients: Seq[Client] =
    List.tabulate(3)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))

  val displayClients: Seq[DisplayClient] = fakeClients.map(DisplayClient.fromClient(_))

  val encodedDisplayClients: Seq[String] =
    displayClients.map(client => Base64.getEncoder.encodeToString(Json.toJson(client).toString.getBytes))

  val agentUsers: Set[AgentUser] = (1 to 5).map(i => AgentUser(id = s"John $i", name = s"John $i name")).toSet

  val userDetails: Seq[UserDetails] = (1 to 5)
    .map { i =>
      UserDetails(Some(s"John $i"), Some("User"), Some(s"John $i name"), Some(s"john$i@abc.com"))
    }

  val teamMembers: Seq[TeamMember] = userDetails.map(TeamMember.fromUserDetails)

  val controller: ManageGroupClientsController = fakeApplication.injector.instanceOf[ManageGroupClientsController]
  private val ctrlRoute: ReverseManageGroupClientsController = routes.ManageGroupClientsController
  private val grpId: GroupId = accessGroup.id

  def expectAuthOkOptedInReady(): Unit = {
    expectAuthorisationGrantsAccess(mockedAuthResponse)
    expectIsArnAllowed(allowed = true)
    expectGetSessionItem(SUSPENSION_STATUS, false)
    expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
  }

  s"GET ${ctrlRoute.showExistingGroupClients(grpId, None, None).url}" should {

    "render correctly the first page of EXISTING CLIENTS page with no query params" in {
      // given
      val groupWithClients =
        accessGroup.copy(clients = displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet)
      val summary = GroupSummary.of(groupWithClients)

      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))

      expectGetPaginatedClientsForCustomGroup(grpId)(1, 20)(
        (displayClients, PaginationMetaData(lastPage = true, firstPage = true, 0, 1, 10, 1, 10))
      )

      // when
      val result = controller.showExistingGroupClients(groupWithClients.id, None, None)(request)

      // then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Manage clients - Bananas - Agent services account - GOV.UK"
      html.select(Css.backLink).text() shouldBe "Back to manage groups page"
      html.select(Css.backLink).attr("href") shouldBe "/agent-permissions/manage-access-groups"
      html.select(Css.PRE_H1).text shouldBe "This access group is Bananas"
      html.select(Css.H1).text shouldBe "Manage clients in this group"

      val th = html.select(Css.tableWithId("clients")).select("thead th")
      th.size() shouldBe 4
      th.get(0).text() shouldBe "Client reference"
      th.get(1).text() shouldBe "Tax reference"
      th.get(2).text() shouldBe "Tax service"
      th.get(3).text() shouldBe "Actions"

      val trs = html.select(Css.tableWithId("clients")).select("tbody tr")

      trs.size() shouldBe 3
      // first row
      trs.get(0).select("td").get(0).text() shouldBe "friendly0"
      trs.get(0).select("td").get(1).text() shouldBe "ending in 6780"
      trs.get(0).select("td").get(2).text() shouldBe "VAT"
      val removeClient1 = trs.get(0).select("td").get(3).select("a")
      removeClient1.text() shouldBe "Remove friendly0"
      removeClient1.attr("href") shouldBe ctrlRoute.showConfirmRemoveClient(grpId, displayClients.head.id).url

      // last row
      trs.get(2).select("td").get(0).text() shouldBe "friendly2"
      trs.get(2).select("td").get(1).text() shouldBe "ending in 6782"
      trs.get(2).select("td").get(2).text() shouldBe "VAT"
      val removeClient2 = trs.get(2).select("td").get(3).select("a")
      removeClient2.text() shouldBe "Remove friendly2"
      removeClient2.attr("href") shouldBe ctrlRoute.showConfirmRemoveClient(grpId, displayClients(2).id).url

      html.select("a#update-clients").text() shouldBe "Add more clients"
      html.select("a#update-clients").attr("href") shouldBe
        ctrlRoute.showSearchClientsToAdd(grpId).url
    }

    "render with filter & searchTerm set" in {
      // given
      val groupWithClients =
        accessGroup.copy(clients = displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet)
      val summary = GroupSummary.of(groupWithClients)

      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))

      expectPutSessionItem(CLIENT_SEARCH_INPUT, "friendly1")
      expectPutSessionItem(CLIENT_FILTER_INPUT, "HMRC-MTD-VAT")
      expectGetPaginatedClientsForCustomGroup(grpId)(1, 20)(
        (displayClients.take(1), PaginationMetaData(lastPage = true, firstPage = true, 1, 1, 10, 1, 10))
      )

      implicit val requestWithQueryParams: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(
        GET,
        ctrlRoute.showExistingGroupClients(groupWithClients.id, None, None).url +
          "?submit=filter&search=friendly1&filter=HMRC-MTD-VAT"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      // when
      val result =
        controller.showExistingGroupClients(groupWithClients.id, None, None)(requestWithQueryParams)

      // then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Filter results for ‘friendly1’ and ‘VAT’ Manage clients - Bananas - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "This access group is Bananas"
      html.select(Css.H1).text shouldBe "Manage clients in this group"
      html
        .select("#filter-description")
        .text shouldBe "Showing total of 1 clients for ‘friendly1’ and ‘VAT’ in this group"

      val clientsTable = html.select(Css.tableWithId("clients"))
      val th = clientsTable.select("thead th")
      val trs = clientsTable.select("tbody tr")
      trs.size() shouldBe 1
      th.size() shouldBe 4
    }

    "render with filter that matches nothing" in {
      // given
      val groupWithClients =
        accessGroup.copy(clients = displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet)
      val summary = GroupSummary.of(groupWithClients)

      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))

      expectPutSessionItem(CLIENT_SEARCH_INPUT, "friendly1")
      expectPutSessionItem(CLIENT_FILTER_INPUT, "HMRC-CGT-PD")
      expectGetPaginatedClientsForCustomGroup(grpId)(1, 20)(
        (Seq.empty[DisplayClient], PaginationMetaData(lastPage = true, firstPage = true, 0, 1, 10, 1, 10))
      )

      // there are none of these HMRC-CGT-PD in the setup clients. so expect no results back
      val NON_MATCHING_FILTER = "HMRC-CGT-PD"
      implicit val requestWithQueryParams: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(
        GET,
        ctrlRoute.showExistingGroupClients(groupWithClients.id, None, None).url +
          s"?submit=filter&search=friendly1&filter=$NON_MATCHING_FILTER"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      // when
      val result = controller.showExistingGroupClients(groupWithClients.id, None, None)(requestWithQueryParams)

      // then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Filter results for ‘friendly1’ and ‘Capital Gains Tax on UK Property account’ Manage clients - Bananas - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "This access group is Bananas"
      html.select(Css.H1).text shouldBe "Manage clients in this group"

      val tableOfClients = html.select(Css.tableWithId("clients"))
      tableOfClients.isEmpty shouldBe true
      val noClientsFound = html.select("div#clients")
      noClientsFound.isEmpty shouldBe false
      noClientsFound.select("h2").text shouldBe "No clients found"
      noClientsFound
        .select("p")
        .text shouldBe "Update your filters and try again or clear your filters to see all your clients"
    }

    "redirect to baseUrl when CLEAR FILTER is clicked" in {
      // given
      val groupWithClients =
        accessGroup.copy(clients = displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet)
      val summary = GroupSummary.of(groupWithClients)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectDeleteSessionItems(clientFilteringKeys)

      // and we have CLEAR filter in query params
      implicit val requestWithQueryParams: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(
        GET,
        ctrlRoute.showExistingGroupClients(groupWithClients.id, None, None).url +
          s"?submit=clear"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      // when
      val result =
        controller.showExistingGroupClients(groupWithClients.id, None, None)(requestWithQueryParams)

      // then
      redirectLocation(result).get
        .shouldBe(ctrlRoute.showExistingGroupClients(groupWithClients.id, Some(1), Some(20)).url)
    }

    "redirect to new page when a pagination button is clicked" in {
      // given
      val groupWithClients =
        accessGroup.copy(clients = displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet)
      val summary = GroupSummary.of(groupWithClients)

      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))

      val pageNumber = 2
      // and we have PAGINATION_BUTTON filter in query params
      implicit val requestWithQueryParams: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest(
          GET,
          ctrlRoute.showExistingGroupClients(groupWithClients.id, None, None).url +
            s"?submit=${PAGINATION_BUTTON}_$pageNumber"
        )
          .withHeaders("Authorization" -> "Bearer XYZ")
          .withSession(SessionKeys.sessionId -> "session-x")

      // when
      val result =
        controller.showExistingGroupClients(groupWithClients.id, None, None)(requestWithQueryParams)

      // then
      redirectLocation(result).get
        .shouldBe(ctrlRoute.showExistingGroupClients(groupWithClients.id, Some(pageNumber), Some(20)).url)
    }

    "Not render remove link when only 1 client in group" in {
      // given
      val groupWithClients =
        accessGroup.copy(clients = displayClients.take(1).map(dc => Client(dc.enrolmentKey, dc.name)).toSet)
      val summary = GroupSummary.of(groupWithClients)

      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectGetPaginatedClientsForCustomGroup(grpId)(1, 20)(
        (displayClients.take(1), PaginationMetaData(lastPage = true, firstPage = true, 0, 1, 10, 1, 10))
      )

      implicit val requestWithQueryParams: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest(GET, ctrlRoute.showExistingGroupClients(groupWithClients.id, None, None).url)
          .withHeaders("Authorization" -> "Bearer XYZ")
          .withSession(SessionKeys.sessionId -> "session-x")

      // when
      val result =
        controller.showExistingGroupClients(groupWithClients.id, None, None)(requestWithQueryParams)

      // then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Manage clients - Bananas - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "This access group is Bananas"
      html.select(Css.H1).text shouldBe "Manage clients in this group"

      val clientsTable = html.select(Css.tableWithId("clients"))
      val th = clientsTable.select("thead th")
      val trs = clientsTable.select("tbody tr")
      trs.size() shouldBe 1
      th.size() shouldBe 3 // <-- only 1 client in group so can't remove
    }

  }

  s"GET ${ctrlRoute.showSearchClientsToAdd(grpId).url}" should {
    "render the client search page" in {
      val summary = GroupSummary.of(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))

      expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)

      val result = controller.showSearchClientsToAdd(grpId)(request)
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
      val summary = GroupSummary.of(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectGetSessionItem(CLIENT_SEARCH_INPUT, "Harry")
      expectGetSessionItem(CLIENT_FILTER_INPUT, "HMRC-MTD-VAT")

      val result = controller.showSearchClientsToAdd(grpId)(request)
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

  s"POST ${ctrlRoute.submitSearchClientsToAdd(grpId).url}" should {

    "save search terms and redirect" in {
      val summary = GroupSummary.of(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectSaveSearch(Some("Harry"), Some("HMRC-MTD-VAT"))

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitSearchClientsToAdd(grpId)}")
          .withFormUrlEncodedBody("search" -> "Harry", "filter" -> "HMRC-MTD-VAT")
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitSearchClientsToAdd(grpId)(request)
      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showAddClients(grpId, None, None).url
    }
  }

  s"GET ${ctrlRoute.showAddClients(grpId, None, None).url}" should {

    "render correctly the manage group add clients page" in {
      // given
      val groupSummary = GroupSummary(grpId, "Carrots", Some(1), 1)
      val existingClients = displayClients.map(_.copy(alreadyInGroup = true))
      val availableClients: Seq[DisplayClient] =
        (5 to 8).map(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i")).map(DisplayClient.fromClient(_))
      val PAGE = 2
      val PAGE_SIZE = 10
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(groupSummary))
      expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)
      expectGetPaginatedClientsToAddToGroup(grpId, PAGE, PAGE_SIZE, None, None)(
        groupSummary,
        existingClients ++ availableClients
      )

      // when
      val result = controller.showAddClients(grpId, Option(PAGE), Option(PAGE_SIZE))(request)

      // then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Select clients (page 2 of 4) - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "This access group is Carrots"
      html.select(Css.H1).text shouldBe "Select clients (page 2 of 4)"
      html.select("#selected-count-text strong").text shouldBe "2"

      val tableOfClients = html.select(Css.tableWithId("multi-select-table"))
      val th = tableOfClients.select("thead th")
      th.size() shouldBe 4
      th.get(0).text() shouldBe "Select client"
      th.get(1).text() shouldBe "Client reference"
      th.get(2).text() shouldBe "Tax reference"
      th.get(3).text() shouldBe "Tax service"

      val trs = tableOfClients.select("tbody tr")
      trs.size() shouldBe 7
      // first row
      val row1 = trs.get(0)
      val row1Cells = row1.select("td")
      row1Cells.get(0).text() shouldBe "i Info Already in this group"
      row1Cells.get(1).text() shouldBe "friendly0"
      row1Cells.get(2).text() shouldBe "ending in 6780"
      row1Cells.get(3).text() shouldBe "VAT"

      val row3Cells = trs.get(2).select("td")
      row3Cells.get(0).text() shouldBe "i Info Already in this group"
      row3Cells.get(1).text() shouldBe "friendly2"
      row3Cells.get(2).text() shouldBe "ending in 6782"
      row3Cells.get(3).text() shouldBe "VAT"

      val row4Cells = trs.get(3).select("td")
      // this is not in group so should have checkbox to add
      row4Cells.get(0).select("input[type=checkbox]").attr("name") shouldBe "clients[]"
      row4Cells.get(0).text() shouldBe "Client reference friendly5, Tax reference ending in 6785, Tax service VAT"
      row4Cells.get(1).text() shouldBe "friendly5"
      row4Cells.get(2).text() shouldBe "ending in 6785"
      row4Cells.get(3).text() shouldBe "VAT"

      // this is not in group so should have checkbox to add
      val row7Cells = trs.get(6).select("td")
      row7Cells.get(0).select("input[type=checkbox]").attr("name") shouldBe "clients[]"
      row7Cells.get(0).text() shouldBe "Client reference friendly8, Tax reference ending in 6788, Tax service VAT"
      row7Cells.get(1).text() shouldBe "friendly8"
      row7Cells.get(2).text() shouldBe "ending in 6788"
      row7Cells.get(3).text() shouldBe "VAT"

      html.select("p#selected-count-text").text() shouldBe "2 clients selected across all searches"
    }

    "render with NO clients after a search returns no results" in { // Render a different view if no results (APB-7378)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(accessGroup.id, Some(GroupSummary.of(accessGroup)))
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)
      expectGetSessionItem(CLIENT_SEARCH_INPUT, "foo")
      expectGetSessionItemNone(SELECTED_CLIENTS) // There are no selected clients
      expectGetPaginatedClientsToAddToGroup(grpId, search = Some("foo"))(GroupSummary.of(accessGroup), Seq.empty)

      val result = controller.showAddClients(accessGroup.id)(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      val buttons = html.select("button")
      buttons.size() shouldBe 1
      html.select("button").get(0).text() shouldBe "Search for clients"
    }
  }

  s"POST ${ctrlRoute.submitAddClients(grpId).url}" should {

    "save selected clients to session" when {

      s"button is Continue and redirect to ${routes.ManageGroupController.showManageGroups(None, None).url}" in {

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", ctrlRoute.submitAddClients(grpId).url)
            .withFormUrlEncodedBody(
              "clients[0]" -> displayClients.head.id,
              "clients[1]" -> displayClients.last.id,
              "search"     -> "",
              "filter"     -> "",
              "submit"     -> CONTINUE_BUTTON
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        expectAuthOkOptedInReady()
        val summary = GroupSummary.of(accessGroup.copy(clients = fakeClients.toSet))
        expectGetCustomSummaryById(grpId, Some(summary))
        expectGetSessionItem(SELECTED_CLIENTS, displayClients)

        val displayClientsIds: Seq[String] = displayClients.map(_.id)
        val formData = AddClientsToGroup(
          clients = Some(List(displayClientsIds.head, displayClientsIds.last)),
          submit = CONTINUE_BUTTON
        )
        expectSaveClientsToAddToExistingGroup(formData, displayClients)
        expectGetSessionItem(CLIENT_SEARCH_INPUT, "Harry")
        expectGetSessionItem(CLIENT_FILTER_INPUT, "HMRC-MTD-VAT")

        // when
        val result = controller.submitAddClients(grpId)(request)

        // then
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe
          ctrlRoute.showReviewSelectedClients(grpId, None, None).url
      }

      "display error when button is CONTINUE_BUTTON, selected in session and ALL deselected" in {
        // given
        val groupSummary = GroupSummary(grpId, "Carrots", Some(1), 1)

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", ctrlRoute.submitAddClients(grpId).url)
            .withSession(SessionKeys.sessionId -> "session-x")
            .withFormUrlEncodedBody(
              "clients" -> "",
              "search"  -> "",
              "filter"  -> "",
              "submit"  -> CONTINUE_BUTTON
            )

        expectAuthOkOptedInReady()
        expectGetCustomSummaryById(grpId, Some(groupSummary))
        expectGetSessionItem(SELECTED_CLIENTS, displayClients)
        expectGetSessionItem(CLIENT_SEARCH_INPUT, "Harry")
        expectGetSessionItem(CLIENT_FILTER_INPUT, "HMRC-MTD-VAT")

        val formData = AddClientsToGroup(
          clients = None,
          submit = CONTINUE_BUTTON
        )
        expectGetPaginatedClientsToAddToGroup(grpId, 1, 20, Option("Harry"), Option("HMRC-MTD-VAT"))(
          groupSummary,
          displayClients
        )
        expectSaveClientsToAddToExistingGroup(formData)

        // when
        val result = controller.submitAddClients(grpId)(request)

        status(result) shouldBe OK
        val html = Jsoup.parse(contentAsString(result))

        // then
        html.title() shouldBe "Error: Select clients (page 1 of 2) - Agent services account - GOV.UK"
        html.select(Css.H1).text() shouldBe "Select clients (page 1 of 2)"
        html
          .select(Css.errorSummaryForField("clients"))
      }

      s"PAGINATION_BUTTON clicked redirect to ${ctrlRoute.showAddClients(grpId, Some(2), Some(20)).url}" in {

        // given
        val paginationButton = PAGINATION_BUTTON + "_2"

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", ctrlRoute.submitAddClients(grpId).url)
            .withSession(SessionKeys.sessionId -> "session-x")
            .withFormUrlEncodedBody(
              "clients" -> "",
              "submit"  -> paginationButton
            )

        expectAuthOkOptedInReady()
        val summary = GroupSummary.of(accessGroup.copy(clients = fakeClients.toSet))
        expectGetCustomSummaryById(grpId, Some(summary))
        expectGetSessionItem(SELECTED_CLIENTS, Seq.empty) // does not matter
        expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
        expectGetSessionItemNone(CLIENT_FILTER_INPUT)
        val formData = AddClientsToGroup(clients = None, submit = paginationButton)
        expectSaveClientsToAddToExistingGroup(formData)

        // when
        val result = controller.submitAddClients(grpId)(request)

        // then
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe ctrlRoute.showAddClients(grpId, Some(2), Some(20)).url
      }
    }

    "display error when button is CONTINUE_BUTTON, no clients were selected" in {
      // given
      val groupSummary = GroupSummary(accessGroup.id, accessGroup.groupName, Some(1), 1)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitAddClients(grpId).url)
          .withSession(SessionKeys.sessionId -> "session-x")
          .withFormUrlEncodedBody(
            "clients" -> "",
            "search"  -> "",
            "filter"  -> "",
            "submit"  -> CONTINUE_BUTTON
          )

      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(groupSummary))
      expectGetSessionItem(SELECTED_CLIENTS, Seq.empty) // does not matter
      expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)
      expectGetPaginatedClientsToAddToGroup(grpId, 1, 20, None, None)(groupSummary, displayClients)

      // when
      val result = controller.submitAddClients(grpId)(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Select clients (page 1 of 2) - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients (page 1 of 2)"
      html
        .select(Css.errorSummaryForField("clients"))
    }

  }

  s"GET showReviewSelectedClients on url ${ctrlRoute.showReviewSelectedClients(grpId, None, None).url}" should {

    "REDIRECT to showSearchClientsToAdd if no SELECTED_CLIENTS in session" in {
      // given
      expectAuthOkOptedInReady()

      expectGetSessionItemNone(SELECTED_CLIENTS)
      expectGetCustomSummaryById(grpId, Some(GroupSummary.of(accessGroup)))

      // when
      val result = controller.showReviewSelectedClients(grpId, None, None)(request)

      // then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSearchClientsToAdd(grpId).url
    }

    "Render correctly if SELECTED_CLIENTS are in session" when {
      "on first page, no params" in {
        // given
        expectAuthOkOptedInReady()

        expectGetSessionItem(SELECTED_CLIENTS, displayClients)
        expectGetCustomSummaryById(grpId, Some(GroupSummary.of(accessGroup)))
        expectGetSessionItemNone(CONFIRM_CLIENTS_SELECTED)

        // when
        val result = controller.showReviewSelectedClients(grpId, None, None)(request)

        // then
        status(result) shouldBe OK
        val html = Jsoup.parse(contentAsString(result))
        html.title() shouldBe "Review selected clients - Agent services account - GOV.UK"
        html.select(H1).text() shouldBe "You have selected 3 clients to add to the group"
        html.select(Css.tableWithId("clients")).select("tbody tr").size() shouldBe 3
        html.select("form .govuk-fieldset__legend").text() shouldBe "Do you need to select more clients?"
        val answerRadios = html.select(Css.radioButtonsField("answer-radios"))
        answerRadios
          .select("label[for=answer]")
          .text() shouldBe "Yes, select more clients"
        answerRadios
          .select("label[for=answer-no]")
          .text() shouldBe "No"
        html.select(Css.submitButton).text() shouldBe "Save and continue"
      }

      "on page 2 with page size 1" in {
        // given
        expectAuthOkOptedInReady()

        expectGetSessionItem(SELECTED_CLIENTS, displayClients)
        expectGetCustomSummaryById(grpId, Some(GroupSummary.of(accessGroup)))
        expectGetSessionItemNone(CONFIRM_CLIENTS_SELECTED)

        // when
        val result = controller.showReviewSelectedClients(grpId, Some(2), Some(1))(request)

        // then
        status(result) shouldBe OK
        val html = Jsoup.parse(contentAsString(result))
        html.title() shouldBe "Review selected clients (page 2 of 3) - Agent services account - GOV.UK"
        html.select(H1).text() shouldBe "You have selected 3 clients to add to the group (page 2 of 3)"
        html.select(Css.tableWithId("clients")).select("tbody tr").size() shouldBe 1

        val paginationListItems = html.select(Css.pagination_li)
        paginationListItems.size() shouldBe 3
        paginationListItems.get(0).text() shouldBe "1" // first pagination item

        paginationListItems.get(1).text() shouldBe "2" // second pagination item
        paginationListItems.get(1).hasClass("govuk-pagination__item--current")

        paginationListItems.get(2).text() shouldBe "3" // 3rd pagination item
        paginationListItems
          .get(2)
          .select("a")
          .attr("href") shouldBe ctrlRoute.showReviewSelectedClients(accessGroup.id, Some(3), Some(1)).url

      }

    }
  }

  s"POST submitReviewSelectedClients on url${ctrlRoute.submitReviewSelectedClients(grpId).url}" should {

    s"redirect to existing group clients on url when page is submitted with answer ‘NO'/'false'" in {

      expectAuthOkOptedInReady()

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitReviewSelectedClients(grpId)}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(SELECTED_CLIENTS, Seq(displayClients.head, displayClients.last))
      expectGetCustomSummaryById(grpId, Some(GroupSummary.of(accessGroup)))
      expectDeleteSessionItems(managingGroupKeys)
      expectAddMembersToGroup(
        grpId,
        AddMembersToAccessGroupRequest(clients =
          Some(Set(displayClients.head, displayClients.last).map(dc => Client(dc.enrolmentKey, dc.name)))
        )
      )

      val result = controller.submitReviewSelectedClients(grpId)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showExistingGroupClients(grpId, None, None).url

    }

    s"redirect to showSearchClientsToAdd on url ‘${ctrlRoute.showSearchClientsToAdd(grpId)}’ " +
      s"when page is submitted with answer ‘YES'/'true'" in {

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", s"${controller.submitReviewSelectedClients(grpId)}")
            .withFormUrlEncodedBody("answer" -> "true")
            .withSession(SessionKeys.sessionId -> "session-x")

        expectAuthOkOptedInReady()
        expectGetSessionItem(SELECTED_CLIENTS, displayClients)
        expectGetCustomSummaryById(grpId, Some(GroupSummary.of(accessGroup)))
        expectDeleteSessionItems(clientFilteringKeys)

        val result = controller.submitReviewSelectedClients(grpId)(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe ctrlRoute
          .showSearchClientsToAdd(grpId)
          .url
      }

    s"render errors when no radio button selected" in {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitReviewSelectedClients(grpId)}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectGetCustomSummaryById(grpId, Some(GroupSummary.of(accessGroup)))

      val result = controller.submitReviewSelectedClients(grpId)(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Review selected clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 3 clients to add to the group"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you need to select more clients"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you need to select more clients"

    }
  }

  private val clientToRemove: DisplayClient = displayClients.head

  s"GET ${ctrlRoute.showConfirmRemoveClient(grpId, clientToRemove.id).url}" should {

    "render the confirm remove client page" in {
      val summary = GroupSummary.of(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectLookupClient(arn)(clientToRemove)
      expectPutSessionItem(CLIENT_TO_REMOVE, clientToRemove)

      val result = controller.showConfirmRemoveClient(grpId, clientToRemove.id)(request)
      // then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Remove friendly0 from this access group? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Remove friendly0 from this access group?"
      html.select(Css.paragraphs).isEmpty shouldBe true
      html.select(Css.backLink).attr("href") shouldBe "#"
      html.select(Css.backLink).text() shouldBe "Back"

      html.select(Css.form).attr("action") shouldBe ctrlRoute.submitConfirmRemoveClient(grpId).url
      html.select("label[for=answer]").text() shouldBe "Yes"
      html.select("label[for=answer-no]").text() shouldBe "No"
      html.select(Css.form + " input[name=answer]").size() shouldBe 2
      html.select(Css.submitButton).text() shouldBe "Save and continue"

    }

  }

  s"POST ${ctrlRoute.submitConfirmRemoveClient(grpId).url}" should {

    "confirm remove client ‘yes’ removes from group and redirect to group clients list" in {
      val summary = GroupSummary.of(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectGetSessionItem(CLIENT_TO_REMOVE, clientToRemove)
      expectRemoveClientFromGroup(grpId, clientToRemove)
      expectDeleteSessionItem(CLIENT_TO_REMOVE)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitConfirmRemoveClient(grpId)}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitConfirmRemoveClient(grpId)(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showExistingGroupClients(grpId, None, None).url
    }

    "confirm remove client ‘no’ redirects to group clients list" in {
      val summary = GroupSummary.of(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectGetSessionItem(CLIENT_TO_REMOVE, clientToRemove)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitConfirmRemoveClient(grpId)}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitConfirmRemoveClient(grpId)(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showExistingGroupClients(grpId, None, None).url
    }

    "render errors when no selections of yes/no made" in {
      val summary = GroupSummary.of(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectGetSessionItem(CLIENT_TO_REMOVE, clientToRemove)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitConfirmRemoveClient(grpId)}")
          .withFormUrlEncodedBody("ohai" -> "blah")
          .withSession(SessionKeys.sessionId -> "session-x")

      // when
      val result = controller.submitConfirmRemoveClient(grpId)(request)

      // then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Remove friendly0 from selected clients? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Remove friendly0 from selected clients?"
      html
        .select(Css.errorSummaryForField("answer"))
        .text() shouldBe "Select yes if you need to remove this client from the access group"
      html
        .select(Css.errorForField("answer"))
        .text() shouldBe "Error: Select yes if you need to remove this client from the access group"

    }
  }

  s"GET ${ctrlRoute.showConfirmRemoveFromSelectedClients(grpId, clientToRemove.id).url}" should {

    "render the confirm remove client page" in {
      val summary = GroupSummary.of(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectPutSessionItem(CLIENT_TO_REMOVE, clientToRemove)

      val result = controller.showConfirmRemoveFromSelectedClients(grpId, clientToRemove.id)(request)
      // then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Remove friendly0 from selected clients? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Remove friendly0 from selected clients?"
      html.select(Css.paragraphs).isEmpty shouldBe true
      html.select(Css.backLink).attr("href") shouldBe "#"
      html.select(Css.backLink).text() shouldBe "Back"

      html.select(Css.form).attr("action") shouldBe ctrlRoute
        .submitConfirmRemoveFromSelectedClients(grpId, clientToRemove.id)
        .url
      html.select("label[for=answer]").text() shouldBe "Yes"
      html.select("label[for=answer-no]").text() shouldBe "No"
      html.select(Css.form + " input[name=answer]").size() shouldBe 2
      html.select(Css.submitButton).text() shouldBe "Save and continue"

    }

    "redirect when no client found" in {
      val summary = GroupSummary.of(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectGetSessionItem(
        SELECTED_CLIENTS,
        displayClients.filterNot(c => c.id == clientToRemove.id)
      ) // clientToRemove is not present

      val result = controller.showConfirmRemoveFromSelectedClients(grpId, clientToRemove.id)(request)
      // then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.ManageGroupClientsController.showSearchClientsToAdd(grpId).url

    }

  }

  s"POST ${ctrlRoute.submitConfirmRemoveFromSelectedClients(grpId, clientToRemove.enrolmentKey).url}" should {

    "confirm remove client ‘yes’ removes from group and redirect to REVIEW SELECTED CLIENTS page" in {
      val summary = GroupSummary.of(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectGetSessionItem(CLIENT_TO_REMOVE, clientToRemove)
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectPutSessionItem(SELECTED_CLIENTS, displayClients.filterNot(_.id == clientToRemove.id))
      expectDeleteSessionItem(CLIENT_TO_REMOVE)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitConfirmRemoveFromSelectedClients(grpId, clientToRemove.enrolmentKey)}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitConfirmRemoveFromSelectedClients(grpId, clientToRemove.enrolmentKey)(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showReviewSelectedClients(grpId, None, None).url
    }

    "confirm remove LAST selected client is removed and REDIRECTS to search clients to add" in {
      val summary = GroupSummary.of(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectGetSessionItem(CLIENT_TO_REMOVE, clientToRemove)
      val only1SelectedClient = Seq(clientToRemove)
      expectGetSessionItem(SELECTED_CLIENTS, only1SelectedClient)
      expectPutSessionItem(SELECTED_CLIENTS, Seq.empty[DisplayClient])
      expectDeleteSessionItem(CLIENT_TO_REMOVE)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitConfirmRemoveFromSelectedClients(grpId, clientToRemove.enrolmentKey)}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitConfirmRemoveFromSelectedClients(grpId, clientToRemove.enrolmentKey)(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showSearchClientsToAdd(grpId).url
    }

    "confirm remove client ‘no’ redirects to group clients list" in {
      val summary = GroupSummary.of(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectGetSessionItem(CLIENT_TO_REMOVE, clientToRemove)
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitConfirmRemoveFromSelectedClients(grpId, clientToRemove.enrolmentKey)}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitConfirmRemoveFromSelectedClients(grpId, clientToRemove.enrolmentKey)(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showReviewSelectedClients(grpId, None, None).url
    }

    "render errors when no selections of yes/no made" in {
      val summary = GroupSummary.of(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectGetSessionItem(CLIENT_TO_REMOVE, clientToRemove)
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitConfirmRemoveFromSelectedClients(grpId, clientToRemove.enrolmentKey)}")
          .withFormUrlEncodedBody("ohai" -> "blah")
          .withSession(SessionKeys.sessionId -> "session-x")

      // when
      val result = controller.submitConfirmRemoveFromSelectedClients(grpId, clientToRemove.enrolmentKey)(request)

      // then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Remove friendly0 from selected clients? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Remove friendly0 from selected clients?"
      html
        .select(Css.errorSummaryForField("answer"))
        .text() shouldBe "Select yes if you no longer want to add this client to the access group"
      html
        .select(Css.errorForField("answer"))
        .text() shouldBe "Error: Select yes if you no longer want to add this client to the access group"

    }
  }

}
