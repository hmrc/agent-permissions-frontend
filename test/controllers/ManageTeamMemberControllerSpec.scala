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
import helpers.Css.H1
import helpers.{BaseSpec, Css}
import models.DisplayClient
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services.{GroupService, ManageClientService}
import uk.gov.hmrc.agentmtdidentifiers.model.{Client, Enrolment, Identifier, OptedInReady}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

import java.util.Base64

class ManageTeamMemberControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector
    : AgentUserClientDetailsConnector =
    mock[AgentUserClientDetailsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]

  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction])
        .toInstance(new AuthAction(mockAuthConnector, env, conf))
      bind(classOf[AgentPermissionsConnector])
        .toInstance(mockAgentPermissionsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepo)
      bind(classOf[AgentUserClientDetailsConnector])
        .toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[GroupService]).toInstance(
        new GroupService(mockAgentUserClientDetailsConnector, sessionCacheRepo, mockAgentPermissionsConnector))
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val controller: ManageTeamMemberController =
    fakeApplication.injector.instanceOf[ManageTeamMemberController]

  val memberId = "test123"

  s"GET ${routes.ManageTeamMemberController.showAllTeamMembers.url}" should {

    "render correctly the confirmation page" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)

      //when
      val result = controller.showAllTeamMembers()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.body.text shouldBe s"showAllTeamMembers not yet implemented ${arn.toString}"
    }
  }

  s"GET ${routes.ManageTeamMemberController.showTeamMemberDetails(memberId).url}" should {

    "render correctly the confirmation page" in {
      //given
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)

      //when
      val result = controller.showTeamMemberDetails(memberId)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.body.text shouldBe s"showTeamMemberDetails for $memberId not yet implemented ${arn.toString}"
    }
  }


}
