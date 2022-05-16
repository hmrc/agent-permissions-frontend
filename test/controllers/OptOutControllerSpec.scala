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
import uk.gov.hmrc.agentmtdidentifiers.model.OptedInSingleUser
import uk.gov.hmrc.auth.core._

class OptOutControllerSpec extends BaseISpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]


  override def moduleWithOverrides = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepository)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val controller = fakeApplication.injector.instanceOf[OptOutController]

  "GET /opt-out/start" should {

    "display content for start" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInSingleUser)

      val result = controller.start()(request)
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Opting out of using access groups - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Opting out of using access groups"
      html.select(Css.insetText).text() shouldBe "If you opt out of using this feature any access groups you have created will be removed."
      //if adding a para please test it!
      val paragraphs = html.select(Css.paragraphs)
      paragraphs.size() shouldBe 2
      paragraphs.get(0).text() shouldBe "Opting out will mean that all users will be able to view and manage the tax affairs of all clients."
      paragraphs.get(1).text() shouldBe "You can opt in to use access groups later but you will have to create them again and reassign clients and team members to each group."
      html.select(Css.linkStyledAsButton).text() shouldBe "Continue"
      html.select(Css.linkStyledAsButton).attr("href") shouldBe "/agent-permissions/opt-out/do-you-want-to-opt-out"
    }

  }

  "GET /opt-out/do-you-want-to-opt-out" should {
    "display expected content" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInSingleUser)

      val result = controller.showDoYouWantToOptOut()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Do you want to opt out of using access groups? - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Do you want to opt out of using access groups?"
      html.select(Css.form).attr("action") shouldBe "/agent-permissions/opt-out/do-you-want-to-opt-out"

      val answerRadios = html.select(Css.radioButtonsField("answer"))
      answerRadios.select("label[for=true]").text() shouldBe "Yes, I want to opt out"
      answerRadios.select("label[for=false]").text() shouldBe "No, I want to stay opted in"

      html.select(Css.SUBMIT_BUTTON).text() shouldBe "Save and continue"
    }
  }

  "POST /opt-out/do-you-want-to-opt-out" should {

    "redirect to 'you have opted out' page with answer 'true'" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInSingleUser)
      stubPostOptOutAccepted(arn)

      val result = controller.submitDoYouWantToOptOut()(
        FakeRequest("POST", "/opt-out/do-you-want-to-opt-out")
          .withFormUrlEncodedBody("answer" -> "true")
      )
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.OptOutController.showYouHaveOptedOut.url
    }

    "redirect to 'manage dashboard' page when user decides not to opt out" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInSingleUser)

      val result = controller.submitDoYouWantToOptOut()(
        FakeRequest("POST", "/opt-out/do-you-want-to-opt-out")
          .withFormUrlEncodedBody("answer" -> "false")
      )
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("http://localhost:9401/agent-services-account/manage-account")
    }

    "render correct error messages when form not filled in" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInSingleUser)

      val result = controller.submitDoYouWantToOptOut()(
        FakeRequest("POST", "/opt-out/do-you-want-to-opt-out")
        .withFormUrlEncodedBody("" -> "")
      )

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Do you want to opt out of using access groups? - Manage Agent Permissions - GOV.UK"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Please select an option."
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Please select an option."

      html.select(Css.SUBMIT_BUTTON)

    }
  }

  "GET /opt-out/you-have-opted-out" should {
    "display expected content" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInSingleUser)

      val result = controller.showYouHaveOptedOut()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "You have opted out of using access groups - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "You have opted out of using access groups"

      html.select(Css.H2).text() shouldBe "What happens next"

      html.select(Css.paragraphs).get(0).text() shouldBe "You will need to log out and log back in to see this change, after which all users will be able to view all clients."
      html.select(Css.paragraphs).get(1).text() shouldBe "Your account will show that you have chosen to opt out of using access groups. If you wish to opt in to use this feature again you can do so from your agent services manage account page."

      html.select(Css.linkStyledAsButton).text() shouldBe "Return to manage account"
      html.select(Css.linkStyledAsButton).attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
    }
  }

  lazy val sessionCacheRepository: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)
}
