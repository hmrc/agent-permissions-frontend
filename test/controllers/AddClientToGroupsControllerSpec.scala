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
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout}
import repository.SessionCacheRepository
import services.{ClientService, ClientServiceImpl, GroupService, GroupServiceImpl}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AddClientToGroupsControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val groupService: GroupService = new GroupServiceImpl(mockAgentUserClientDetailsConnector, sessionCacheRepo, mockAgentPermissionsConnector)
  implicit val clientService: ClientService = new ClientServiceImpl(mockAgentUserClientDetailsConnector, sessionCacheRepo)
  lazy val sessionCacheRepo: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)

  override def moduleWithOverrides = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepo)
      bind(classOf[GroupService]).toInstance(groupService)
      bind(classOf[ClientService]).toInstance(clientService)
    }
  }

  private val controller = fakeApplication.injector.instanceOf[AddClientToGroupsController]

  override implicit lazy val fakeApplication: Application =
    appBuilder.configure("mongodb.uri" -> mongoUri).build()

  val fakeClients: Seq[Client] =
    List.tabulate(3)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly$i"))

  val displayClients: Seq[DisplayClient] = fakeClients.map(DisplayClient.fromClient(_))
  private val client: DisplayClient = displayClients(0)


  s"GET ${routes.AddClientToGroupsController.showSelectGroupsForClient(client.id)}" should {

    "render correctly the EXISTING CLIENTS page with no query params" in {
      //given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(s"groupId$i", s"name $i", i * 3, i * 4))
      val summaries = Some((groupSummaries, Seq.empty))
      val groupsAlreadyAssociatedToClient = groupSummaries.take(2)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      (clientService
        .lookupClient(_: Arn)(_: String)( _: HeaderCarrier, _: ExecutionContext))
        .expects(arn, client.id, *, *)
        .returning(Future successful Some(client))

      expectGetGroupSummarySuccess(arn, summaries)
      expectGetGroupsForClientSuccess(arn, DisplayClient.toEnrolment(client), Some(groupsAlreadyAssociatedToClient))

      //when
      val result = controller.showSelectGroupsForClient(client.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      println(html)
      html.title() shouldBe "Select clients - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select clients"
    }

  }
}
