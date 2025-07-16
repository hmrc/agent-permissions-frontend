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
import connectors.{AgentAssuranceConnector, AgentPermissionsConnector}
import controllers.actions.AuthAction
import helpers.BaseSpec
import org.jsoup.Jsoup
import play.api.Application
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.SessionCacheService
import sttp.model.Uri.UriContext
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.SessionKeys

class SignOutControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentAssuranceConnector: AgentAssuranceConnector =
    mock[AgentAssuranceConnector]
  implicit lazy val mockSessionCacheService: SessionCacheService = mock[SessionCacheService]

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(
        new AuthAction(
          mockAuthConnector,
          env,
          conf,
          mockAgentPermissionsConnector,
          mockAgentAssuranceConnector,
          mockSessionCacheService
        )
      )
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[SessionCacheService]).toInstance(mockSessionCacheService)
    }
  }

  override implicit lazy val fakeApplication: Application = appBuilder.configure("mongodb.uri" -> mongoUri).build()
  implicit val appConfig: AppConfig = fakeApplication.injector.instanceOf[AppConfig]
  val controller: SignOutController = fakeApplication.injector.instanceOf[SignOutController]

  def authOk(): Unit = {
    expectAuthorisationGrantsAccess(mockedAuthResponse)
    expectIsArnAllowed(allowed = true)
    expectGetSessionItem(SUSPENSION_STATUS, false)
  }

  def fakeRequest(method: String = "GET", uri: String = "/"): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(method, uri).withSession(
      SessionKeys.sessionId -> "session-id",
      SessionKeys.authToken -> "auth-token"
    )

  def signOutUrlWithContinue(continue: String): String = {
    val signOutBaseUrl = "http://localhost:9099"
    val signOutPath = "/bas-gateway/sign-out-without-state"
    uri"""${signOutBaseUrl + signOutPath}?${Map("continue" -> continue)}""".toString
  }

  "GET /sign-out" should {
    "remove session and redirect to /home/survey" in {
      val continueUrl = uri"${appConfig.selfExternalUrl + "/agent-permissions/signed-out"}"
      val response = controller.signOut(fakeRequest())
      status(response) shouldBe 303
      redirectLocation(response) shouldBe Some(signOutUrlWithContinue(continueUrl.toString))
    }
  }

  "GET /signed-out" should {
    "redirect to sign in with continue url back to /agent-services-account" in {
      val response = controller.signedOut(FakeRequest("GET", "/"))
      status(response) shouldBe 200
      val html = Jsoup.parse(contentAsString(response))
      html.title() shouldBe "You have signed out - Agent services account - GOV.UK"
    }
  }

  "GET /time-out" should {
    "redirect to bas-gateway-frontend/sign-out-without-state with timed out page as continue" in {
      val continue = uri"${appConfig.selfExternalUrl + routes.SignOutController.timedOut().url}"
      val response = controller.timeOut()(fakeRequest())
      status(response) shouldBe 303
      redirectLocation(response) shouldBe Some(signOutUrlWithContinue(continue.toString))
    }
  }

  "GET /timed-out" should {
    "should show the timed out page" in {
      val response = controller.timedOut(FakeRequest("GET", "/"))
      status(response) shouldBe 200
      val html = Jsoup.parse(contentAsString(response))
      html.title() shouldBe "You have been signed out - Agent services account - GOV.UK"
      html.select("main p.govuk-body").text() shouldBe
        "You have not done anything for 15 minutes, so we have signed you out to keep your account secure. Sign in again to use this service."
    }
  }

}
