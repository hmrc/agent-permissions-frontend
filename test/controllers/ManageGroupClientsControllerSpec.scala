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
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector, UpdateAccessGroupRequest}
import helpers.Css._
import helpers.{BaseSpec, Css}
import models.{DisplayClient, TeamMember}
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import org.mongodb.scala.bson.ObjectId
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, await, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services.{GroupService, GroupServiceImpl}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

import java.time.LocalDate
import java.util.Base64

class ManageGroupClientsControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector
    : AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val groupService: GroupService = new GroupServiceImpl(mockAgentUserClientDetailsConnector, sessionCacheRepo, mockAgentPermissionsConnector)

  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)
  val groupId = "xyz"
  private val agentUser: AgentUser =
    AgentUser(RandomStringUtils.random(5), "Rob the Agent")
  val accessGroup: AccessGroup = AccessGroup(new ObjectId(),
                                arn,
                                "Bananas",
                                LocalDate.of(2020, 3, 10).atStartOfDay(),
                                null,
                                agentUser,
                                agentUser,
                                None,
                                None)

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

  val fakeClients: Seq[Client] =
    List.tabulate(3)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))

  val displayClients: Seq[DisplayClient] =
    fakeClients.map(DisplayClient.fromClient(_))

  val encodedDisplayClients: Seq[String] = displayClients.map(client =>
    Base64.getEncoder.encodeToString(Json.toJson(client).toString.getBytes))

  val agentUsers: Set[AgentUser] = (1 to 5).map(i => AgentUser(id = s"John $i", name = s"John $i name")).toSet

  val userDetails: Seq[UserDetails] = (1 to 5)
    .map { i =>
      UserDetails(
        Some(s"John $i"),
        Some("User"),
        Some(s"John $i name"),
        Some(s"john$i@abc.com")
      )
    }

  val teamMembers: Seq[TeamMember] = userDetails.map(TeamMember.fromUserDetails)

  val controller: ManageGroupClientsController = fakeApplication.injector.instanceOf[ManageGroupClientsController]

  s"GET ${routes.ManageGroupClientsController.showExistingGroupClients(accessGroup._id.toString).url}" should {

    "render correctly the EXISTING CLIENTS page with no query params" in {
      //given
      val groupWithClients = accessGroup.copy(clients =
        Some(displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      expectGetGroupSuccess(accessGroup._id.toString, Some(groupWithClients))


      //when
      val result =
        controller.showExistingGroupClients(groupWithClients._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Manage clients - Bananas - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Manage clients in this group"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 3
      th.get(0).text() shouldBe "Client reference"
      th.get(1).text() shouldBe "Tax reference"
      th.get(2).text() shouldBe "Tax service"

      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")

      trs.size() shouldBe 3
      //first row
      trs.get(0).select("td").get(0).text() shouldBe "friendly0"
      trs.get(0).select("td").get(1).text() shouldBe "ending in 6780"
      trs.get(0).select("td").get(2).text() shouldBe "VAT"

      //last row
      trs.get(2).select("td").get(0).text() shouldBe "friendly2"
      trs.get(2).select("td").get(1).text() shouldBe "ending in 6782"
      trs.get(2).select("td").get(2).text() shouldBe "VAT"

      html.select("p#clients-in-group").text() shouldBe "Showing total of 3 clients"
      html.select("a#update-clients").text() shouldBe "Update clients"
      html.select("a#update-clients").attr("href") shouldBe
        routes.ManageGroupClientsController.showManageGroupClients(accessGroup._id.toString).url
    }

    "render with filter & searchTerm set" in {
      //given
      val groupWithClients = accessGroup.copy(clients =
        Some(displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      expectGetGroupSuccess(accessGroup._id.toString, Some(groupWithClients))

      implicit val requestWithQueryParams = FakeRequest(GET,
        routes.ManageGroupClientsController.showExistingGroupClients(groupWithClients._id.toString).url +
          "?submit=filter&search=friendly1&filter=HMRC-MTD-VAT"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result =
        controller.showExistingGroupClients(groupWithClients._id.toString)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Filter results for 'friendly1' and 'VAT' Manage clients - Bananas - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Manage clients in this group"
      html.select(H2).text shouldBe "Filter results for 'friendly1' and 'VAT'"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 3
      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 1
    }

    "render with filter that matches nothing" in {
      //given
      val groupWithClients = accessGroup.copy(clients =
        Some(displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      expectGetGroupSuccess(accessGroup._id.toString, Some(groupWithClients))

      //there are none of these HMRC-CGT-PD in the setup clients. so expect no results back
      val NON_MATCHING_FILTER = "HMRC-CGT-PD"
      implicit val requestWithQueryParams = FakeRequest(GET,
        routes.ManageGroupClientsController.showExistingGroupClients(groupWithClients._id.toString).url +
          s"?submit=filter&search=friendly1&filter=$NON_MATCHING_FILTER"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showExistingGroupClients(groupWithClients._id.toString)(requestWithQueryParams)

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
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      expectGetGroupSuccess(accessGroup._id.toString, Some(groupWithClients))

      //and we have CLEAR filter in query params
      implicit val requestWithQueryParams = FakeRequest(GET,
        routes.ManageGroupClientsController.showExistingGroupClients(groupWithClients._id.toString).url +
          s"?submit=clear"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result =
        controller.showExistingGroupClients(groupWithClients._id.toString)(requestWithQueryParams)

      //then
      redirectLocation(result).get
        .shouldBe(routes.ManageGroupClientsController.showExistingGroupClients(groupWithClients._id.toString).url)
    }

  }

  s"GET ${routes.ManageGroupClientsController.showManageGroupClients(accessGroup._id.toString).url}" should {

    "render correctly the manage group CLIENTS page" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
      stubGetClientsOk(arn)(fakeClients)

      //when
      val result =
        controller.showManageGroupClients(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Update clients in this group - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Update clients in this group"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Client reference"
      th.get(2).text() shouldBe "Tax reference"
      th.get(3).text() shouldBe "Tax service"
      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")

      trs.size() shouldBe 3
      //first row
      trs.get(0).select("td").get(1).text() shouldBe "friendly0"
      trs.get(0).select("td").get(2).text() shouldBe "ending in 6780"
      trs.get(0).select("td").get(3).text() shouldBe "VAT"

      //last row
      trs.get(2).select("td").get(1).text() shouldBe "friendly2"
      trs.get(2).select("td").get(2).text() shouldBe "ending in 6782"
      trs.get(2).select("td").get(3).text() shouldBe "VAT"

      html.select("p#member-count-text").text() shouldBe "Selected 0 clients of 3"
    }

    "render correctly the manage group CLIENTS page when there are no clients" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(CLIENT_FILTER_INPUT, "HMRC-MTD-VAT"))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
      stubGetClientsAccepted(arn)

      //when
      val result =
        controller.showManageGroupClients(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Filter results for 'VAT' Update clients in this group - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Update clients in this group"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 0
      html.select(Css.H2).text() shouldBe "No clients found"
      html.select(Css.paragraphs).get(1).text() shouldBe "Update your filters and try again or clear your filters to see all your clients"

    }

    "render correctly the manage group CLIENTS page when there are clients already in the group" in {
      //given
      val clients = displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet
      val groupWithClients = accessGroup.copy(clients = Some(clients))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      expectGetGroupSuccess(groupWithClients._id.toString, Some(groupWithClients))

      stubGetClientsOk(arn)(fakeClients)

      //when
      val result =
        controller.showManageGroupClients(groupWithClients._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Update clients in this group - Agent services account - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Update clients in this group"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Client reference"
      th.get(2).text() shouldBe "Tax reference"
      th.get(3).text() shouldBe "Tax service"
      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")

      trs.size() shouldBe 3
      //first row
      trs.get(0).select("td").get(1).text() shouldBe "friendly0"
      trs.get(0).select("td").get(2).text() shouldBe "ending in 6780"
      trs.get(0).select("td").get(3).text() shouldBe "VAT"

      //last row
      trs.get(2).select("td").get(1).text() shouldBe "friendly2"
      trs.get(2).select("td").get(2).text() shouldBe "ending in 6782"
      trs.get(2).select("td").get(3).text() shouldBe "VAT"

      html.select("p#member-count-text").text() shouldBe "Selected 3 clients of 3"
    }

    "render with clients held in session when a filter was applied" in {

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(FILTERED_CLIENTS, displayClients.take(1)))
      await(sessionCacheRepo.putSession(CLIENT_SEARCH_INPUT, "blah"))
      await(sessionCacheRepo.putSession(CLIENT_FILTER_INPUT, "HMRC-MTD-VAT"))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      stubGetClientsOk(arn)(fakeClients)

      val result =
        controller.showManageGroupClients(accessGroup._id.toString)(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Filter results for 'blah' and 'VAT' Update clients in this group - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Update clients in this group"
      html.select(H2).text() shouldBe "Filter results for 'blah' and 'VAT'"

      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")

      trs.size() shouldBe 1
      trs.get(0).select("td").get(1).text() shouldBe "friendly0"
      trs.get(0).select("td").get(2).text() shouldBe "ending in 6780"
      trs.get(0).select("td").get(3).text() shouldBe "VAT"
    }
  }

  s"POST ${routes.ManageGroupClientsController.submitManageGroupClients(accessGroup._id.toString).url}" should {

    "save selected clients to session" when {

      s"button is Continue and redirect to ${routes.ManageGroupController.showManageGroups.url}" in {

        implicit val request =
          FakeRequest("POST", routes.ManageGroupClientsController.submitManageGroupClients(accessGroup._id.toString).url)
            .withFormUrlEncodedBody(
              "hasSelectedClients" -> "false",
              "clients[0]" -> displayClients.head.id,
              "clients[1]" -> displayClients.last.id,
              "search" -> "",
              "filter" -> "",
              "continue" -> "continue"
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        await(sessionCacheRepo.putSession(FILTERED_CLIENTS, displayClients.take(1)))
        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
        stubGetClientsOk(arn)(fakeClients)

        expectUpdateGroupSuccess(accessGroup._id.toString,
          UpdateAccessGroupRequest(clients = Some(Set(displayClients.head, displayClients.last).map(dc => Client(dc.enrolmentKey, dc.name)))))

        val result =
          controller.submitManageGroupClients(accessGroup._id.toString)(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe
          routes.ManageGroupClientsController.showReviewSelectedClients(accessGroup._id.toString).url
        await(sessionCacheRepo.getFromSession(FILTERED_CLIENTS)) shouldBe Option.empty
        await(sessionCacheRepo.getFromSession(HIDDEN_CLIENTS_EXIST)) shouldBe Option.empty
      }

      "display error when button is Continue, no filtered clients, no hidden clients exist and no clients were selected" in {
        // given

        implicit val request = FakeRequest(
          "POST",
          routes.ManageGroupClientsController.submitManageGroupClients(accessGroup._id.toString)
            .url
        ).withFormUrlEncodedBody(
          "hasSelectedClients" -> "false",
          "clients" -> "",
          "search" -> "",
          "filter" -> "",
          "continue" -> "continue"
        ).withSession(SessionKeys.sessionId -> "session-x")

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
        expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
        stubGetClientsOk(arn)(fakeClients)


        // when
        val result = controller.submitManageGroupClients(accessGroup._id.toString)(request)

        status(result) shouldBe OK
        val html = Jsoup.parse(contentAsString(result))

        // then
        html.title() shouldBe "Error: Update clients in this group - Agent services account - GOV.UK"
        html.select(Css.H1).text() shouldBe "Update clients in this group"
        html
          .select(Css.errorSummaryForField("clients"))
        await(sessionCacheRepo.getFromSession(SELECTED_CLIENTS)).isDefined shouldBe false
      }

      "display error when filtered clients and form has errors" in {
        // given

        implicit val request = FakeRequest(
          "POST",
          routes.ManageGroupClientsController.submitManageGroupClients(accessGroup._id.toString).url
        ).withFormUrlEncodedBody(
          "hasSelectedClients" -> "false",
          "clients" -> "",
          "search" -> "",
          "filter" -> "",
          "submitFilter" -> "submitFilter"
        ).withSession(SessionKeys.sessionId -> "session-x")

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
        expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
        await(sessionCacheRepo.putSession(FILTERED_CLIENTS, displayClients))
        stubGetClientsOk(arn)(fakeClients)

        // when
        val result = controller.submitManageGroupClients(accessGroup._id.toString)(request)

        status(result) shouldBe OK
        val html = Jsoup.parse(contentAsString(result))

        // then
        html.title() shouldBe "Error: Update clients in this group - Agent services account - GOV.UK"
        html.select(Css.H1).text() shouldBe "Update clients in this group"
        html
          .select(Css.errorSummaryForField("clients"))
        await(sessionCacheRepo.getFromSession(SELECTED_CLIENTS)).isDefined shouldBe false
      }


      s"when Filter clicked redirect to ${routes.ManageGroupClientsController.showManageGroupClients(accessGroup._id.toString).url}" in {

        implicit val request = FakeRequest(
          "POST",
          routes.ManageGroupClientsController.submitManageGroupClients(accessGroup._id.toString).url
        ).withFormUrlEncodedBody(
          "hasSelectedClients" -> "false",
          "clients" -> "",
          "search" -> "",
          "filter" -> "Ab",
          "submitFilter" -> "submitFilter"
        ).withSession(SessionKeys.sessionId -> "session-x")

        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
        stubGetClientsOk(arn)(fakeClients)

        // when
        val result = controller.submitManageGroupClients(accessGroup._id.toString)(request)
        status(result) shouldBe SEE_OTHER
      }
    }
  }

  s"GET ${routes.ManageGroupClientsController.showReviewSelectedClients(accessGroup._id.toString).url}" should {


    "redirect if no clients selected are in session" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      //when
      val result = controller.showReviewSelectedClients(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.ManageGroupClientsController.showManageGroupClients(accessGroup._id.toString).url
    }

    "render correctly the manage group REVIEW SELECTED page" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      //when
      val result = controller.showReviewSelectedClients(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Review selected clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 3 clients"
      html.select(Css.tableWithId("sortable-table")).select("tbody tr").size() shouldBe 3
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

  s"POST ${routes.ManageGroupClientsController.submitReviewSelectedClients(accessGroup._id.toString).url}" should {

    s"redirect to '${routes.ManageGroupClientsController.showGroupClientsUpdatedConfirmation(accessGroup._id.toString)}' page with answer 'false'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedClients(accessGroup._id.toString)}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      val result = controller.submitReviewSelectedClients(accessGroup._id.toString)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.ManageGroupClientsController
        .showGroupClientsUpdatedConfirmation(accessGroup._id.toString).url
    }

    s"redirect to '${routes.ManageGroupClientsController.showManageGroupClients(accessGroup._id.toString)}'" +
      s" page with answer 'true'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedClients(accessGroup._id.toString)}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      val result = controller.submitReviewSelectedClients(accessGroup._id.toString)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.ManageGroupClientsController
        .showManageGroupClients(accessGroup._id.toString).url
    }

    s"render errors when no radio button selected" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedClients(accessGroup._id.toString)}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      val result = controller.submitReviewSelectedClients(accessGroup._id.toString)(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Review selected clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 3 clients"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you need to add or remove selected clients"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you need to add or remove selected clients"

    }
  }

  s"GET ${routes.ManageGroupClientsController.showGroupClientsUpdatedConfirmation(accessGroup._id.toString).url}" should {

    "render correctly" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      //when
      val result = controller.showGroupClientsUpdatedConfirmation(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK
      // selected clients should be cleared from session
      await(sessionCacheRepo.getFromSession(SELECTED_CLIENTS)) shouldBe None

      //and
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Bananas access group clients updated - Agent services account - GOV.UK"
      html
        .select(Css.confirmationPanelH1)
        .text() shouldBe "Bananas access group clients updated"
      html.select(Css.H2).text() shouldBe "What happens next"
      html
        .select(Css.paragraphs)
        .get(0)
        .text() shouldBe "You have changed the clients that can be managed by the team members in this access group."
      html
        .select("a#returnToDashboard")
        .text() shouldBe "Return to manage access groups"
      html
        .select("a#returnToDashboard")
        .attr("href") shouldBe routes.ManageGroupController.showManageGroups.url
      html.select(Css.backLink).size() shouldBe 0
    }

    s"redirect to ${routes.ManageGroupClientsController.showManageGroupClients(groupId)} when there are no selected clients" in {

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      //when
      val result = controller.showGroupClientsUpdatedConfirmation(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe routes.ManageGroupClientsController.showManageGroupClients(accessGroup._id.toString).url
    }
  }


}
