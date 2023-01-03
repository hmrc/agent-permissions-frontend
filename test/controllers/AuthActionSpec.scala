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
import config.AppConfig
import connectors.AgentPermissionsConnector
import controllers.actions.AuthAction
import helpers.BaseSpec
import play.api.Application
import play.api.http.Status.{FORBIDDEN, SEE_OTHER}
import play.api.mvc.Results.Ok
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation}
import uk.gov.hmrc.auth.core.{AuthConnector, InsufficientEnrolments, MissingBearerToken, UnsupportedAuthProvider}

import scala.concurrent.Future

class AuthActionSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]

  override def moduleWithOverrides = new AbstractModule() {
    override def configure(): Unit = {
      bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .build()

  val authAction = fakeApplication.injector.instanceOf[AuthAction]
  implicit val appConfig = fakeApplication.injector.instanceOf[AppConfig]

  "Auth Action" when {
    "the user hasn't logged in" should {
      "redirect the user to log in " in {

        expectAuthorisationFails(MissingBearerToken())

        val result =
          authAction.isAuthorisedAgent((arn) => Future.successful(Ok("")))
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe
          "http://localhost:9553/bas-gateway/sign-in?continue_url=http%3A%2F%2Flocalhost%3A9452%2F&origin=agent-permissions-frontend"
      }
    }

    "the user has InsufficentEnrolments" should {
      "return FORBIDDEN" in {

        expectAuthorisationFails(InsufficientEnrolments())

        val result =
          authAction.isAuthorisedAgent((arn) => Future.successful(Ok("")))
        status(result) shouldBe FORBIDDEN
      }

      "the user has UnsupportedAuthProvider" should {
        "return FORBIDDEN" in {

          expectAuthorisationFails(UnsupportedAuthProvider())

          val result =
            authAction.isAuthorisedAgent((arn) => Future.successful(Ok("")))
          status(result) shouldBe FORBIDDEN
        }
      }
    }
  }
}
