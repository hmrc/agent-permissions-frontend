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
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, redirectLocation}
import services.{GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.OptedInReady
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

class CreateGroupSelectNameControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]
  implicit val mockSessionCacheService: SessionCacheService = mock[SessionCacheService]
  private val groupName = "XYZ"

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[GroupService]).toInstance(mockGroupService)
      bind(classOf[SessionCacheService]).toInstance(mockSessionCacheService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder.configure("mongodb.uri" -> mongoUri).build()

  val controller: CreateGroupSelectNameController = fakeApplication.injector.instanceOf[CreateGroupSelectNameController]

  private val ctrlRoute: ReverseCreateGroupSelectNameController = routes.CreateGroupSelectNameController

  s"GET ${ctrlRoute.showGroupName.url}" should {

    "have correct layout and content and existing session keys should be cleared" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, "My Shiny Group")
      expectDeleteSessionItems(sessionKeys)

      val result = controller.showGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Create an access group - Agent services account - GOV.UK"
      html.select(Css.backLink).attr("href") shouldBe "http://localhost:9401/agent-services-account/manage-account"
      html.select(Css.backLink).text() shouldBe "Back"
      html.select(Css.H1).text() shouldBe "Create an access group"
      html.select(Css.form).attr("action") shouldBe ctrlRoute.showGroupName.url
      html.select(Css.labelFor("name")).text() shouldBe "What do you want to call this access group?"
      html.select(Css.form + " input[name=name]").size() shouldBe 1
      html.select(Css.submitButton).text() shouldBe "Continue"

      html.select(".hmrc-report-technical-issue").text() shouldBe "Is this page not working properly? (opens in new tab)"
      html.select(".hmrc-report-technical-issue").attr("href") startsWith "http://localhost:9250/contact/report-technical-problem?newTab=true&service=AOSS"
    }

  }

  s"POST ${ctrlRoute.showGroupName.url}" should {

    "redirect to confirmation page with when posting a valid group name" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      val groupName = "My Group Name"
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitGroupName.url)
          .withFormUrlEncodedBody("name" -> groupName)
          .withHeaders("Authorization" -> s"Bearer $groupName")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectPutSessionItem(GROUP_NAME, groupName)
      expectPutSessionItem(GROUP_NAME_CONFIRMED, false)

      val result = controller.submitGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showConfirmGroupName.url
    }

    "render correct error messages when form not filled in" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitGroupName.url)
          .withFormUrlEncodedBody("name" -> "")
          .withHeaders("Authorization" -> s"Bearer $groupName")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)

      val result = controller.submitGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Create an access group - Agent services account - GOV.UK"
      html
        .select(Css.errorSummaryForField("name"))
        .text() shouldBe "Enter an access group name"
      html
        .select(Css.errorForField("name"))
        .text() shouldBe "Error: Enter an access group name"

    }

    "render correct error messages when name exceeds 32 chars" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitGroupName.url)
          .withFormUrlEncodedBody("name" -> RandomStringUtils.randomAlphanumeric(33))
          .withHeaders("Authorization" -> s"Bearer $groupName")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)

      val result = controller.submitGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Create an access group - Agent services account - GOV.UK"
      html
        .select(Css.errorSummaryForField("name"))
        .text() shouldBe "Access group name must be 32 characters or fewer"
      html
        .select(Css.errorForField("name"))
        .text() shouldBe "Error: Access group name must be 32 characters or fewer"
    }
  }

  "GET /group/confirm-name" should {

    "have correct layout and content" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)

      val result = controller.showConfirmGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Confirm access group name - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Confirm access group name"
      html.select(Css.form).attr("action") shouldBe ctrlRoute.showConfirmGroupName.url
      html.select(Css.legend).text() shouldBe s"Is the access group name ‘$groupName’ correct?"
      html.select("label[for=answer]").text() shouldBe "Yes"
      html.select("label[for=answer-no]").text() shouldBe "No"
      html.select(Css.form + " input[name=answer]").size() shouldBe 2
      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }

    "redirect to /group/group-name when there is no groupName in the session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME) // <-- We are testing this. no group in session

      val result = controller.showConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showGroupName.url
    }
  }

  "POST /group/confirm-name" should {
    "render correct error messages when nothing is submitted" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      implicit val request =
        FakeRequest("POST", ctrlRoute.submitConfirmGroupName.url)
          .withFormUrlEncodedBody("answer" -> "")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Confirm access group name - Agent services account - GOV.UK"
      html
        .select(Css.errorSummaryForField("answer"))
        .text() shouldBe "Select yes if the access group name is correct"
      html
        .select(Css.errorForField("answer"))
        .text() shouldBe "Error: Select yes if the access group name is correct"

    }

    "redirect to /group/group-name when there is no name in session" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      implicit val request =
        FakeRequest("POST", ctrlRoute.submitConfirmGroupName.url)
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME) // <-- We are testing this. no group in session

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showGroupName.url
    }

    s"redirect to ${ctrlRoute.showAccessGroupNameExists.url} when the access group name already exists" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGroupNameCheck(ok = false)(arn, groupName)

      implicit val request =
        FakeRequest("POST", ctrlRoute.submitConfirmGroupName.url)
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showAccessGroupNameExists.url
    }

    "redirect to add-clients page when Confirm access group name 'yes' selected" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGroupNameCheck(ok = true)(arn, groupName)

      implicit val request =
        FakeRequest("POST", ctrlRoute.submitGroupName.url)
          .withFormUrlEncodedBody("name" -> groupName, "answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectPutSessionItem(GROUP_NAME_CONFIRMED, true)

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.CreateGroupSelectClientsController.showSelectClients(None, None)
        .url)
    }

    "redirect to /group/group-name when Confirm access group name 'no' selected" in {
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      implicit val request = FakeRequest("POST", ctrlRoute.submitConfirmGroupName.url)
        .withFormUrlEncodedBody("name" -> groupName, "answer" -> "false")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(
        ctrlRoute.showGroupName.url)
    }
  }

  s"GET ${ctrlRoute.showAccessGroupNameExists.url}" should {

    "display the right content" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)

      val result = controller.showAccessGroupNameExists()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Access group name already exists - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Access group name already exists"
      html
        .select(Css.paragraphs)
        .get(0)
        .text shouldBe s"You already have an access group called ’$groupName’. Please enter a new access group name."
      html
        .select(Css.linkStyledAsButton)
        .text shouldBe "Enter a new access group name"
      html
        .select(Css.linkStyledAsButton)
        .attr("href") shouldBe s"${ctrlRoute.showGroupName.url}"
    }
  }

}