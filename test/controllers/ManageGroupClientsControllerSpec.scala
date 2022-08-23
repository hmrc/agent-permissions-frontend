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
import models.DisplayClient.toEnrolment
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

  override def moduleWithOverrides = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction])
        .toInstance(new AuthAction(mockAuthConnector, env, conf))
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

  val controller = fakeApplication.injector.instanceOf[ManageGroupClientsController]

  s"GET ${routes.ManageGroupClientsController.showExistingGroupClients(accessGroup._id.toString)}" should {

    "render correctly the EXISTING CLIENTS page with no query params" in {
      //given
      val groupWithClients = accessGroup.copy(clients =
        Some(displayClients.map(DisplayClient.toEnrolment).toSet))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)

      expectGetGroupSuccess(accessGroup._id.toString, Some(groupWithClients))


      //when
      val result =
        controller.showExistingGroupClients(groupWithClients._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Manage clients - Bananas - Manage Agent Permissions - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas"
      html.select(Css.H1).text shouldBe "Manage clients"

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
        Some(displayClients.map(DisplayClient.toEnrolment).toSet))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)

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
      html.title shouldBe "Manage clients - Bananas - Manage Agent Permissions - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas"
      html.select(Css.H1).text shouldBe "Manage clients"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 3
      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 1
    }

    "render with filter that matches nothing" in {
      //given
      val groupWithClients = accessGroup.copy(clients =
        Some(displayClients.map(DisplayClient.toEnrolment).toSet))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)

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
      html.title shouldBe "Manage clients - Bananas - Manage Agent Permissions - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas"
      html.select(Css.H1).text shouldBe "Manage clients"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 3
      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 0
    }

    "redirect to baseUrl when CLEAR FILTER is clicked" in {
      //given
      val groupWithClients = accessGroup.copy(clients =
        Some(displayClients.map(DisplayClient.toEnrolment).toSet))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)

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

  s"GET ${routes.ManageGroupClientsController.showManageGroupClients(accessGroup._id.toString)}" should {

    "render correctly the manage group CLIENTS page" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
      stubGetClientsOk(arn)(fakeClients)

      //when
      val result =
        controller.showManageGroupClients(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Select clients - Manage Agent Permissions - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Select clients"

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
    }

    "render correctly the manage group CLIENTS page when there are no clients" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
      stubGetClientsAccepted(arn)

      //when
      val result =
        controller.showManageGroupClients(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Select clients - Manage Agent Permissions - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Select clients"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Client reference"
      th.get(2).text() shouldBe "Tax reference"
      th.get(3).text() shouldBe "Tax service"
      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")

      trs.size() shouldBe 0
    }

    "render correctly the manage group CLIENTS page when there are clients already in the group" in {
      //given
      val enrolments = displayClients.map(dc => DisplayClient.toEnrolment(dc)).toSet
      val groupWithClients = accessGroup.copy(clients = Some(enrolments))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)

      expectGetGroupSuccess(groupWithClients._id.toString, Some(groupWithClients))

      stubGetClientsOk(arn)(fakeClients)

      //when
      val result =
        controller.showManageGroupClients(groupWithClients._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Select clients - Manage Agent Permissions - GOV.UK"
      html.select(Css.PRE_H1).text shouldBe "Bananas access group"
      html.select(Css.H1).text shouldBe "Select clients"

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
    }

    "render with clients held in session when a filter was applied" in {

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(
        sessionCacheRepo.putSession(FILTERED_CLIENTS, displayClients.take(1)))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      stubGetClientsOk(arn)(fakeClients)

      val result =
        controller.showManageGroupClients(accessGroup._id.toString)(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select clients - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"

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
        expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
        stubGetClientsOk(arn)(fakeClients)

        expectUpdateGroupSuccess(accessGroup._id.toString,
          UpdateAccessGroupRequest(clients = Some(Set(displayClients.head, displayClients.last).map(toEnrolment(_)))))

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
        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
        expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
        stubGetClientsOk(arn)(fakeClients)


        // when
        val result = controller.submitManageGroupClients(accessGroup._id.toString)(request)

        status(result) shouldBe OK
        val html = Jsoup.parse(contentAsString(result))

        // then
        html.title() shouldBe "Error: Select clients - Manage Agent Permissions - GOV.UK"
        html.select(Css.H1).text() shouldBe "Select clients"
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
        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
        expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
        await(sessionCacheRepo.putSession(FILTERED_CLIENTS, displayClients))
        stubGetClientsOk(arn)(fakeClients)

        // when
        val result = controller.submitManageGroupClients(accessGroup._id.toString)(request)

        status(result) shouldBe OK
        val html = Jsoup.parse(contentAsString(result))

        // then
        html.title() shouldBe "Error: Select clients - Manage Agent Permissions - GOV.UK"
        html.select(Css.H1).text() shouldBe "Select clients"
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
        expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
        stubGetClientsOk(arn)(fakeClients)

        // when
        val result = controller.submitManageGroupClients(accessGroup._id.toString)(request)
        status(result) shouldBe SEE_OTHER
      }
    }
  }

  s"GET ${routes.ManageGroupClientsController.showReviewSelectedClients(accessGroup._id.toString)}" should {


    "redirect if no clients selected are in session" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
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
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      //when
      val result = controller.showReviewSelectedClients(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Review selected clients - Manage Agent Permissions - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 3 clients"
      html.select(Css.tableWithId("sortable-table")).select("tbody tr").size() shouldBe 3

    }
  }

  s"GET ${routes.ManageGroupClientsController.showGroupClientsUpdatedConfirmation(accessGroup._id.toString).url}" should {

    "render correctly" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_CLIENTS, displayClients))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      //when
      val result = controller.showGroupClientsUpdatedConfirmation(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK

      //and
      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Bananas access group clients updated - Manage Agent Permissions - GOV.UK"
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
  }


}
