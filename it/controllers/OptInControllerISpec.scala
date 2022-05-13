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
import helpers.{BaseISpec, Css}
import org.jsoup.Jsoup
import play.api.Application
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{OptedOutEligible, OptedOutSingleUser}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import uk.gov.hmrc.http.UpstreamErrorResponse

class OptInControllerISpec extends BaseISpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]


  override def moduleWithOverrides = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessioncacheRepo)
    }
  }


  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val controller = fakeApplication.injector.instanceOf[OptInController]

  "GET /opt-in/start" should {

    "display content for start" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptinStatusOk(arn)(OptedOutEligible)

      val result = controller.start()(request)
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Opting in to use access groups - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Opting in to use access groups"
      html.select(Css.insetText).text() shouldBe "By default, agent services accounts allow all users to view and manage the tax affairs of all clients using a shared login"
      //if adding a para please test it!
      val paragraphs = html.select(Css.paragraphs)
      paragraphs.size() shouldBe 2
      paragraphs.get(0).text() shouldBe "If you opt in to use access groups you can create groups of clients based on client type, tax services, regions or your team members internal working groups."
      paragraphs.get(1).text() shouldBe "This feature is designed for agent services accounts that have multiple clients and want to manage team member access rights to their clients tax information."
      html.select(Css.linkStyledAsButton).text() shouldBe "Continue"
      html.select(Css.linkStyledAsButton).attr("href") shouldBe "/agent-permissions/opt-in/do-you-want-to-opt-in"
    }

    "return Forbidden when user is not an Agent" in {

      val nonAgentEnrolmentKey = "IR-SA"
      val mockedAuthResponse = Enrolments(Set(Enrolment(nonAgentEnrolmentKey, agentEnrolmentIdentifiers, "Activated"))) and Some(User)
      stubAuthorisationGrantAccess(mockedAuthResponse)

      val result = controller.start()(request)

      status(result) shouldBe FORBIDDEN
    }

    "return Forbidden when user is not an admin" in {

      val mockedAuthResponse = Enrolments(Set(Enrolment(agentEnrolment, agentEnrolmentIdentifiers, "Activated"))) and Some(Assistant)
      stubAuthorisationGrantAccess(mockedAuthResponse)

      val result = controller.start()(request)

      status(result) shouldBe FORBIDDEN
    }

    "return Forbidden when user is not eligible" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptinStatusOk(arn)(OptedOutSingleUser)

      val result = controller.start()(request)
      status(result) shouldBe FORBIDDEN
    }
  }

  "GET /opt-in/do-you-want-to-opt-in" should {
    "display expected content" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptinStatusOk(arn)(OptedOutEligible)

      val result = controller.showDoYouWantToOptIn()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Do you want to opt in to use access groups? - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Do you want to opt in to use access groups?"
      html.select(Css.form).attr("action") shouldBe "/agent-permissions/opt-in/do-you-want-to-opt-in"

      val answerRadios = html.select(Css.radioButtonsField("answer"))
      answerRadios.select("label[for=true]").text() shouldBe "Yes, I want to opt-in"
      answerRadios.select("label[for=false]").text() shouldBe "No, I want to remain opted-out"

      html.select(Css.SUBMIT_BUTTON).text() shouldBe "Save and continue"
    }
  }

  "POST /opt-in/do-you-want-to-opt-in" should {

    "redirect to 'you have opted in' page with answer 'true'" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptinStatusOk(arn)(OptedOutEligible)
      stubPostOptinAccepted(arn)

      val result = controller.submitDoYouWantToOptIn()(
        FakeRequest("POST", "/opt-in/do-you-want-to-opt-in")
          .withFormUrlEncodedBody("answer" -> "true")
      )
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.OptInController.showYouHaveOptedIn.url
    }

    "redirect to 'you have not opted in' page with answer 'false'" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptinStatusOk(arn)(OptedOutEligible)

      val result = controller.submitDoYouWantToOptIn()(
        FakeRequest("POST", "/opt-in/do-you-want-to-opt-in")
          .withFormUrlEncodedBody("answer" -> "false")
      )
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/agent-permissions/opt-in/you-have-not-opted-in")
    }

    "render correct error messages when form not filled in" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptinStatusOk(arn)(OptedOutEligible)

      val result = controller.submitDoYouWantToOptIn()(
        FakeRequest("POST", "/opt-in/do-you-want-to-opt-in")
        .withFormUrlEncodedBody("" -> "")
      )

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Do you want to opt in to use access groups? - Manage Agent Permissions - GOV.UK"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Please select an option."
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Please select an option."

      html.select(Css.SUBMIT_BUTTON)

    }

    "throw exception when there was a problem with optin call" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptinStatusOk(arn)(OptedOutEligible)
      stubPostOptinError(arn)

      intercept[UpstreamErrorResponse]{
        await(controller.submitDoYouWantToOptIn()(
          FakeRequest("POST", "/opt-in/do-you-want-to-opt-in")
            .withFormUrlEncodedBody("answer" -> "true"))
        )
      }
    }
  }

  "GET /opt-in/you-have-opted-in" should {
    "display expected content" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)

      val result = controller.showYouHaveOptedIn()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "You have opted in to use access groups - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "You have opted in to use access groups"

      html.select(Css.H2).text() shouldBe "What happens next"

      html.select(Css.paragraphs).get(0).text() shouldBe "You now need to create access groups and assign clients and team members to them."

      html.select(Css.linkStyledAsButton).text() shouldBe "Create an access group"
      html.select(Css.linkStyledAsButton).attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
    }
  }

  "GET /opt-in/you-have-not-opted-in" should {
    "display expected content" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)

      val result = controller.showYouHaveNotOptedIn()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "You have not opted-in to use access groups - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "You have not opted-in to use access groups"
      html.select(Css.H2).text() shouldBe "What happens next"

      html.select(Css.paragraphs).get(0).text() shouldBe "You can opt in at any time later"

      html.select(Css.linkStyledAsButton).text() shouldBe "Back to manage groups page"
      html.select(Css.linkStyledAsButton).attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"

    }
  }

  lazy val sessioncacheRepo: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)
}
