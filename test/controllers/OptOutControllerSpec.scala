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
import connectors.AgentPermissionsConnector
import controllers.actions.AuthAction
import helpers.{BaseSpec, Css}
import org.jsoup.Jsoup
import play.api.Application
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{OptinService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.{OptedInSingleUser, OptedOutEligible}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.SessionKeys

class OptOutControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockSessionCacheService : SessionCacheService = mock[SessionCacheService]
  implicit lazy val mockOptinService : OptinService = mock[OptinService]

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[OptinService]).toInstance(mockOptinService)
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[SessionCacheService]).toInstance(mockSessionCacheService)
    }
  }

  override implicit lazy val fakeApplication: Application = appBuilder.configure("mongodb.uri" -> mongoUri).build()

  val controller: OptOutController = fakeApplication.injector.instanceOf[OptOutController]

  s"GET ${routes.OptOutController.start.url}" should {

    "display content for start" in {

      expectIsArnAllowed(allowed = true)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetSessionItem(OPT_IN_STATUS, OptedInSingleUser)

      val result = controller.start()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Turn off access groups - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Turn off access groups"
      html.select(Css.insetText)
        .text() shouldBe "If you turn off this feature any access groups you have created will be disabled. The groups can be turned on again at any time."
      //if adding a para please test it!
      val paragraphs = html.select(Css.paragraphs)
      paragraphs.size() shouldBe 2
      paragraphs.get(0)
        .text() shouldBe "Turning off access groups will mean that all your team members can view and manage the tax affairs of all your clients. We recommend checking with your team before turning off access groups."
      paragraphs.get(1)
        .text() shouldBe "If you turn access groups back on your groups will be restored. Team members will only be able to manage the clients in their access groups."
      html.select(Css.linkStyledAsButton).get(0).text() shouldBe "Cancel"
      html.select(Css.linkStyledAsButton).get(0).attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
      html.select(Css.linkStyledAsButton).get(1).text() shouldBe "Continue"
      html.select(Css.linkStyledAsButton).get(1).attr("href") shouldBe "/agent-permissions/confirm-turn-off"
    }

  }

  s"GET showDoYouWantToOptOut on url ${routes.OptOutController.showDoYouWantToOptOut.url}" should {

    "display expected content" in {

      expectIsArnAllowed(allowed = true)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetSessionItem(OPT_IN_STATUS, OptedInSingleUser)

      val result = controller.showDoYouWantToOptOut()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Do you want to turn off access groups? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Do you want to turn off access groups?"
      html.select(Css.form).attr("action") shouldBe "/agent-permissions/confirm-turn-off"

      val answerRadios = html.select(Css.radioButtonsField("answer-radios"))
      answerRadios.select("label[for=answer]").text() shouldBe "Yes, I want to turn access groups off"
      answerRadios.select("label[for=answer-no]").text() shouldBe "No, I want to keep access groups turned on"

      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }
  }

  s"POST to submitDoYouWantToOptOut on url ${routes.OptOutController.submitDoYouWantToOptOut.url}" should {

    s"redirect to ${routes.OptOutController.showYouHaveOptedOut} page with answer 'true'" in {

      expectIsArnAllowed(allowed = true)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetSessionItem(OPT_IN_STATUS, OptedInSingleUser)
      expectOptOut(arn)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", "/opt-out/do-you-want-to-opt-out")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitDoYouWantToOptOut()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.OptOutController.showYouHaveOptedOut.url
    }

    "redirect to 'manage dashboard' page when user decides not to opt out" in {

      expectIsArnAllowed(allowed = true)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetSessionItem(OPT_IN_STATUS, OptedInSingleUser)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${routes.OptOutController.submitDoYouWantToOptOut}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitDoYouWantToOptOut()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("http://localhost:9401/agent-services-account/manage-account")
    }

    "render correct error messages when form not filled in" in {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${routes.OptOutController.submitDoYouWantToOptOut}")
          .withFormUrlEncodedBody("answer" -> "")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectIsArnAllowed(allowed = true)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetSessionItem(OPT_IN_STATUS, OptedInSingleUser)

      val result = controller.submitDoYouWantToOptOut()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Do you want to turn off access groups? - Agent services account - GOV.UK"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes to turn off access groups"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes to turn off access groups"

      html.select(Css.submitButton)

    }
  }

  s"GET showYouHaveOptedOut on url: ${routes.OptOutController.showYouHaveOptedOut.url}" should {

    "display expected content" in {

      expectIsArnAllowed(allowed = true)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetSessionItem(OPT_IN_STATUS, OptedOutEligible)
      expectDeleteSessionItems(sessionKeys)

      val result = controller.showYouHaveOptedOut()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "You have turned off access groups - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "You have turned off access groups"
      html.select(Css.H2).text() shouldBe "What happens next"
      html.select(Css.paragraphs).get(0)
        .text() shouldBe "You need to sign out and sign back in to see this change, after which all team members will be able to view and manage the tax affairs of all clients."
      html.select(Css.paragraphs).get(1)
        .text() shouldBe "Your account will show that you have chosen to turn off access groups. If you wish to turn this feature on again you can do so from your agent services ‘Manage account‘ page."
      html.select(Css.link).get(0)
        .text() shouldBe "Return to manage account"
      html.select(Css.link).get(0)
        .attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
    }
  }

}
