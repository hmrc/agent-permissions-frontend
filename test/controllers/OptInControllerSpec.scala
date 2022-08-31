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
import org.jsoup.Jsoup
import play.api.Application
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{OptedInNotReady, OptedInReady, OptedOutEligible, OptinStatus, OptedOutSingleUser}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import uk.gov.hmrc.http.{SessionKeys, UpstreamErrorResponse}

class OptInControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector
    : AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

  override def moduleWithOverrides = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction])
        .toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector))
      bind(classOf[AgentPermissionsConnector])
        .toInstance(mockAgentPermissionsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepo)
      bind(classOf[AgentUserClientDetailsConnector])
        .toInstance(mockAgentUserClientDetailsConnector)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val controller = fakeApplication.injector.instanceOf[OptInController]

  s"GET ${routes.OptInController.start}" should {

    "display content for start" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubOptInStatusOk(arn)(OptedOutEligible)

      val result = controller.start()(request)
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Turn on access groups - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Turn on access groups"
      html
        .select(Css.insetText)
        .text() shouldBe "By default, agent services accounts allow all team members to view and manage the tax affairs of all clients using shared sign in details."
      //if adding a para please test it!
      val paragraphs = html.select(Css.paragraphs)
      paragraphs.size() shouldBe 4
      paragraphs
        .get(0)
        .text() shouldBe "This feature is designed for agent services accounts with multiple clients who would like to assign team members to view and manage their client‘s tax affairs."
      paragraphs
        .get(1)
        .text() shouldBe "If you turn on this feature you can create access groups of clients based on client type, tax services, regions or your internal working groups."
      paragraphs
        .get(2)
        .text() shouldBe "You can then manage access permissions by assigning your team members to each access group."
      paragraphs
        .get(3)
        .text() shouldBe "Your organisation may have already created access groups and then turned this feature off. Turning access groups on will restore these groups."

      html.select(Css.linkStyledAsButton).text() shouldBe "Continue"
      html
        .select(Css.linkStyledAsButton)
        .attr("href") shouldBe "/agent-permissions/confirm-turn-on"
    }

    "return Forbidden when user is not an Agent" in {

      val nonAgentEnrolmentKey = "IR-SA"
      val mockedAuthResponse = Enrolments(
        Set(
          Enrolment(nonAgentEnrolmentKey,
                    agentEnrolmentIdentifiers,
                    "Activated"))) and Some(User)
      expectAuthorisationGrantsAccess(mockedAuthResponse)

      val result = controller.start()(request)

      status(result) shouldBe FORBIDDEN
    }

    "return Forbidden when user is not an admin" in {

      val mockedAuthResponse = Enrolments(Set(Enrolment(
        agentEnrolment,
        agentEnrolmentIdentifiers,
        "Activated"))) and Some(Assistant)
      expectAuthorisationGrantsAccess(mockedAuthResponse)

      val result = controller.start()(request)

      status(result) shouldBe FORBIDDEN
    }

    "redirect user to root when user is not eligible" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubOptInStatusOk(arn)(OptedOutSingleUser)

      val result = controller.start()(request)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.RootController.start.url
    }
  }

  s"GET ${routes.OptInController.showDoYouWantToOptIn}" should {
    "display expected content" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubOptInStatusOk(arn)(OptedOutEligible)

      val result = controller.showDoYouWantToOptIn()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Do you want to turn on access groups? - Agent services account - GOV.UK"
      html
        .select(Css.H1)
        .text() shouldBe "Do you want to turn on access groups?"
      html
        .select(Css.form)
        .attr("action") shouldBe "/agent-permissions/confirm-turn-on"

      val answerRadios = html.select(Css.radioButtonsField("answer"))
      answerRadios
        .select("label[for=true]")
        .text() shouldBe "Yes, I want to turn access groups on"
      answerRadios
        .select("label[for=false]")
        .text() shouldBe "No, I want to keep access groups turned off"

      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }
  }

  s"POST ${routes.OptInController.submitDoYouWantToOptIn}" should {

    s"redirect to '${routes.OptInController.showYouHaveOptedIn}' page with answer 'true'" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      implicit val request =
        FakeRequest("POST", s"${routes.OptInController.submitDoYouWantToOptIn}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedOutEligible))

      stubPostOptInAccepted(arn)
      stubOptInStatusOk(arn)(OptedInReady)

      val result = controller.submitDoYouWantToOptIn()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.OptInController.showYouHaveOptedIn.url
    }

    "redirect to 'ASA Manage account' page with answer 'false'" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      implicit val request =
        FakeRequest("POST", s"${routes.OptInController.submitDoYouWantToOptIn}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedOutEligible))

      val result = controller.submitDoYouWantToOptIn()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(
        "http://localhost:9401/agent-services-account/manage-account")
    }

    "render correct error messages when form not filled in" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      implicit val request =
        FakeRequest("POST", s"${routes.OptInController.submitDoYouWantToOptIn}")
          .withFormUrlEncodedBody("answer" -> "")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedOutEligible))

      val result = controller.submitDoYouWantToOptIn()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Do you want to turn on access groups? - Agent services account - GOV.UK"
      html
        .select(Css.errorSummaryForField("answer"))
        .text() shouldBe "Please select an option."
      html
        .select(Css.errorForField("answer"))
        .text() shouldBe "Error: Please select an option."

      html.select(Css.submitButton)

    }

    "throw exception when there was a problem with optin call" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      stubPostOptInError(arn)

      implicit val request =
        FakeRequest("POST", "/opt-in/do-you-want-to-opt-in")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedOutEligible))

      intercept[UpstreamErrorResponse] {
        await(controller.submitDoYouWantToOptIn()(request))
      }
    }
  }

  s"GET ${routes.OptInController.showYouHaveOptedIn}" should {
    "display expected content when client list not available yet" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(
        sessionCacheRepo.putSession[OptinStatus](OPTIN_STATUS, OptedInNotReady))

      val result = controller.showYouHaveOptedIn()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "You have turned on access groups but we need some time to gather the client data - Agent services account - GOV.UK"
      html
        .select(Css.H1)
        .text() shouldBe "You have turned on access groups but we need some time to gather the client data"

      html.select(Css.H2).text() shouldBe "What happens next"

      html
        .select(Css.paragraphs)
        .get(0)
        .text() shouldBe "Your client data is currently being processed."

      html
        .select(Css.paragraphs)
        .get(1)
        .text() shouldBe "You will receive a confirmation email to let you know when this is done and what to do next."

      html
        .select(Css.linkStyledAsButton)
        .text() shouldBe "Return to manage account"
      html
        .select(Css.linkStyledAsButton)
        .attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
    }

    "display expected content when client list is ready" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      await(
        sessionCacheRepo.putSession[OptinStatus](OPTIN_STATUS, OptedInReady))

      val result = controller.showYouHaveOptedIn()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "You have turned on access groups - Agent services account - GOV.UK"
      html
        .select(Css.H1)
        .text() shouldBe "You have turned on access groups"

      html.select(Css.H2).text() shouldBe "What happens next"

      html
        .select(Css.paragraphs)
        .get(0)
        .text() shouldBe "You can now create access groups for your clients and assign your team members to each access group. Any access groups created in the past have been restored."

      html
        .select(Css.paragraphs)
        .get(1)
        .text() shouldBe "Clients and team members can be in more than one access group and you can make changes to these assignments whenever you need to."
      html
        .select(Css.linkStyledAsButton)
        .text() shouldBe "Create access group"
      html
        .select(Css.linkStyledAsButton)
        .attr("href") shouldBe routes.GroupController.start.url
    }
  }

}
