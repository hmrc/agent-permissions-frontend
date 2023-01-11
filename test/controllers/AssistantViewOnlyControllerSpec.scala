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
import helpers.Css._
import helpers.{BaseSpec, Css}
import models.DisplayClient
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import org.mongodb.scala.bson.ObjectId
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services.GroupService
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroupSummary => GroupSummary}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

import java.time.LocalDate

class AssistantViewOnlyControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector
    : AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val groupService: GroupService = mock[GroupService]

  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

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

  val groupSummaries: Seq[GroupSummary] = (1 to 3).map(i =>
    GroupSummary(s"groupId$i", s"name $i", Some(i * 3), i * 4))

  private val agentUser: AgentUser =
    AgentUser(RandomStringUtils.random(5), "Rob the Agent")

  val accessGroup: AccessGroup =
    AccessGroup(new ObjectId(),
    arn,
    "Bananas",
    LocalDate.of(2020, 3, 10).atStartOfDay(),
    null,
    agentUser,
    agentUser,
    None,
    Some(fakeClients.toSet)
    )

  val controller: AssistantViewOnlyController = fakeApplication.injector.instanceOf[AssistantViewOnlyController]
  private val ctrlRoute: ReverseAssistantViewOnlyController = routes.AssistantViewOnlyController

  s"GET ${ctrlRoute.showUnassignedClientsViewOnly.url}" should {

    "render unassigned clients list with no query params" in {
      // given
      expectAuthorisationGrantsAccess(mockedAssistantAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectOptInStatusOk(arn)(OptedInReady)

      expectGetUnassignedClientsSuccess(arn, displayClients)

      //when
      val result = controller.showUnassignedClientsViewOnly(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Other clients you can access - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Other clients you can access"
      html.select(Css.backLink).attr("href") shouldBe "http://localhost:9401/agent-services-account/your-account"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 3
      val tr = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      tr.size() shouldBe 3


    }

    "render the unassigned clients list with search params" in {
      //given
      expectAuthorisationGrantsAccess(mockedAssistantAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectOptInStatusOk(arn)(OptedInReady)

      expectGetUnassignedClientsSuccess(arn, displayClients)

      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showUnassignedClientsViewOnly.url +
          "?submit=filter&search=friendly1&filter="
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showUnassignedClientsViewOnly(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Filter results for 'friendly1' Other clients you can access - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Other clients you can access"
      html.select(Css.backLink).attr("href") shouldBe "http://localhost:9401/agent-services-account/your-account"

      html.select(H2).text shouldBe "Filter results for 'friendly1'"

      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 1
    }

    "render with filter that matches nothing" in {
      //given
      expectAuthorisationGrantsAccess(mockedAssistantAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectOptInStatusOk(arn)(OptedInReady)

      expectGetUnassignedClientsSuccess(arn, displayClients)

      //there are none of these HMRC-CGT-PD in the setup clients. so expect no results back
      val NON_MATCHING_FILTER = "HMRC-CGT-PD"
      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showUnassignedClientsViewOnly.url +
          s"?submit=filter&search=friendly1&filter=$NON_MATCHING_FILTER"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showUnassignedClientsViewOnly(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Filter results for 'friendly1' and 'Capital Gains Tax on UK Property account' Other clients you can access - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Other clients you can access"
      html.select(Css.backLink).attr("href") shouldBe "http://localhost:9401/agent-services-account/your-account"

      html.select(H2).text shouldBe "No clients found"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 0
      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 0
    }

    "redirect to baseUrl when CLEAR FILTER is clicked" in {
      //given
      expectAuthorisationGrantsAccess(mockedAssistantAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectOptInStatusOk(arn)(OptedInReady)

      //and we have CLEAR filter in query params
      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showUnassignedClientsViewOnly.url +
          s"?submit=clear"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showUnassignedClientsViewOnly(requestWithQueryParams)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get
        .shouldBe(ctrlRoute.showUnassignedClientsViewOnly.url)
    }

  }

  s"GET ${ctrlRoute.showExistingGroupClientsViewOnly(accessGroup._id.toString).url}" should {

    s"render group ${accessGroup.groupName} clients list with no query params" in {
      // given
      expectAuthorisationGrantsAccess(mockedAssistantAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectOptInStatusOk(arn)(OptedInReady)

      expectGetGroupById(accessGroup._id.toString, Some(accessGroup))


      //when
      val result = controller.showExistingGroupClientsViewOnly(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe s"${accessGroup.groupName} clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe s"${accessGroup.groupName} clients"

      html.select(Css.backLink).attr("href") shouldBe "http://localhost:9401/agent-services-account/your-account"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 3
      val tr = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      tr.size() shouldBe 3


    }

    s"render group ${accessGroup.groupName} clients list with search params" in {
      //given
      expectAuthorisationGrantsAccess(mockedAssistantAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectOptInStatusOk(arn)(OptedInReady)

      expectGetGroupById(accessGroup._id.toString, Some(accessGroup))

      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showExistingGroupClientsViewOnly(accessGroup._id.toString).url +
          "?submit=filter&search=friendly1&filter="
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showExistingGroupClientsViewOnly(accessGroup._id.toString)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Filter results for 'friendly1' ${accessGroup.groupName} clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe s"${accessGroup.groupName} clients"
      html.select(Css.backLink).attr("href") shouldBe "http://localhost:9401/agent-services-account/your-account"

      html.select(H2).text shouldBe "Filter results for 'friendly1'"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 3
      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 1
    }

    "render with filter that matches nothing" in {
      //given
      expectAuthorisationGrantsAccess(mockedAssistantAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectOptInStatusOk(arn)(OptedInReady)

      expectGetGroupById(accessGroup._id.toString, Some(accessGroup))

      //there are none of these HMRC-CGT-PD in the setup clients. so expect no results back
      val NON_MATCHING_FILTER = "HMRC-CGT-PD"
      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showExistingGroupClientsViewOnly(accessGroup._id.toString).url +
          s"?submit=filter&search=friendly1&filter=$NON_MATCHING_FILTER"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showExistingGroupClientsViewOnly(accessGroup._id.toString)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe s"Filter results for 'friendly1' and 'Capital Gains Tax on UK Property account' ${accessGroup.groupName} clients - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe s"${accessGroup.groupName} clients"

      html.select(H2).text shouldBe "No clients found"

      val th = html.select(Css.tableWithId("sortable-table")).select("thead th")
      th.size() shouldBe 0
      val trs = html.select(Css.tableWithId("sortable-table")).select("tbody tr")
      trs.size() shouldBe 0
    }

    "redirect to baseUrl when CLEAR FILTER is clicked" in {
      //given
      expectAuthorisationGrantsAccess(mockedAssistantAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectOptInStatusOk(arn)(OptedInReady)

      expectGetGroupById(accessGroup._id.toString, Some(accessGroup))

      //and we have CLEAR filter in query params
      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showExistingGroupClientsViewOnly(accessGroup._id.toString).url +
          s"?submit=clear"
      )
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showExistingGroupClientsViewOnly(accessGroup._id.toString)(requestWithQueryParams)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get
        .shouldBe(ctrlRoute.showExistingGroupClientsViewOnly(accessGroup._id.toString).url)
    }

  }


}
