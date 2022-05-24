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
import helpers.{BaseISpec, Css}
import models.JourneySession
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{Enrolment, Identifier, OptedInReady}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

class GroupControllerSpec extends BaseISpec {

  lazy val sessionCacheRepo: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]

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

  val controller = fakeApplication.injector.instanceOf[GroupController]


  "create group" should {
    "have correct layout and content" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)
      val result = controller.showCreateGroup()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Create an access group - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Create an access group"
      html.select(Css.form).attr("action") shouldBe "/agent-permissions/group/create"
      html.select(Css.labelFor("name")).text() shouldBe "What do you want to call this access group?"
      html.select(Css.form + " input[name=name]").size() shouldBe 1
      html.select(Css.SUBMIT_BUTTON).text() shouldBe "Save and continue"
    }
  }

  "POST to /group/create" should {

    "redirect to confirmation page with when posting a valid group name" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)
      val result = controller.submitCreateGroup()(
        FakeRequest("POST", routes.GroupController.submitCreateGroup.url)
          .withFormUrlEncodedBody("name" -> "My Group Name")
      )
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showConfirmGroupName.url
    }

    "render correct error messages when form not filled in" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)
      val result = controller.submitCreateGroup()(
        FakeRequest("POST", routes.GroupController.submitCreateGroup.url)
          .withFormUrlEncodedBody("name" -> "")
      )
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Create an access group - Manage Agent Permissions - GOV.UK"
      html.select(Css.errorSummaryForField("name")).text() shouldBe "Group name is required"
      html.select(Css.errorForField("name")).text() shouldBe "Error: Group name is required"

    }

    "render correct error messages when name exceeds 32 chars" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)

      val result = controller.submitCreateGroup()(
        FakeRequest("POST", routes.GroupController.submitCreateGroup.url)
          .withFormUrlEncodedBody("name" -> RandomStringUtils.random(33))
      )
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Create an access group - Manage Agent Permissions - GOV.UK"
      html.select(Css.errorSummaryForField("name")).text() shouldBe "Group name must be less than 32 characters long"
      html.select(Css.errorForField("name")).text() shouldBe "Error: Group name must be less than 32 characters long"

    }
  }

  "GET confirm create group" should {

    "have correct layout and content" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)

      val request = FakeRequest()
        .withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x", "groupName" -> "XYZ")
      val result = controller.showConfirmGroupName()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Confirm group name for XYZ - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Confirm group name for XYZ"
      html.select(Css.form).attr("action") shouldBe "/agent-permissions/group/confirm-name"
      html.select(Css.legend).text() shouldBe "Is the access group name correct?"
      html.select("label[for=answer-yes]").text() shouldBe "Yes"
      html.select("label[for=answer-no]").text() shouldBe "No"
      html.select(Css.form + " input[name=answer]").size() shouldBe 2
      html.select(Css.SUBMIT_BUTTON).text() shouldBe "Continue"
    }

    "render correct error messages when name exceeds 32 chars" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)

      val result = controller.submitConfirmGroupName()(
        FakeRequest("POST", routes.GroupController.submitCreateGroup.url)
          .withFormUrlEncodedBody("name" -> "XYZ")
      )
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Confirm group name for XYZ - Manage Agent Permissions - GOV.UK"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Please select an option."
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Please select an option."

    }

    "redirect to add-clients page when confirm group name 'yes' selected" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)

      val result = controller.submitConfirmGroupName()(
        FakeRequest("POST", routes.GroupController.submitCreateGroup.url)
          .withFormUrlEncodedBody("name" -> "XYZ", "answer" -> "true")
      )
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.GroupController.showAddClients.url)
    }

    "redirect to create group page when confirm group name 'no' selected" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)

      val result = controller.submitConfirmGroupName()(
        FakeRequest("POST", routes.GroupController.submitCreateGroup.url)
          .withFormUrlEncodedBody("name" -> "XYZ", "answer" -> "false")
      )
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.GroupController.showCreateGroup.url)


    }
  }

  "GET group list" should {

    "render with clients (enrolments)" in {

      val fakeEnrolments = (1 to 10)
        .map(i => {
          Enrolment(
            s"serviceName$i",
            s"state$i",
            s"friendly$i",
            List(Identifier("", s"ABCDEF$i")))
        })

      stubAuthorisationGrantAccess(mockedAuthResponse)
      sessionCacheRepo.putSession(DATA_KEY, JourneySession(OptedInReady, Some(fakeEnrolments)))

      val result = controller.showAddClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Add clients to - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Add clients to"

      val th = html.select(Css.tableWithId("client-list-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "HMRC reference"
      th.get(2).text() shouldBe "Client name"
      th.get(3).text() shouldBe "Tax service"
      val trs = html.select(Css.tableWithId("client-list-table")).select("tbody tr")
      trs.size() shouldBe 10
      //first row
      trs.get(0).select("td").get(1).text() shouldBe "xxxxDEF1"
      trs.get(0).select("td").get(2).text() shouldBe "friendly1"
      trs.get(0).select("td").get(3).text() shouldBe "serviceName1"

      //last row
      trs.get(9).select("td").get(1).text() shouldBe "xxxxEF10"
      trs.get(9).select("td").get(2).text() shouldBe "friendly10"
      trs.get(9).select("td").get(3).text() shouldBe "serviceName10"
    }

    "render with NO CLIENTS" in {

      stubAuthorisationGrantAccess(mockedAuthResponse)
      sessionCacheRepo.putSession(DATA_KEY, JourneySession(OptedInReady, None))

      val result = controller.showAddClients()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Add clients to - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Add clients to"
      val th = html.select(Css.tableWithId("client-list-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "HMRC reference"
      th.get(2).text() shouldBe "Client name"
      th.get(3).text() shouldBe "Tax service"
      val trs = html.select(Css.tableWithId("client-list-table")).select("tbody tr")
      trs.size() shouldBe 0

    }
  }

  "POST add clients to list" should {
    "render with clients (enrolments)" in {
      stubAuthorisationGrantAccess(mockedAuthResponse)
      stubOptInStatusOk(arn)(OptedInReady)

      val result = controller.submitCreateGroup()(
        FakeRequest("POST", routes.GroupController.submitAddClients.url)
          .withFormUrlEncodedBody("" -> "")
      )
      status(result) shouldBe OK
      redirectLocation(result) shouldBe None

    }
  }

}
