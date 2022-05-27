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
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector}
import helpers.BaseSpec
import models.JourneySession
import play.api.Application
import play.api.http.Status.SEE_OTHER
import play.api.test.Helpers._
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{OptedInReady, OptedOutEligible, OptedOutSingleUser}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.UpstreamErrorResponse

class RootControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]

  lazy val sessioncacheRepo: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)

  override def moduleWithOverrides = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessioncacheRepo)
    }
  }

  override implicit lazy val fakeApplication: Application = appBuilder.build()

  val controller = fakeApplication.injector.instanceOf[RootController]

  "root controller" when {
    "no session is available" should {
      "retrieve opt-in status from backend and redirect to self if status available" in {

        stubAuthorisationGrantAccess(mockedAuthResponse)
        stubOptInStatusOk(arn)(OptedOutEligible)

        val result = controller.start()(request)

        status(result) shouldBe SEE_OTHER

        redirectLocation(result).get shouldBe routes.RootController.start.url
      }

      "throw an exception if there was no response from the backend" in {

        stubAuthorisationGrantAccess(mockedAuthResponse)
        stubOptInStatusError(arn)

        intercept[UpstreamErrorResponse] {
          await(controller.start()(request))
        }
      }
    }

    "a session is available" should {
      "redirect to opt-in journey if the optin status is eligible to opt-in" in {

        stubAuthorisationGrantAccess(mockedAuthResponse)
        await(sessioncacheRepo.putSession(DATA_KEY, JourneySession(optInStatus = OptedOutEligible)))

        val result = controller.start()(request)

        status(result) shouldBe SEE_OTHER

        redirectLocation(result).get shouldBe routes.OptInController.start.url
      }

      "redirect to opt-out journey if the optin status is eligible to opt-out" in {

        stubAuthorisationGrantAccess(mockedAuthResponse)
        await(sessioncacheRepo.putSession(DATA_KEY, JourneySession(optInStatus = OptedInReady)))

        val result = controller.start()(request)

        status(result) shouldBe SEE_OTHER

        redirectLocation(result).get shouldBe routes.OptOutController.start.url
      }

      "redirect to ASA dashboard if user is not eligible to opt-in or opt-out" in {

        stubAuthorisationGrantAccess(mockedAuthResponse)
        await(sessioncacheRepo.putSession(DATA_KEY, JourneySession(optInStatus = OptedOutSingleUser)))

        val result = controller.start()(request)

        status(result) shouldBe SEE_OTHER

        redirectLocation(result).get shouldBe "http://localhost:9401/agent-services-account/manage-account"
      }
    }
  }

}
