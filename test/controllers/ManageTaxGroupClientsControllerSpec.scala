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
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.actions.AuthAction
import helpers.Css.H2
import helpers.{BaseSpec, Css}
import models.{DisplayClient, TeamMember}
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import org.mongodb.scala.bson.ObjectId
import play.api.Application
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services.{ClientService, GroupService, SessionCacheService, TaxGroupService}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

import java.time.LocalDate
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
  val accessGroup: CustomGroup = CustomGroup(new ObjectId(),
    arn,
    "Bananas",
    LocalDate.of(2020, 3, 10).atStartOfDay(),
    null,
    agentUser,
    agentUser,
    None,
    None)

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

  val displayClients: Seq[DisplayClient] = fakeClients.map(DisplayClient.fromClient(_))

  val encodedDisplayClients: Seq[String] = displayClients.map(client =>
    Base64.getEncoder.encodeToString(Json.toJson(client).toString.getBytes))

  val agentUsers: Set[AgentUser] = (1 to 5).map(i => AgentUser(id = s"John $i", name = s"John $i name")).toSet

  val userDetails: Seq[UserDetails] = (1 to 5)
    .map { i =>
      UserDetails(Some(s"John $i"), Some("User"), Some(s"John $i name"), Some(s"john$i@abc.com"))
    }

  val teamMembers: Seq[TeamMember] = userDetails.map(TeamMember.fromUserDetails)

  val controller: ManageTaxGroupClientsController = fakeApplication.injector.instanceOf[ManageTaxGroupClientsController]
  private val grpId: String = accessGroup._id.toString

  val enrolmentKey: String = "HMRC-MTD-VAT~VRN~123456780"
  private val ctrlRoute: ReverseManageTaxGroupClientsController = routes.ManageTaxGroupClientsController

  def expectAuthOkOptedInReady(): Unit = {
    expectAuthorisationGrantsAccess(mockedAuthResponse)
    expectIsArnAllowed(allowed = true)
    expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
  }

  s"GET ${ctrlRoute.showExistingGroupClients(grpId, None, None).url}" should {

    "render correctly the first page of CLIENTS in tax group, with no query params" in {
      //given
      expectAuthOkOptedInReady()
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      expectPutSessionItem(CLIENT_FILTER_INPUT, taxGroup.service)

      expectGetPageOfClients(taxGroup.arn, 1, 20)(displayClients)

      //when
      val result = controller.showExistingGroupClients(taxGroup._id.toString, None, None)(request)

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
    }

    "render with searchTerm set" in {
      //given
      expectAuthOkOptedInReady()
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      expectPutSessionItem(CLIENT_FILTER_INPUT, taxGroup.service)

      expectPutSessionItem(CLIENT_SEARCH_INPUT, "friendly1")
      expectGetPageOfClients(taxGroup.arn, 1, 20)(displayClients.take(1))

      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showExistingGroupClients(taxGroup._id.toString, None, None).url +
          "?submit=filter&search=friendly1&filter=HMRC-MTD-VAT"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result =
        controller.showExistingGroupClients(taxGroup._id.toString, None, None)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Filter results for 'friendly1' Manage clients - Bananas - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Manage clients in this group"
      html.select(H2).text shouldBe "Filter results for 'friendly1'"

      val th = html.select(Css.tableWithId("clients")).select("thead th")
      th.size() shouldBe 3
      val trs = html.select(Css.tableWithId("clients")).select("tbody tr")
      trs.size() shouldBe 1
    }

    "render with search that matches nothing" in {
      //given
      expectAuthOkOptedInReady()
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      expectPutSessionItem(CLIENT_FILTER_INPUT, taxGroup.service)
      expectPutSessionItem(CLIENT_SEARCH_INPUT, "nothing") //not matching
      expectGetPageOfClients(taxGroup.arn, 1, 20)(Seq.empty[DisplayClient])

      val NON_MATCHING_SEARCH = "nothing"

      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showExistingGroupClients(taxGroup._id.toString, None, None).url +
          s"?submit=$FILTER_BUTTON&search=$NON_MATCHING_SEARCH&filter=HMRC-MTD-VAT"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showExistingGroupClients(taxGroup._id.toString, None, None)(requestWithQueryParams)

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
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      expectDeleteSessionItem(CLIENT_SEARCH_INPUT)

      //and we have CLEAR filter in query params
      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showExistingGroupClients(taxGroup._id.toString, None, None).url +
          s"?submit=$CLEAR_BUTTON"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result =
        controller.showExistingGroupClients(taxGroup._id.toString, None, None)(requestWithQueryParams)

      //then
      redirectLocation(result).get
        .shouldBe(ctrlRoute.showExistingGroupClients(taxGroup._id.toString, Some(1), Some(20)).url)
    }


    "redirect to new page when a pagination button is clicked" in {
      //given
      expectAuthOkOptedInReady()
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))

      val pageNumber = 2
      //and we have PAGINATION_BUTTON filter in query params
      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showExistingGroupClients(taxGroup._id.toString, None, None).url +
          s"?submit=${PAGINATION_BUTTON}_$pageNumber"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result =
        controller.showExistingGroupClients(taxGroup._id.toString, None, None)(requestWithQueryParams)

      //then
      redirectLocation(result).get
        .shouldBe(ctrlRoute.showExistingGroupClients(taxGroup._id.toString, Some(pageNumber), Some(20)).url)
    }

  }

}
