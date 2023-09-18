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
import connectors.{AgentClientAuthorisationConnector, AgentPermissionsConnector}
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
import uk.gov.hmrc.agents.accessgroups.optin.OptedInReady
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

class CreateGroupSelectNameControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentClientAuthConnector: AgentClientAuthorisationConnector = mock[AgentClientAuthorisationConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]
  implicit val mockSessionCacheService: SessionCacheService = mock[SessionCacheService]
  private val groupName = "XYZ"

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector, mockAgentClientAuthConnector,mockSessionCacheService))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[GroupService]).toInstance(mockGroupService)
      bind(classOf[SessionCacheService]).toInstance(mockSessionCacheService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder.configure("mongodb.uri" -> mongoUri).build()

  val controller: CreateGroupSelectNameController = fakeApplication.injector.instanceOf[CreateGroupSelectNameController]

  private val ctrlRoute: ReverseCreateGroupSelectNameController = routes.CreateGroupSelectNameController

  def expectAuthOkOptedInReadyWithGroupType(groupType :String = CUSTOM_GROUP): Unit = {
    expectAuthorisationGrantsAccess(mockedAuthResponse)
    expectIsArnAllowed(allowed = true)
    expectGetSessionItem(SUSPENSION_STATUS, false)
    expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
    expectGetSessionItem(GROUP_TYPE, groupType)
  }

  s"GET ${ctrlRoute.showGroupName().url}" should {

    "render choose_name with correct content" in {
      expectAuthOkOptedInReadyWithGroupType()
      expectGetSessionItem(GROUP_NAME, "My Shiny Group")

      val result = controller.showGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "What do you want to call this group? - Agent services account - GOV.UK"
      html.select(Css.backLink).attr("href") shouldBe "#"
      html.select(Css.backLink).text() shouldBe "Back"
      html.select(Css.H1).text() shouldBe "What do you want to call this group?"
      html.select(Css.form).attr("action") shouldBe ctrlRoute.showGroupName().url
      html.select(Css.labelFor("name")).text() shouldBe "Access group name"
      html.select(Css.form + " input[name=name]").size() shouldBe 1
      html.select(Css.submitButton).text() shouldBe "Save and continue"

    }

    "have correct back link if tax group" in {
      expectAuthOkOptedInReadyWithGroupType(TAX_SERVICE_GROUP)
      expectGetSessionItem(GROUP_NAME, "My Shiny Group")

      val result = controller.showGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.select(Css.backLink).attr("href") shouldBe "#"
      html.select(Css.backLink).text() shouldBe "Back"
    }

  }

  s"POST ${ctrlRoute.showGroupName().url}" should {

    "redirect to confirmation page with when posting a valid group name" in {
      expectAuthOkOptedInReadyWithGroupType()

      val groupName = "My Group Name"
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitGroupName().url)
          .withFormUrlEncodedBody("name" -> groupName)
          .withHeaders("Authorization" -> s"Bearer $groupName")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectPutSessionItem(GROUP_NAME, groupName)

      val result = controller.submitGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showConfirmGroupName().url
    }

    "render correct error messages when form not filled in" in {
      expectAuthOkOptedInReadyWithGroupType()

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitGroupName().url)
          .withFormUrlEncodedBody("name" -> "")
          .withHeaders("Authorization" -> s"Bearer $groupName")
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: What do you want to call this group? - Agent services account - GOV.UK"
      html
        .select(Css.errorSummaryForField("name"))
        .text() shouldBe "Enter an access group name"
      html
        .select(Css.errorForField("name"))
        .text() shouldBe "Error: Enter an access group name"

    }

    "render correct error messages when name exceeds 50 chars" in {
      expectAuthOkOptedInReadyWithGroupType()

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitGroupName().url)
          .withFormUrlEncodedBody("name" -> RandomStringUtils.randomAlphanumeric(51))
          .withHeaders("Authorization" -> s"Bearer $groupName")
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: What do you want to call this group? - Agent services account - GOV.UK"
      html
        .select(Css.errorSummaryForField("name"))
        .text() shouldBe "Access group name must be 50 characters or fewer"
      html
        .select(Css.errorForField("name"))
        .text() shouldBe "Error: Access group name must be 50 characters or fewer"
    }
  }

  "GET /group/confirm-name" should {

    "have correct layout and content" in {
      expectAuthOkOptedInReadyWithGroupType()
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetSessionItemNone(GROUP_NAME_CONFIRMED)

      val result = controller.showConfirmGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Confirm access group name - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Confirm access group name"
      html.select(Css.form).attr("action") shouldBe ctrlRoute.showConfirmGroupName().url
      html.select(Css.legend).text() shouldBe s"Is ‘$groupName’ the correct name for this access group?"
      html.select("label[for=answer]").text() shouldBe "Yes"
      html.select("label[for=answer-no]").text() shouldBe "No"
      html.select(Css.form + " input[name=answer]").size() shouldBe 2
      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }

    "redirect to /group/group-name when there is no groupName in the session" in {
      expectAuthOkOptedInReadyWithGroupType()

      expectGetSessionItemNone(GROUP_NAME) // <-- We are testing this. no group in session

      val result = controller.showConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showGroupName().url
    }
  }

  "POST /group/confirm-name" should {
    "render correct error messages when nothing is submitted" in {
      expectAuthOkOptedInReadyWithGroupType()
      expectGetSessionItem(GROUP_NAME, groupName)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitConfirmGroupName().url)
          .withFormUrlEncodedBody("answer" -> "")
          .withSession(SessionKeys.sessionId -> "session-x")

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
      expectAuthOkOptedInReadyWithGroupType()
      expectGetSessionItemNone(GROUP_NAME) // <-- We are testing this. no group in session

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitConfirmGroupName().url)
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showGroupName().url
    }

    s"redirect to ${ctrlRoute.showAccessGroupNameExists().url} when the access group name already exists" in {
      expectAuthOkOptedInReadyWithGroupType()
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGroupNameCheckConflict(arn, groupName)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitConfirmGroupName().url)
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showAccessGroupNameExists().url
    }

    "redirect to search clients page when Confirm access group name 'yes' selected" in {
      expectAuthOkOptedInReadyWithGroupType()

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitGroupName().url)
          .withFormUrlEncodedBody("name" -> groupName, "answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(GROUP_NAME, groupName)
      expectPutSessionItem(GROUP_NAME_CONFIRMED, true)
      expectGroupNameCheckOK(arn, groupName)

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.CreateGroupSelectClientsController.showSearchClients
        ().url)
    }

    "redirect to /group/group-name when Confirm access group name 'no' selected" in {
      expectAuthOkOptedInReadyWithGroupType()
      expectGetSessionItem(GROUP_NAME, groupName)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitConfirmGroupName().url)
        .withFormUrlEncodedBody("name" -> groupName, "answer" -> "false")
        .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitConfirmGroupName()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(
        ctrlRoute.showGroupName().url)
    }
  }

  s"GET ${ctrlRoute.showAccessGroupNameExists().url}" should {

    "display the right content" in {
      expectAuthOkOptedInReadyWithGroupType()
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
        .attr("href") shouldBe s"${ctrlRoute.showGroupName().url}"
    }
  }

}
