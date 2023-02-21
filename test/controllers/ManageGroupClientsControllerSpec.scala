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
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector, UpdateAccessGroupRequest}
import controllers.actions.AuthAction
import helpers.Css._
import helpers.{BaseSpec, Css}
import models.{AddClientsToGroup, DisplayClient, TeamMember}
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import org.mongodb.scala.bson.ObjectId
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, await, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services._
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

import java.time.LocalDate
import java.time.LocalDateTime.MIN
import java.util.Base64

class ManageGroupClientsControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val mockSessionCacheService: SessionCacheService = mock[SessionCacheService]
  implicit val mockSessionCacheOps: SessionCacheOperationsService = mock[SessionCacheOperationsService] // TODO move to a 'real' (in-memory store) session cache service and you won't have to mock either SessionCacheService or SessionCacheServiceOperations!
  implicit lazy val mockGroupService: GroupService = mock[GroupService]
  implicit lazy val mockTaxGroupService: TaxGroupService = mock[TaxGroupService]
  implicit lazy val mockClientService: ClientService = mock[ClientService]
  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

  private val agentUser: AgentUser = AgentUser(RandomStringUtils.random(5), "Rob the Agent")
  val accessGroup: CustomGroup = CustomGroup(new ObjectId(),
                                arn,
                                "Bananas",
                                LocalDate.of(2020, 3, 10).atStartOfDay(),
                                null,
                                agentUser,
                                agentUser,
                                None,
                                None)

  val taxGroup: TaxGroup = TaxGroup(arn, "Bananas", MIN, MIN, agentUser, agentUser, None, "HMRC-MTD-VAT", automaticUpdates = true, None)

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector))
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

  val encodedDisplayClients: Seq[String] = displayClients.map(client =>
    Base64.getEncoder.encodeToString(Json.toJson(client).toString.getBytes))

  val agentUsers: Set[AgentUser] = (1 to 5).map(i => AgentUser(id = s"John $i", name = s"John $i name")).toSet

  val userDetails: Seq[UserDetails] = (1 to 5)
    .map { i =>
      UserDetails(Some(s"John $i"), Some("User"), Some(s"John $i name"), Some(s"john$i@abc.com"))
    }

  val teamMembers: Seq[TeamMember] = userDetails.map(TeamMember.fromUserDetails)

  val controller: ManageGroupClientsController = fakeApplication.injector.instanceOf[ManageGroupClientsController]
  private val ctrlRoute: ReverseManageGroupClientsController = routes.ManageGroupClientsController
  private val grpId: String = accessGroup._id.toString

  def expectAuthOkOptedInReady(): Unit = {
    expectAuthorisationGrantsAccess(mockedAuthResponse)
    expectIsArnAllowed(allowed = true)
    expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
  }

  s"GET ${ctrlRoute.showExistingGroupClients(grpId, None, None).url}" should {

    "render correctly the first page of EXISTING CLIENTS page with no query params" in {
      //given
      val groupWithClients = accessGroup.copy(clients =
        Some(displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet))
      val summary = GroupSummary.fromAccessGroup(groupWithClients)

      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))

      expectGetPaginatedClientsForCustomGroup(grpId)(1, 20)((displayClients,PaginationMetaData(lastPage = true,firstPage = true,0,1,10,1,10)))

      //when
      val result = controller.showExistingGroupClients(groupWithClients._id.toString, None, None)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Manage clients - Bananas - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Manage clients in this group"

      val th = html.select(Css.tableWithId("clients")).select("thead th")
      th.size() shouldBe 3
      th.get(0).text() shouldBe "Client reference"
      th.get(1).text() shouldBe "Tax reference"
      th.get(2).text() shouldBe "Tax service"

      val trs = html.select(Css.tableWithId("clients")).select("tbody tr")

      trs.size() shouldBe 3
      //first row
      trs.get(0).select("td").get(0).text() shouldBe "friendly0"
      trs.get(0).select("td").get(1).text() shouldBe "ending in 6780"
      trs.get(0).select("td").get(2).text() shouldBe "VAT"

      //last row
      trs.get(2).select("td").get(0).text() shouldBe "friendly2"
      trs.get(2).select("td").get(1).text() shouldBe "ending in 6782"
      trs.get(2).select("td").get(2).text() shouldBe "VAT"

      //html.select("p#clients-in-group").text() shouldBe "Showing total of 3 clients"
      html.select("a#update-clients").text() shouldBe "Update clients"
      html.select("a#update-clients").attr("href") shouldBe
        ctrlRoute.showSearchClientsToAdd(grpId).url
    }

    "render with filter & searchTerm set" in {
      //given
      val groupWithClients = accessGroup.copy(clients =
        Some(displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet))
      val summary = GroupSummary.fromAccessGroup(groupWithClients)

      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))

      expectPutSessionItem(CLIENT_SEARCH_INPUT, "friendly1")
      expectPutSessionItem(CLIENT_FILTER_INPUT, "HMRC-MTD-VAT")
      expectGetPaginatedClientsForCustomGroup(grpId)(1, 20)(displayClients.take(1),PaginationMetaData(lastPage = true,firstPage = true,0,1,10,1,10))

      implicit val requestWithQueryParams: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET,
        ctrlRoute.showExistingGroupClients(groupWithClients._id.toString, None, None).url +
          "?submit=filter&search=friendly1&filter=HMRC-MTD-VAT"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result =
        controller.showExistingGroupClients(groupWithClients._id.toString, None, None)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Filter results for 'friendly1' and 'VAT' Manage clients - Bananas - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Manage clients in this group"
      html.select(H2).text shouldBe "Filter results for 'friendly1' and 'VAT'"

      val th = html.select(Css.tableWithId("clients")).select("thead th")
      th.size() shouldBe 3
      val trs = html.select(Css.tableWithId("clients")).select("tbody tr")
      trs.size() shouldBe 1
    }

    "render with filter that matches nothing" in {
      //given
      val groupWithClients = accessGroup.copy(clients =
        Some(displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet))
      val summary = GroupSummary.fromAccessGroup(groupWithClients)

      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))

      expectPutSessionItem(CLIENT_SEARCH_INPUT, "friendly1")
      expectPutSessionItem(CLIENT_FILTER_INPUT, "HMRC-CGT-PD")
      expectGetPaginatedClientsForCustomGroup(grpId)(1, 20)((Seq.empty[DisplayClient],PaginationMetaData(lastPage = true,firstPage = true,0,1,10,1,10)))

      //there are none of these HMRC-CGT-PD in the setup clients. so expect no results back
      val NON_MATCHING_FILTER = "HMRC-CGT-PD"
      implicit val requestWithQueryParams: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET,
        ctrlRoute.showExistingGroupClients(groupWithClients._id.toString, None, None).url +
          s"?submit=filter&search=friendly1&filter=$NON_MATCHING_FILTER"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showExistingGroupClients(groupWithClients._id.toString, None, None)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Filter results for 'friendly1' and 'Capital Gains Tax on UK Property account' Manage clients - Bananas - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Manage clients in this group"

      val tableOfClients = html.select(Css.tableWithId("clients"))
      tableOfClients.isEmpty shouldBe true
      val noClientsFound = html.select("div#clients")
      noClientsFound.isEmpty shouldBe false
      noClientsFound.select("h2").text shouldBe "No clients found"
      noClientsFound.select("p").text shouldBe "Update your filters and try again or clear your filters to see all your clients"
    }

    "redirect to baseUrl when CLEAR FILTER is clicked" in {
      //given
      val groupWithClients = accessGroup.copy(clients =
        Some(displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet))
      val summary = GroupSummary.fromAccessGroup(groupWithClients)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectDeleteSessionItems(clientFilteringKeys)

      //and we have CLEAR filter in query params
      implicit val requestWithQueryParams: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET,
        ctrlRoute.showExistingGroupClients(groupWithClients._id.toString, None, None).url +
          s"?submit=clear"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result =
        controller.showExistingGroupClients(groupWithClients._id.toString, None, None)(requestWithQueryParams)

      //then
      redirectLocation(result).get
        .shouldBe(ctrlRoute.showExistingGroupClients(groupWithClients._id.toString, Some(1), Some(20)).url)
    }

    "redirect to new page when a pagination button is clicked" in {
      //given
      val groupWithClients = accessGroup.copy(clients =
        Some(displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet))
      val summary = GroupSummary.fromAccessGroup(groupWithClients)

      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))

      val pageNumber = 2
      //and we have PAGINATION_BUTTON filter in query params
      implicit val requestWithQueryParams: FakeRequest[AnyContentAsEmpty.type] =
        FakeRequest(GET,
          ctrlRoute.showExistingGroupClients(groupWithClients._id.toString, None, None).url +
          s"?submit=${PAGINATION_BUTTON}_$pageNumber"
        )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result =
        controller.showExistingGroupClients(groupWithClients._id.toString, None, None)(requestWithQueryParams)

      //then
      redirectLocation(result).get
        .shouldBe(ctrlRoute.showExistingGroupClients(groupWithClients._id.toString, Some(pageNumber), Some(20)).url)
    }

  }

  s"GET ${ctrlRoute.showSearchClientsToAdd(grpId).url}" should {
    "render the client search page" in {
      val summary = GroupSummary.fromAccessGroup(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectGetAvailableTaxServiceClientCount(arn)(List(13, 85, 38, 22, 108))

      expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)

      val result = controller.showSearchClientsToAdd(grpId)(request)
      // then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Search for clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Search for clients"
      html
        .select(Css.backLink)
        .attr("href") shouldBe routes.ManageGroupClientsController.showExistingGroupClients(grpId, None, None).url

      html.select(Css.labelFor("search")).text() shouldBe "Filter by tax reference or client reference"

      html.select(Css.labelFor("filter")).text() shouldBe "Filter by tax service"

    }

    "render the client search page with inputs saved in session" in {
      val summary = GroupSummary.fromAccessGroup(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectGetSessionItem(CLIENT_SEARCH_INPUT, "Harry")
      expectGetSessionItem(CLIENT_FILTER_INPUT, "HMRC-MTD-VAT")
      expectGetAvailableTaxServiceClientCount(arn)(List(13, 85, 38, 22, 108))

      val result = controller.showSearchClientsToAdd(grpId)(request)
      // then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Search for clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Search for clients"
      html
        .select(Css.backLink)
        .attr("href") shouldBe routes.ManageGroupClientsController.showExistingGroupClients(grpId, None, None).url

      html.select(Css.labelFor("search")).text() shouldBe "Filter by tax reference or client reference"
      html.select("#search").attr("value") shouldBe "Harry"
      html.select(Css.labelFor("filter")).text() shouldBe "Filter by tax service"
      //TODO this isn't working
      //html.select("#filter").attr("value") shouldBe "HMRC-MTD-VAT"

    }

  }

  s"POST ${ctrlRoute.submitSearchClientsToAdd(grpId).url}" should {
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
      val summary = GroupSummary.fromAccessGroup(accessGroup)
      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(summary))
      expectSaveSearch(Some("Harry"), Some("HMRC-MTD-VAT"))

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest(
          "POST",
          s"${controller.submitSearchClientsToAdd(grpId)}")
          .withFormUrlEncodedBody("search" -> "Harry", "filter" -> "HMRC-MTD-VAT")
          .withSession(SessionKeys.sessionId -> "session-x")


      val result = controller.submitSearchClientsToAdd(grpId)(request)
      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showManageGroupClients(grpId, None, None).url
    }
  }

  s"GET ${ctrlRoute.showManageGroupClients(grpId, None, None).url}" should {

    "render correctly the manage group CLIENTS page" in {
      //given
      val selected = displayClients.map(_.copy(selected = true))

      expectAuthOkOptedInReady()
      expectGetGroupById(grpId, Some(accessGroup.copy(clients = Some(fakeClients.toSet))))
      expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
      expectGetSessionItemNone(CLIENT_FILTER_INPUT)
      expectGetSessionItemNone(SELECTED_CLIENTS)
      expectPutSessionItem(SELECTED_CLIENTS, selected)
      expectGetPageOfClients(arn)(displayClients)

      //when
      val result = controller.showManageGroupClients(grpId, None, None)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Update clients in this group - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Update clients in this group"

      val th = html.select(Css.tableWithId("multi-select-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Client reference"
      th.get(2).text() shouldBe "Tax reference"
      th.get(3).text() shouldBe "Tax service"
      val trs = html.select(Css.tableWithId("multi-select-table")).select("tbody tr")

      trs.size() shouldBe 3
      //first row
      trs.get(0).select("td").get(1).text() shouldBe "friendly0"
      trs.get(0).select("td").get(2).text() shouldBe "ending in 6780"
      trs.get(0).select("td").get(3).text() shouldBe "VAT"

      //last row
      trs.get(2).select("td").get(1).text() shouldBe "friendly2"
      trs.get(2).select("td").get(2).text() shouldBe "ending in 6782"
      trs.get(2).select("td").get(3).text() shouldBe "VAT"

      //TODO - this is wrong right?
      html.select("p#member-count-text").text() shouldBe "0 clients selected across all searches"
    }

    "render correctly the manage group CLIENTS page when there are no clients to add found" in {
      //given
      expectAuthOkOptedInReady()
      expectGetGroupById(grpId, Some(accessGroup.copy(clients = Some(fakeClients.toSet))))
      expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
      expectGetSessionItem(CLIENT_FILTER_INPUT, "HMRC-MTD-VAT")
      expectGetSessionItemNone(SELECTED_CLIENTS)
      expectPutSessionItem(SELECTED_CLIENTS, displayClients.map(_.copy(selected = true)) )
      expectGetPageOfClients(arn)(Seq.empty)
      //when
      val result = controller.showManageGroupClients(grpId, None, None)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Filter results for 'VAT' Update clients in this group - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Update clients in this group"

      val th = html.select(Css.tableWithId("multi-select-table")).select("thead th")
      th.size() shouldBe 0
      html.select(Css.H2).text() shouldBe "No clients found"
      html.select(Css.paragraphs).get(1).text() shouldBe "Update your filters and try again or clear your filters to see all your clients"

    }

    "render with clients held in session when a filter was applied" in {

      expectAuthOkOptedInReady()
      expectGetGroupById(grpId, Some(accessGroup.copy(clients = Some(fakeClients.toSet))))
      expectGetSessionItem(CLIENT_SEARCH_INPUT, "blah")
      expectGetSessionItem(CLIENT_FILTER_INPUT, "HMRC-MTD-VAT")
      expectGetSessionItemNone(SELECTED_CLIENTS)
      expectPutSessionItem(SELECTED_CLIENTS, displayClients.map(_.copy(selected = true)) )
      expectGetPageOfClients(arn)(displayClients.take(1))

      val result = controller.showManageGroupClients(grpId, None, None)(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Filter results for 'blah' and 'VAT' Update clients in this group - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Update clients in this group"

      val trs = html.select(Css.tableWithId("multi-select-table")).select("tbody tr")

      trs.size() shouldBe 1
      trs.get(0).select("td").get(1).text() shouldBe "friendly0"
      trs.get(0).select("td").get(2).text() shouldBe "ending in 6780"
      trs.get(0).select("td").get(3).text() shouldBe "VAT"
    }
  }

  s"POST ${ctrlRoute.submitManageGroupClients(grpId).url}" should {

    "save selected clients to session" when {

      s"button is Continue and redirect to ${routes.ManageGroupController.showManageGroups(None,None).url}" in {

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", ctrlRoute.submitManageGroupClients(grpId).url)
            .withFormUrlEncodedBody(
                            "clients[0]" -> displayClients.head.id,
              "clients[1]" -> displayClients.last.id,
              "search" -> "",
              "filter" -> "",
              "submit" -> CONTINUE_BUTTON
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        expectAuthOkOptedInReady()
        expectGetGroupById(grpId, Some(accessGroup.copy(clients = Some(fakeClients.toSet))))
        expectGetSessionItem(SELECTED_CLIENTS, displayClients)

        val displayClientsIds: Seq[String] = displayClients.map(_.id)
        val formData = AddClientsToGroup(
          clients = Some(List(displayClientsIds.head, displayClientsIds.last)),
          submit = CONTINUE_BUTTON
        )
        expectSavePageOfClients(formData, displayClients)

        expectDeleteSessionItems(clientFilteringKeys)

        val result = controller.submitManageGroupClients(grpId)(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe
          ctrlRoute.showReviewSelectedClients(grpId, None, None).url
      }

      "display error when button is CONTINUE_BUTTON, selected in session and ALL deselected" in {
        // given
        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", ctrlRoute.submitManageGroupClients(grpId).url)
            .withSession(SessionKeys.sessionId -> "session-x")
            .withFormUrlEncodedBody(
            "clients" -> "",
            "search" -> "",
            "filter" -> "",
            "submit" -> CONTINUE_BUTTON
            )

        expectAuthOkOptedInReady()
        expectGetGroupById(grpId, Some(accessGroup.copy(clients = Some(fakeClients.toSet))))
        expectGetSessionItem(SELECTED_CLIENTS, displayClients)

        val formData = AddClientsToGroup(
          clients = None,
          submit = CONTINUE_BUTTON
        )
        expectSavePageOfClients(formData, Seq.empty)

        expectGetPageOfClients(arn)(displayClients)

        // when
        await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients)) // hasPreSelected is true
        val result = controller.submitManageGroupClients(grpId)(request)

        status(result) shouldBe OK
        val html = Jsoup.parse(contentAsString(result))

        // then
        html.title() shouldBe "Error: Update clients in this group - Agent services account - GOV.UK"
        html.select(Css.H1).text() shouldBe "Update clients in this group"
        html
          .select(Css.errorSummaryForField("clients"))
      }

      s"PAGINATION_BUTTON clicked redirect to ${ctrlRoute.showManageGroupClients(grpId, Some(2), Some(20)).url}" in {

        val paginationButton = PAGINATION_BUTTON + "_2"

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", ctrlRoute.submitManageGroupClients(grpId).url)
            .withSession(SessionKeys.sessionId -> "session-x")
            .withFormUrlEncodedBody(
              "clients" -> "",
              "submit" -> paginationButton
            )

        expectAuthOkOptedInReady()
        expectGetGroupById(grpId, Some(accessGroup.copy(clients = Some(fakeClients.toSet))))
        expectGetSessionItem(SELECTED_CLIENTS, Seq.empty) //does not matter
        val formData = AddClientsToGroup(
          clients = None,
          submit = paginationButton
        )
        expectSavePageOfClients(formData, Seq.empty)

        // when
        val result = controller.submitManageGroupClients(grpId)(request)
        status(result) shouldBe SEE_OTHER

        redirectLocation(result).get shouldBe
          ctrlRoute.showManageGroupClients(grpId, Some(2),Some(20)).url
      }
    }

    "display error when button is CONTINUE_BUTTON, no clients were selected" in {
      // given

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitManageGroupClients(grpId).url)
          .withSession(SessionKeys.sessionId -> "session-x")
          .withFormUrlEncodedBody(
          "clients" -> "",
          "search" -> "",
          "filter" -> "",
          "submit" -> CONTINUE_BUTTON
        )

      expectAuthOkOptedInReady()
      expectGetGroupById(grpId, Some(accessGroup.copy(clients = Some(fakeClients.toSet))))
      expectGetSessionItem(SELECTED_CLIENTS, Seq.empty)
      expectGetPageOfClients(arn)(displayClients)

      // when
      val result = controller.submitManageGroupClients(grpId)(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Update clients in this group - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Update clients in this group"
      html
        .select(Css.errorSummaryForField("clients"))
    }

  }

  s"GET showReviewSelectedClients on url ${ctrlRoute.showReviewSelectedClients(grpId, None, None).url}" should {

    "REDIRECT to showSearchClientsToAdd if no SELECTED_CLIENTS in session" in {
      //given
      expectAuthOkOptedInReady()

      expectGetSessionItemNone(SELECTED_CLIENTS)
      expectGetCustomSummaryById(grpId, Some(GroupSummary.fromAccessGroup(accessGroup)))

      //when
      val result = controller.showReviewSelectedClients(grpId, None, None)(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSearchClientsToAdd(grpId).url
    }

    "Render correctly if SELECTED_CLIENTS are in session" when {
      "on first page, no params" in {
        //given
        expectAuthOkOptedInReady()

        expectGetSessionItem(SELECTED_CLIENTS, displayClients)
        expectGetCustomSummaryById(grpId, Some(GroupSummary.fromAccessGroup(accessGroup)))

        //when
        val result = controller.showReviewSelectedClients(grpId, None, None)(request)

        //then
        status(result) shouldBe OK
        val html = Jsoup.parse(contentAsString(result))
        html.title() shouldBe "Review selected clients - Agent services account - GOV.UK"
        html.select(H1).text() shouldBe "You have selected 3 clients"
        html.select(Css.tableWithId("clients")).select("tbody tr").size() shouldBe 3
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

      "on page 2 with page size 1" in {
        //given
        expectAuthOkOptedInReady()

        expectGetSessionItem(SELECTED_CLIENTS, displayClients)
        expectGetCustomSummaryById(grpId, Some(GroupSummary.fromAccessGroup(accessGroup)))

        //when
        val result = controller.showReviewSelectedClients(grpId, Some(2), Some(1))(request)

        //then
        status(result) shouldBe OK
        val html = Jsoup.parse(contentAsString(result))
        html.title() shouldBe "Review selected clients - Agent services account - GOV.UK"
        html.select(H1).text() shouldBe "You have selected 3 clients"
        html.select(Css.tableWithId("clients")).select("tbody tr").size() shouldBe 1

        val paginationListItems = html.select(Css.pagination_li)
        paginationListItems.size() shouldBe 3
        paginationListItems.get(0).text() shouldBe "1" // first pagination item

        paginationListItems.get(1).text() shouldBe "2"  // second pagination item
        paginationListItems.get(1).hasClass("govuk-pagination__item--current")

        paginationListItems.get(2).text() shouldBe "3"  // 3rd pagination item
        paginationListItems.get(2).select("a")
          .attr("href") shouldBe ctrlRoute.showReviewSelectedClients(accessGroup._id.toString, Some(3), Some(1)).url

      }

    }
  }

  s"POST submitReviewSelectedClients on url${ctrlRoute.submitReviewSelectedClients(grpId).url}" should {

    s"redirect to showGroupClientsUpdatedConfirmation on url '${ctrlRoute.showGroupClientsUpdatedConfirmation(grpId)}' " +
      s"when page is submitted with answer 'NO'/'false'" in {

      expectAuthOkOptedInReady()

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitReviewSelectedClients(grpId)}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(SELECTED_CLIENTS, Seq(displayClients.head, displayClients.last))
      expectGetCustomSummaryById(grpId, Some(GroupSummary.fromAccessGroup(accessGroup)))
      expectUpdateGroup(grpId,
        UpdateAccessGroupRequest(clients = Some(Set(displayClients.head, displayClients.last).map(dc => Client(dc.enrolmentKey, dc.name))))
      )

      val result = controller.submitReviewSelectedClients(grpId)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute
        .showGroupClientsUpdatedConfirmation(grpId).url
    }

    s"redirect to showSearchClientsToAdd on url '${ctrlRoute.showSearchClientsToAdd(grpId)}' " +
      s"when page is submitted with answer 'YES'/'true'" in {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitReviewSelectedClients(grpId)}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()

      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectGetCustomSummaryById(grpId, Some(GroupSummary.fromAccessGroup(accessGroup)))

      val result = controller.submitReviewSelectedClients(grpId)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute
        .showSearchClientsToAdd(grpId).url
    }

    s"render errors when no radio button selected" in {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitReviewSelectedClients(grpId)}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectGetCustomSummaryById(grpId, Some(GroupSummary.fromAccessGroup(accessGroup)))

      val result = controller.submitReviewSelectedClients(grpId)(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Review selected clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 3 clients"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you need to add or remove selected clients"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you need to add or remove selected clients"

    }
  }

  s"GET showGroupClientsUpdatedConfirmation on ${ctrlRoute.showGroupClientsUpdatedConfirmation(grpId).url}" should {

    "render correctly" in {
      //given
      expectAuthOkOptedInReady()

      expectGetCustomSummaryById(grpId, Some(GroupSummary.fromAccessGroup(accessGroup)))
      expectGetSessionItem(SELECTED_CLIENTS, displayClients)
      expectDeleteSessionItem(SELECTED_CLIENTS)

      //when
      val result = controller.showGroupClientsUpdatedConfirmation(grpId)(request)

      //then
      status(result) shouldBe OK
      // selected clients should be cleared from session

      //and
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Bananas access group clients updated - Agent services account - GOV.UK"
      html.select(Css.confirmationPanelH1).text() shouldBe "Bananas access group clients updated"
      html.select(Css.H2).text() shouldBe "What happens next"
      html.select(Css.paragraphs).get(0).text() shouldBe "You have changed the clients that can be managed by the team members in this access group."
      html.select("a#returnToDashboard").text() shouldBe "Return to manage access groups"
      html.select("a#returnToDashboard").attr("href") shouldBe routes.ManageGroupController.showManageGroups(None,None).url
      html.select(Css.backLink).size() shouldBe 0
    }

    s"redirect to ${ctrlRoute.showSearchClientsToAdd(grpId)} when there are no selected clients" in {

      expectAuthOkOptedInReady()
      expectGetCustomSummaryById(grpId, Some(GroupSummary.fromAccessGroup(accessGroup)))

      expectGetSessionItemNone(SELECTED_CLIENTS)


      //when
      val result = controller.showGroupClientsUpdatedConfirmation(grpId)(request)

      //then
      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showSearchClientsToAdd(grpId).url
    }
  }


}
