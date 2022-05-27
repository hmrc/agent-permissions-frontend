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
import helpers.{BaseSpec, Css}
import models.JourneySession
import org.jsoup.Jsoup
import play.api.Application
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{OptedInNotReady, OptedInReady, OptedOutEligible, OptedOutSingleUser}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import uk.gov.hmrc.http.{SessionKeys, UpstreamErrorResponse}

class OptInControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  lazy val sessionCacheRepo: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)


  override def moduleWithOverrides = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepo)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
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
      stubOptInStatusOk(arn)(OptedOutEligible)

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
      paragraphs.get(1).text() shouldBe "This feature is designed for agent services accounts that have multiple clients and want to manage team member access rights to their client?s tax information."
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

    "redirect user to root when user is not eligible" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedOutSingleUser)

      val result = controller.start()(request)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.RootController.start.url
    }
  }

  "GET /opt-in/do-you-want-to-opt-in" should {
    "display expected content" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedOutEligible)

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

  import helpers.TestData._

  "POST /opt-in/do-you-want-to-opt-in" should {

    "redirect to 'you have opted in' page with answer 'true'" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)

      implicit val request = FakeRequest("POST", "/opt-in/do-you-want-to-opt-in")
        .withFormUrlEncodedBody("answer" -> "true")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(DATA_KEY, JourneySession(optInStatus = OptedOutEligible)))

      stubPostOptInAccepted(arn)
      stubGetClientListOk(arn)(clientListData)
      stubOptInStatusOk(arn)(OptedInReady)

      val result = controller.submitDoYouWantToOptIn()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.OptInController.showYouHaveOptedIn.url
    }

    "redirect to 'you have not opted in' page with answer 'false'" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)

      implicit val request = FakeRequest("POST", "/opt-in/do-you-want-to-opt-in")
        .withFormUrlEncodedBody("answer" -> "false")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(DATA_KEY, JourneySession(optInStatus = OptedOutEligible)))

      val result = controller.submitDoYouWantToOptIn()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/agent-permissions/opt-in/you-have-not-opted-in")
    }

    "render correct error messages when form not filled in" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)

      implicit val request = FakeRequest("POST", "/opt-in/do-you-want-to-opt-in")
        .withFormUrlEncodedBody("answer" -> "")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(DATA_KEY, JourneySession(optInStatus = OptedOutEligible)))

      val result = controller.submitDoYouWantToOptIn()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Do you want to opt in to use access groups? - Manage Agent Permissions - GOV.UK"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Please select an option."
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Please select an option."

      html.select(Css.SUBMIT_BUTTON)

    }

    "throw exception when there was a problem with optin call" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubPostOptInError(arn)

      implicit val request = FakeRequest("POST", "/opt-in/do-you-want-to-opt-in")
        .withFormUrlEncodedBody("answer" -> "true")
        .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(DATA_KEY, JourneySession(optInStatus = OptedOutEligible)))

      intercept[UpstreamErrorResponse] {
        await(controller.submitDoYouWantToOptIn()(request))
      }
    }
  }


  "GET /opt-in/you-have-opted-in" should {
    "display expected content with continueUrl of ASA dashboard when clientList is not in session" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession[JourneySession](DATA_KEY, JourneySession(optInStatus = OptedInNotReady, clientList = None)))

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

    "display expected content with continueUrl of /groups when clientList is in session" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      await(sessionCacheRepo.putSession[JourneySession](DATA_KEY, JourneySession(optInStatus = OptedInNotReady, clientList = Some(clientListData))))

      val result = controller.showYouHaveOptedIn()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "You have opted in to use access groups - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "You have opted in to use access groups"

      html.select(Css.H2).text() shouldBe "What happens next"

      html.select(Css.paragraphs).get(0).text() shouldBe "You now need to create access groups and assign clients and team members to them."

      html.select(Css.linkStyledAsButton).text() shouldBe "Create an access group"
      html.select(Css.linkStyledAsButton).attr("href") shouldBe routes.GroupController.showCreateGroup.url
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

}
