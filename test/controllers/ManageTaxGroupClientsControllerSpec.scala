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
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector, UpdateTaxServiceGroupRequest}
import controllers.actions.AuthAction
import helpers.Css._
import helpers.{BaseSpec, Css}
import models.DisplayClient.{fromClient, toClient}
import models.{DisplayClient, TeamMember}
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import play.api.Application
import play.api.Play.materializer
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services.{ClientService, GroupService, SessionCacheService, TaxGroupService}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

import java.time.LocalDateTime.MIN
import java.util.Base64

class ManageTaxGroupClientsControllerSpec extends BaseSpec {


  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector =
    mock[AgentUserClientDetailsConnector]
  implicit val groupService: GroupService = mock[GroupService]
  implicit val taxGroupService: TaxGroupService = mock[TaxGroupService]
  implicit val mockClientService: ClientService = mock[ClientService]
  implicit val mockSessionCacheService: SessionCacheService = mock[SessionCacheService]

  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

  private val agentUser: AgentUser = AgentUser(RandomStringUtils.random(5), "Rob the Agent")

  val taxGroup: TaxGroup
  = TaxGroup(arn, "Bananas", MIN, MIN, agentUser, agentUser, None, "HMRC-MTD-VAT", automaticUpdates = true, None)

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[SessionCacheService]).toInstance(mockSessionCacheService)
      bind(classOf[ClientService]).toInstance(mockClientService)
      bind(classOf[TaxGroupService]).toInstance(taxGroupService)
      bind(classOf[GroupService]).toInstance(groupService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder.configure("mongodb.uri" -> mongoUri).build()

  val fakeClients: Seq[Client] =
    List.tabulate(3)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))
  val displayClients: Seq[DisplayClient] = fakeClients.map(fromClient(_))

  private val chars: List[Char] = List.range('a', 'z')
  val excludedClients = chars.map(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"John $i")).toSet
  val excludedDisplayClients = excludedClients.map(fromClient(_))

  val encodedDisplayClients: Seq[String] = displayClients.map(client =>
    Base64.getEncoder.encodeToString(Json.toJson(client).toString.getBytes))

  val agentUsers: Set[AgentUser] = (1 to 5).map(i => AgentUser(id = s"John $i", name = s"John $i name")).toSet

  val userDetails: Seq[UserDetails] = (1 to 5)
    .map { i =>
      UserDetails(Some(s"John $i"), Some("User"), Some(s"John $i name"), Some(s"john$i@abc.com"))
    }

  val teamMembers: Seq[TeamMember] = userDetails.map(TeamMember.fromUserDetails)

  val controller: ManageTaxGroupClientsController = fakeApplication.injector.instanceOf[ManageTaxGroupClientsController]

  val enrolmentKey: String = "HMRC-MTD-VAT~VRN~123456780"
  private val ctrlRoute: ReverseManageTaxGroupClientsController = routes.ManageTaxGroupClientsController
  val taxGroupId = taxGroup._id.toString

  def expectAuthOkOptedInReady(): Unit = {
    expectAuthorisationGrantsAccess(mockedAuthResponse)
    expectIsArnAllowed(allowed = true)
    expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
  }

  s"GET ${ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url}" should {

    "render correctly the first page of CLIENTS in tax group, with no query params" in {
      //given
      expectAuthOkOptedInReady()
      val taxGroupWithExcluded = taxGroup.copy(excludedClients = Some(Set(fakeClients(0))))
      expectGetTaxGroupById(taxGroupId, Some(taxGroupWithExcluded))
      expectPutSessionItem(CLIENT_FILTER_INPUT, taxGroup.service)
      expectGetPageOfClients(taxGroup.arn, 1, 20)(displayClients)

      //when
      val result = controller.showExistingGroupClients(taxGroupWithExcluded._id.toString, None, None)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Manage clients - Bananas - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Manage clients in this group"

      val ths = html.select(Css.tableWithId("clients")).select("thead th")
      ths.size() shouldBe 4
      ths.get(0).text() shouldBe "Client reference"
      ths.get(1).text() shouldBe "Tax reference"
      ths.get(2).text() shouldBe "Tax service"
      ths.get(3).text() shouldBe "Actions"

      val trs = html.select(Css.tableWithId("clients")).select("tbody tr")

      trs.size() shouldBe 3

      //first row
      val row1Cells = trs.get(0).select("td")
      row1Cells.get(0).text() shouldBe "friendly0"
      row1Cells.get(1).text() shouldBe "ending in 6780"
      row1Cells.get(2).text() shouldBe "VAT"
      row1Cells.get(3).text() shouldBe "Client excluded"

      //last row
      val row3Cells = trs.get(2).select("td")
      row3Cells.get(0).text() shouldBe "friendly2"
      row3Cells.get(1).text() shouldBe "ending in 6782"
      row3Cells.get(2).text() shouldBe "VAT"
      row3Cells.get(3).select("a").attr("href") shouldBe ctrlRoute.showConfirmRemoveClient(taxGroupId, displayClients(2).id).url
      row3Cells.get(3).text() shouldBe "Remove"

      //and view removed clients button should be present
      val viewRemovedClientsButton = html.select(linkStyledAsButtonWithId("view-excluded-clients"))
      viewRemovedClientsButton.text shouldBe "View removed clients"
      viewRemovedClientsButton.attr("href") shouldBe ctrlRoute.showExcludedClients(taxGroupId, None, None).url
    }

    "render excluded/non excluded correctly with searchTerm set" in {
      //given
      expectAuthOkOptedInReady()
      val taxGroupWithExcluded = taxGroup.copy(excludedClients = Some(excludedClients.take(2)))
      expectGetTaxGroupById(taxGroupId, Some(taxGroupWithExcluded))
      expectPutSessionItem(CLIENT_FILTER_INPUT, taxGroup.service)
      expectPutSessionItem(CLIENT_SEARCH_INPUT, "friendly1")
      expectGetPageOfClients(taxGroup.arn, 1, 20)(excludedDisplayClients.take(4).toSeq)

      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url +
          "?submit=filter&search=friendly1&filter=HMRC-MTD-VAT"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result =
        controller.showExistingGroupClients(taxGroupId, None, None)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Filter results for 'friendly1' Manage clients - Bananas - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Manage clients in this group"
      html.select(H2).text shouldBe "Filter results for 'friendly1'"

      val th = html.select(Css.tableWithId("clients")).select("thead th")
      th.size() shouldBe 4
      val trs = html.select(Css.tableWithId("clients")).select("tbody tr")
      trs.size() shouldBe 4

      val row1Cells = trs.get(0).select("td")
      row1Cells.get(0).text() shouldBe "John u"
      row1Cells.get(3).text() shouldBe "Remove"

      val row3Cells = trs.get(3).select("td")
      row3Cells.get(0).text() shouldBe "John x"
      row3Cells.get(3).text() shouldBe "Client excluded"
    }

    "render with search that matches nothing" in {
      //given
      expectAuthOkOptedInReady()
      expectGetTaxGroupById(taxGroupId, Some(taxGroup))
      expectPutSessionItem(CLIENT_FILTER_INPUT, taxGroup.service)
      expectPutSessionItem(CLIENT_SEARCH_INPUT, "nothing") //not matching
      expectGetPageOfClients(taxGroup.arn, 1, 20)(Seq.empty[DisplayClient])

      val NON_MATCHING_SEARCH = "nothing"

      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url +
          s"?submit=$FILTER_BUTTON&search=$NON_MATCHING_SEARCH&filter=HMRC-MTD-VAT"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showExistingGroupClients(taxGroupId, None, None)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Filter results for 'nothing' Manage clients - Bananas - Agent services account - GOV.UK"
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
      expectAuthOkOptedInReady()
      expectGetTaxGroupById(taxGroupId, Some(taxGroup))
      expectDeleteSessionItem(CLIENT_SEARCH_INPUT)

      //and we have CLEAR filter in query params
      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url +
          s"?submit=$CLEAR_BUTTON"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result =
        controller.showExistingGroupClients(taxGroupId, None, None)(requestWithQueryParams)

      //then
      redirectLocation(result).get
        .shouldBe(ctrlRoute.showExistingGroupClients(taxGroupId, Some(1), Some(20)).url)
    }

    "redirect to new page when a pagination button is clicked" in {
      //given
      expectAuthOkOptedInReady()
      expectGetTaxGroupById(taxGroupId, Some(taxGroup))

      val pageNumber = 2
      //and we have PAGINATION_BUTTON filter in query params
      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url +
          s"?submit=${PAGINATION_BUTTON}_$pageNumber"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result =
        controller.showExistingGroupClients(taxGroupId, None, None)(requestWithQueryParams)

      //then
      redirectLocation(result).get
        .shouldBe(ctrlRoute.showExistingGroupClients(taxGroupId, Some(pageNumber), Some(20)).url)
    }

    val clientToRemove: DisplayClient = displayClients.head

    s"GET ${ctrlRoute.showConfirmRemoveClient(taxGroupId, clientToRemove.id).url}" should {

      "render the confirm remove client page" in {

        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupId, Some(taxGroup))
        expectLookupClient(taxGroup.arn)(clientToRemove)
        expectPutSessionItem(CLIENT_TO_REMOVE, clientToRemove)

        val result = controller.showConfirmRemoveClient(taxGroupId, clientToRemove.id)(request)
        // then
        status(result) shouldBe OK

        val html = Jsoup.parse(contentAsString(result))

        html.title() shouldBe "Remove friendly0 from selected clients? - Agent services account - GOV.UK"
        html.select(Css.H1).text() shouldBe "Remove friendly0 from selected clients?"
        html.select(backLink)
          .attr("href") shouldBe ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url


        html.select(Css.form).attr("action") shouldBe ctrlRoute.submitConfirmRemoveClient(taxGroupId, clientToRemove.id).url
        html.select("label[for=answer]").text() shouldBe "Yes"
        html.select("label[for=answer-no]").text() shouldBe "No"
        html.select(Css.form + " input[name=answer]").size() shouldBe 2
        html.select(Css.submitButton).text() shouldBe "Save and continue"

      }

      "when client not found render existing group clients" in {

        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupId, Some(taxGroup))
        expectLookupClientNone(taxGroup.arn)

        val result = controller.showConfirmRemoveClient(taxGroupId, clientToRemove.id)(request)
        // then
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url
      }

    }

    s"POST ${ctrlRoute.submitConfirmRemoveClient(taxGroupId, clientToRemove.enrolmentKey).url}" should {

      "confirm remove client 'yes' removes  from group and redirect to group clients list" in {

        val updatePayload = UpdateTaxServiceGroupRequest(excludedClients = Some(Set(DisplayClient.toClient(clientToRemove))))
        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupId, Some(taxGroup))
        expectGetSessionItem(CLIENT_TO_REMOVE, clientToRemove)
        expectUpdateTaxGroup(taxGroupId, updatePayload)

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", s"${controller.submitConfirmRemoveClient(taxGroupId, clientToRemove.enrolmentKey)}")
            .withFormUrlEncodedBody("answer" -> "true")
            .withSession(SessionKeys.sessionId -> "session-x")


        val result = controller.submitConfirmRemoveClient(taxGroupId, clientToRemove.enrolmentKey)(request)

        status(result) shouldBe SEE_OTHER

        redirectLocation(result).get shouldBe ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url
      }

      "confirm remove client 'no' redirects to group clients list" in {

        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupId, Some(taxGroup))
        expectGetSessionItem(CLIENT_TO_REMOVE, clientToRemove)

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", s"${controller.submitConfirmRemoveClient(taxGroupId, clientToRemove.enrolmentKey)}")
            .withFormUrlEncodedBody("answer" -> "false")
            .withSession(SessionKeys.sessionId -> "session-x")


        val result = controller.submitConfirmRemoveClient(taxGroupId, clientToRemove.enrolmentKey)(request)

        status(result) shouldBe SEE_OTHER

        redirectLocation(result).get shouldBe ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url
      }

      "render errors when no selections of yes/no made" in {

        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupId, Some(taxGroup))
        expectGetSessionItem(CLIENT_TO_REMOVE, clientToRemove)

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", s"${controller.submitConfirmRemoveClient(taxGroupId, clientToRemove.enrolmentKey)}")
            .withFormUrlEncodedBody("ohai" -> "blah")
            .withSession(SessionKeys.sessionId -> "session-x")

        //when
        val result = controller.submitConfirmRemoveClient(taxGroupId, clientToRemove.enrolmentKey)(request)

        //then
        status(result) shouldBe OK

        val html = Jsoup.parse(contentAsString(result))
        html.title() shouldBe "Error: Remove friendly0 from selected clients? - Agent services account - GOV.UK"
        html.select(Css.H1).text() shouldBe "Remove friendly0 from selected clients?"
        html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you need to remove this client from the access group"
        html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you need to remove this client from the access group"

      }

      "redirect when client not found" in {

        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupId, Some(taxGroup))
        expectGetSessionItemNone(CLIENT_TO_REMOVE)

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", s"${controller.submitConfirmRemoveClient(taxGroupId, clientToRemove.enrolmentKey)}")
            .withFormUrlEncodedBody("ohai" -> "blah")
            .withSession(SessionKeys.sessionId -> "session-x")

        //when
        val result = controller.submitConfirmRemoveClient(taxGroupId, clientToRemove.enrolmentKey)(request)

        //then
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url

      }
    }

    val taxGroupWithExcluded: TaxGroup = TaxGroup(arn, "Bananas", MIN, MIN, agentUser, agentUser,
      None, "HMRC-MTD-VAT", automaticUpdates = true, Some(excludedClients))
    val taxGroupWithExcludedId = taxGroupWithExcluded._id.toString

    s"GET excluded clients" should {

      "render message when no excluded clients in group" in {
        // given
        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupId, Some(taxGroup))
        expectGetSessionItemNone(SELECTED_CLIENTS)
        expectGetSessionItemNone(CLIENT_SEARCH_INPUT)

        //when
        val result = controller.showExcludedClients(taxGroupId, None, None)(request)

        //then
        status(result) shouldBe OK

        val html = Jsoup.parse(contentAsString(result))
        html.title() shouldBe "Removed clients - Agent services account - GOV.UK"
        html.select(H1).text() shouldBe "Removed clients"
        html.select(paragraphs).text() shouldBe "There are no excluded clients for this group"
        html.select(backLink).attr("href") shouldBe ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url
        html.select(linkStyledAsButtonWithId("button-link")).attr("href") shouldBe ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url
      }

      "render message when no excluded clients is present but empty" in {
        // given
        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupId, Some(taxGroup))
        expectGetSessionItem(SELECTED_CLIENTS, Seq.empty[DisplayClient])
        expectGetSessionItemNone(CLIENT_SEARCH_INPUT)

        //when
        val result = controller.showExcludedClients(taxGroupId, None, None)(request)

        //then
        status(result) shouldBe OK

        val html = Jsoup.parse(contentAsString(result))
        html.title() shouldBe "Removed clients - Agent services account - GOV.UK"
        html.select(H1).text() shouldBe "Removed clients"
        html.select(paragraphs).text() shouldBe "There are no excluded clients for this group"
        html.select(backLink).attr("href") shouldBe ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url
        html.select(linkStyledAsButtonWithId("button-link")).attr("href") shouldBe ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url
      }

      "render excluded clients list for the group" in {
        // given
        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupWithExcludedId, Some(taxGroupWithExcluded))
        expectGetSessionItemNone(SELECTED_CLIENTS)
        expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
        val currentPageOfClients = excludedClients.map(fromClient(_)).toSeq.sortBy(_.name).take(10)
        expectPutSessionItem(CURRENT_PAGE_CLIENTS, currentPageOfClients)

        //when
        val result = controller.showExcludedClients(taxGroupWithExcludedId, None, None)(request)

        //then
        status(result) shouldBe OK

        val html = Jsoup.parse(contentAsString(result))
        html.title() shouldBe "Removed clients - Agent services account - GOV.UK"
        html.select(H1).text() shouldBe "Removed clients"
        html.select(backLink).attr("href") shouldBe ctrlRoute.showExistingGroupClients(taxGroupWithExcludedId, None, None).url

        val ths = html.select(Css.tableWithId("multi-select-table")).select("thead th")
        ths.size() shouldBe 4
        ths.get(0).text shouldBe ""
        ths.get(1).text shouldBe "Client reference"
        ths.get(2).text shouldBe "Tax reference"
        ths.get(3).text shouldBe "Tax service"
        val trs = html.select(Css.tableWithId("multi-select-table")).select("tbody tr")
        trs.size() shouldBe 10

        val row1Cells = trs.get(0).select("td")
        val row1Checkbox = row1Cells.get(0).select("input")
        row1Checkbox.attr("name") shouldBe "clients[]"
        row1Checkbox.attr("value") shouldBe currentPageOfClients(0).id
        row1Cells.get(1).text shouldBe "John a"
        row1Cells.get(1).text shouldBe currentPageOfClients(0).name
        row1Cells.get(2).text shouldBe "ending in 678a"
        row1Cells.get(3).text shouldBe "VAT"

        val row10Cells = trs.get(9).select("td")
        val row10Checkbox = row10Cells.get(0).select("input")
        row10Checkbox.attr("name") shouldBe "clients[]"
        row10Checkbox.attr("value") shouldBe currentPageOfClients(9).id
        row10Cells.get(1).text shouldBe "John j"
        row10Cells.get(1).text shouldBe currentPageOfClients(9).name
        row10Cells.get(2).text shouldBe "ending in 678j"
        row10Cells.get(3).text shouldBe "VAT"

      }

      "render excluded clients with search term filtering" in {
        // given
        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupWithExcludedId, Some(taxGroupWithExcluded))
        expectGetSessionItemNone(SELECTED_CLIENTS)
        expectGetSessionItem(CLIENT_SEARCH_INPUT, "john a")
        val currentPageOfClients = excludedClients.map(fromClient(_)).filter(dc => dc.name.equalsIgnoreCase("john a")).toSeq.sortBy(_.name).take(10)
        expectPutSessionItem(CURRENT_PAGE_CLIENTS, currentPageOfClients)

        //when
        val result = controller.showExcludedClients(taxGroupWithExcludedId, None, None)(request)

        //then
        status(result) shouldBe OK

        val html = Jsoup.parse(contentAsString(result))
        html.title() shouldBe "Filter results for 'john a' Removed clients - Agent services account - GOV.UK"
        html.select(H1).text() shouldBe "Removed clients"
        html.select(backLink).attr("href") shouldBe ctrlRoute.showExistingGroupClients(taxGroupWithExcludedId, None, None).url

        val ths = html.select(Css.tableWithId("multi-select-table")).select("thead th")
        ths.size() shouldBe 4
        ths.get(0).text shouldBe ""
        ths.get(1).text shouldBe "Client reference"
        ths.get(2).text shouldBe "Tax reference"
        ths.get(3).text shouldBe "Tax service"
        val trs = html.select(Css.tableWithId("multi-select-table")).select("tbody tr")
        trs.size() shouldBe 1

        val row1Cells = trs.get(0).select("td")
        val row1Checkbox = row1Cells.get(0).select("input")
        row1Checkbox.attr("name") shouldBe "clients[]"
        row1Checkbox.attr("value") shouldBe currentPageOfClients(0).id
        row1Cells.get(1).text shouldBe "John a"
        row1Cells.get(1).text shouldBe currentPageOfClients(0).name
        row1Cells.get(2).text shouldBe "ending in 678a"
        row1Cells.get(3).text shouldBe "VAT"

      }

    }

    s"POST to submitUnexcludeClients" should {

      "Remove clients from the excluded list and therefore put them back in the group" in {

        val currentPageOfClients = excludedClients.map(fromClient(_)).toSeq.sortBy(_.name).take(10)
        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", ctrlRoute.submitUnexcludeClients(taxGroupWithExcludedId).url)
            .withFormUrlEncodedBody(
              "clients[0]" -> currentPageOfClients.head.id,
              "clients[1]" -> currentPageOfClients.last.id,
              "search" -> "",
              "filter" -> "",
              "submit" -> CONTINUE_BUTTON
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        val updatePayload = UpdateTaxServiceGroupRequest(
          excludedClients = Some(excludedClients -- Set(currentPageOfClients.head, currentPageOfClients.last).map(toClient(_)))
        )
        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupWithExcludedId, Some(taxGroupWithExcluded))
        expectGetSessionItemNone(SELECTED_CLIENTS)
        expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
        expectGetSessionItem(CURRENT_PAGE_CLIENTS, currentPageOfClients)
        expectUpdateTaxGroup(taxGroupWithExcludedId, updatePayload)
        expectDeleteSessionItem(SELECTED_CLIENTS)

        val result = controller.submitUnexcludeClients(taxGroupWithExcludedId)()(request)

        status(result) shouldBe SEE_OTHER

        redirectLocation(result).get shouldBe ctrlRoute.showExcludedClients(taxGroupWithExcludedId, None, None).url
      }

      "Remove clients from the excluded list EVEN if current page has no selected but there are saved selected" in {

        val currentPageOfClients = excludedClients.map(fromClient(_)).toSeq.sortBy(_.name).take(10)
        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", ctrlRoute.submitUnexcludeClients(taxGroupWithExcludedId).url)
            .withFormUrlEncodedBody(
              "search" -> "",
              "filter" -> "",
              "submit" -> CONTINUE_BUTTON
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        val alreadySelectedToUnexclude = excludedDisplayClients.takeRight(1)
        val updatePayload = UpdateTaxServiceGroupRequest(
          excludedClients = Some(excludedClients -- alreadySelectedToUnexclude.map(x => toClient(x)))
        )
        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupWithExcludedId, Some(taxGroupWithExcluded))
        expectGetSessionItem(SELECTED_CLIENTS, alreadySelectedToUnexclude.toSeq)
        expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
        expectGetSessionItem(CURRENT_PAGE_CLIENTS, currentPageOfClients)
        expectUpdateTaxGroup(taxGroupWithExcludedId, updatePayload)
        expectDeleteSessionItem(SELECTED_CLIENTS)

        val result = controller.submitUnexcludeClients(taxGroupWithExcludedId)()(request)

        status(result) shouldBe SEE_OTHER

        redirectLocation(result).get shouldBe ctrlRoute.showExcludedClients(taxGroupWithExcludedId, None, None).url
      }

      "save clients to unexclude, and redirect to when button is FILTER (i.e. not CONTINUE)" in {

        val currentPageOfClients = excludedClients.map(fromClient(_)).toSeq.sortBy(_.name).take(10)
        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST",
            ctrlRoute.submitUnexcludeClients(taxGroupWithExcludedId).url
          ).withFormUrlEncodedBody(
            "clients[0]" -> excludedDisplayClients.head.id,
            "clients[1]" -> excludedDisplayClients.last.id,
            "search" -> "john",
            "submit" -> FILTER_BUTTON
          ).withSession(SessionKeys.sessionId -> "session-x")

        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupWithExcludedId, Some(taxGroupWithExcluded))
        expectGetSessionItemNone(SELECTED_CLIENTS)
        expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
        expectGetSessionItem(CURRENT_PAGE_CLIENTS, currentPageOfClients)
        expectPutSessionItem(CLIENT_SEARCH_INPUT, "john")

        val result = controller.submitUnexcludeClients(taxGroupWithExcludedId)()(request)

        status(result) shouldBe SEE_OTHER

        redirectLocation(result).get shouldBe s"${ctrlRoute.showExcludedClients(taxGroupWithExcludedId, None, None).url}"


      }

      "save clients to unexclude, and redirect to when button is CLEAR" in {

        val currentPageOfClients = excludedClients.map(fromClient(_)).toSeq.sortBy(_.name).take(10)
        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST",
            ctrlRoute.submitUnexcludeClients(taxGroupWithExcludedId).url
          ).withFormUrlEncodedBody(
            "clients[0]" -> excludedDisplayClients.head.id,
            "clients[1]" -> excludedDisplayClients.last.id,
            "submit" -> CLEAR_BUTTON
          ).withSession(SessionKeys.sessionId -> "session-x")

        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupWithExcludedId, Some(taxGroupWithExcluded))
        expectGetSessionItemNone(SELECTED_CLIENTS)
        expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
        expectGetSessionItem(CURRENT_PAGE_CLIENTS, currentPageOfClients)
        expectDeleteSessionItem(CLIENT_SEARCH_INPUT)

        val result = controller.submitUnexcludeClients(taxGroupWithExcludedId)()(request)

        status(result) shouldBe SEE_OTHER

        redirectLocation(result).get shouldBe s"${ctrlRoute.showExcludedClients(taxGroupWithExcludedId, Some(1), Some(10)).url}"


      }

      "save clients to unexclude, and redirect to when button is PAGINATION" in {

        val currentPageOfClients = excludedClients.map(fromClient(_)).toSeq.sortBy(_.name).take(10)
        val page = 2
        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST",
            ctrlRoute.submitUnexcludeClients(taxGroupWithExcludedId).url
          ).withFormUrlEncodedBody(
            "clients[0]" -> currentPageOfClients.head.id,
            "clients[1]" -> currentPageOfClients.last.id,
            "submit" -> s"${PAGINATION_BUTTON}_$page"
          ).withSession(SessionKeys.sessionId -> "session-x")

        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupWithExcludedId, Some(taxGroupWithExcluded))
        expectGetSessionItemNone(SELECTED_CLIENTS)
        expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
        expectGetSessionItem(CURRENT_PAGE_CLIENTS, currentPageOfClients)
        expectPutSessionItem(SELECTED_CLIENTS, Seq(currentPageOfClients.head, currentPageOfClients.last))

        val result = controller.submitUnexcludeClients(taxGroupWithExcludedId)()(request)

        status(result) shouldBe SEE_OTHER

        redirectLocation(result).get shouldBe s"${ctrlRoute.showExcludedClients(taxGroupWithExcludedId, Some(page), Some(10)).url}"


      }

      "present page with errors when form validation fails" in {

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", ctrlRoute.submitUnexcludeClients(taxGroupWithExcludedId).url)
            .withFormUrlEncodedBody(
              "search" -> "",
              "filter" -> "",
              "submit" -> CONTINUE_BUTTON
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupWithExcludedId, Some(taxGroupWithExcluded))
        expectGetSessionItemNone(SELECTED_CLIENTS)
        expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
        val currentPageOfClients = excludedClients.map(fromClient(_)).toSeq.sortBy(_.name).take(10)
        expectGetSessionItem(CURRENT_PAGE_CLIENTS, currentPageOfClients)

        val result = controller.submitUnexcludeClients(taxGroupWithExcludedId)()(request)

        status(result) shouldBe OK

      }

      "redirect when this group has NO EXCLUDED clients" in {

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", ctrlRoute.submitUnexcludeClients(taxGroupId).url)
            .withFormUrlEncodedBody(
              "search" -> "",
              "filter" -> "",
              "submit" -> CONTINUE_BUTTON
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupId, Some(taxGroup))
        expectGetSessionItemNone(SELECTED_CLIENTS)
        expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
        val currentPageOfClients = excludedClients.map(fromClient(_)).toSeq.sortBy(_.name).take(10)
        expectGetSessionItem(CURRENT_PAGE_CLIENTS, currentPageOfClients)

        val result = controller.submitUnexcludeClients(taxGroupId)()(request)

        status(result) shouldBe OK
        val html = Jsoup.parse(contentAsString(result))
        html.title() shouldBe "Removed clients - Agent services account - GOV.UK"
        html.select(H1).text() shouldBe "Removed clients"
        html.select(paragraphs).text() shouldBe "There are no excluded clients for this group"
        html.select(backLink).attr("href") shouldBe ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url
        html.select(linkStyledAsButtonWithId("button-link")).attr("href") shouldBe ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url

      }

      "redirect when this group has NO EXCLUDED clients even if form data seems valid" in {

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", ctrlRoute.submitUnexcludeClients(taxGroupId).url)
            .withFormUrlEncodedBody(
              "search" -> "",
              "clients[0]" -> displayClients.head.id,
              "clients[1]" -> displayClients.last.id,
              "filter" -> "",
              "submit" -> CONTINUE_BUTTON
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        expectAuthOkOptedInReady()
        expectGetTaxGroupById(taxGroupId, Some(taxGroup))
        expectGetSessionItemNone(SELECTED_CLIENTS)
        expectGetSessionItemNone(CLIENT_SEARCH_INPUT)
        expectGetSessionItemNone(CURRENT_PAGE_CLIENTS)

        val result = controller.submitUnexcludeClients(taxGroupId)()(request)

        status(result) shouldBe OK
        val html = Jsoup.parse(contentAsString(result))
        html.title() shouldBe "Removed clients - Agent services account - GOV.UK"
        html.select(H1).text() shouldBe "Removed clients"
        html.select(paragraphs).text() shouldBe "There are no excluded clients for this group"
        html.select(backLink).attr("href") shouldBe ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url
        html.select(linkStyledAsButtonWithId("button-link")).attr("href") shouldBe ctrlRoute.showExistingGroupClients(taxGroupId, None, None).url

      }
    }
  }

}
