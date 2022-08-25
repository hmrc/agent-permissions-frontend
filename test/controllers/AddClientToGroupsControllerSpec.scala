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
import helpers.{BaseSpec, Css}
import models.DisplayClient
import org.jsoup.Jsoup
import play.api.Application
import play.api.mvc.Request
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout}
import repository.SessionCacheRepository
import services.{ClientService, GroupService}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AddClientToGroupsControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]
  implicit val mockClientService: ClientService = mock[ClientService]
  lazy val sessionCacheRepo: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)

  override def moduleWithOverrides = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepo)
      bind(classOf[GroupService]).toInstance(mockGroupService)
      bind(classOf[ClientService]).toInstance(mockClientService)
    }
  }

  private val controller = fakeApplication.injector.instanceOf[AddClientToGroupsController]

  override implicit lazy val fakeApplication: Application =
    appBuilder.configure("mongodb.uri" -> mongoUri).build()

  val fakeClients: Seq[Client] =
    List.tabulate(3)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"Client $i"))

  val displayClients: Seq[DisplayClient] = fakeClients.map(DisplayClient.fromClient(_))
  private val client: DisplayClient = displayClients(0)


  s"GET ${routes.AddClientToGroupsController.showSelectGroupsForClient(client.id)}" should {

    "render correctly the html" in {
      //given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(s"groupId$i", s"Group $i", i * 3, i * 4))
      val summaries = (groupSummaries, Seq.empty)
      val groupsAlreadyAssociatedToClient = groupSummaries.take(2)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)

      (mockClientService
        .lookupClient(_: Arn)(_: String)( _: HeaderCarrier, _: ExecutionContext))
        .expects(arn, client.id, *, *)
        .returning(Future successful Some(client))

      (mockGroupService.groupSummaries(_: Arn)(_: Request[_], _: ExecutionContext,_: HeaderCarrier))
        .expects(arn, *, *, *)
        .returning( Future.successful(summaries))

      (mockGroupService
        .groupSummariesForClient(_: Arn, _: DisplayClient)(_: Request[_], _: ExecutionContext,_: HeaderCarrier))
        .expects(arn, client, *, *, *)
        .returning( Future.successful(groupsAlreadyAssociatedToClient))

      //when
      val result = controller.showSelectGroupsForClient(client.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Which access groups would you like to add Client 0 to? - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Which access groups would you like to add Client 0 to?"
      html.select(Css.paragraphs).get(0).text() shouldBe "Client is currently in these access groups:"
      html.select(Css.li("already-in-groups")).get(0).text() shouldBe "Group 1"
      html.select(Css.li("already-in-groups")).get(1).text() shouldBe "Group 2"
      val form = html.select(Css.form)
      form.attr("action")
        .shouldBe( routes.AddClientToGroupsController.submitSelectGroupsForClient(client.id).url)
      val checkboxes = form.select(".govuk-checkboxes#groups input[name=groups[]]")
      checkboxes size() shouldBe 3
      val checkboxLabels = form.select("label.govuk-checkboxes__label")
      checkboxLabels.get(0).text() shouldBe "Group 3"
      checkboxLabels.get(1).text() shouldBe "Group 4"
      checkboxLabels.get(2).text() shouldBe "Group 5"
      html.select(Css.submitButton).text() shouldBe "Continue"
    }

  }
}
