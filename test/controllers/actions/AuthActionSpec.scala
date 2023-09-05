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

package controllers.actions

import com.google.inject.AbstractModule
import config.AppConfig
import connectors.{AgentClientAuthorisationConnector, AgentPermissionsConnector}
import helpers.{AgentClientAuthorisationConnectorMocks, BaseSpec}
import play.api.Application
import play.api.http.Status.{FORBIDDEN, OK, SEE_OTHER}
import play.api.mvc.Results.Ok
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation}
import services.{InMemorySessionCacheService, SessionCacheService}
import uk.gov.hmrc.auth.core.{AuthConnector, InsufficientEnrolments, MissingBearerToken, UnsupportedAuthProvider}

import scala.concurrent.Future

class AuthActionSpec extends BaseSpec with AgentClientAuthorisationConnectorMocks {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentClientAuthConnector: AgentClientAuthorisationConnector = mock[AgentClientAuthorisationConnector]
  implicit val mockSessionService: InMemorySessionCacheService = new InMemorySessionCacheService()

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {
    override def configure(): Unit = {
      bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[AgentClientAuthorisationConnector]).toInstance(mockAgentClientAuthConnector)
      bind(classOf[SessionCacheService]).toInstance(mockSessionService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .build()

  val authAction: AuthAction = fakeApplication.injector.instanceOf[AuthAction]
  implicit val appConfig: AppConfig = fakeApplication.injector.instanceOf[AppConfig]

  "Auth Action" when {
    "the user hasn't logged in" should {
      "redirect the user to log in " in {

        expectAuthorisationFails(MissingBearerToken())

        val result =
          authAction.isAuthorisedAgent(arn => Future.successful(Ok("")))
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe
          "http://localhost:9553/bas-gateway/sign-in?continue_url=http%3A%2F%2Flocalhost%3A9452%2F&origin=agent-permissions-frontend"
      }
    }

    "the user has suspension details" should {
      "redirect to ASA if suspended" in {
        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectGetSuspensionDetails(suspensionStatus = true)

        val result =  authAction.isAuthorisedAgent(_ => Future.successful(Ok("")))
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe
          "http://localhost:9401/agent-services-account/account-limited"

      }

      "save to session if not suspended" in {
        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectGetSuspensionDetails()
        expectIsArnAllowed(allowed = true)

        val result =  authAction.isAuthorisedAgent(_ => Future.successful(Ok("")))
        status(result) shouldBe OK
      }
    }

    "the user has InsufficentEnrolments" should {
      "return FORBIDDEN" in {

        expectAuthorisationFails(InsufficientEnrolments())

        val result =
          authAction.isAuthorisedAgent(arn => Future.successful(Ok("")))
        status(result) shouldBe FORBIDDEN
      }

      "the user has UnsupportedAuthProvider" should {
        "return FORBIDDEN" in {

          expectAuthorisationFails(UnsupportedAuthProvider())

          val result =
            authAction.isAuthorisedAgent(arn => Future.successful(Ok("")))
          status(result) shouldBe FORBIDDEN
        }
      }
    }
  }
}
