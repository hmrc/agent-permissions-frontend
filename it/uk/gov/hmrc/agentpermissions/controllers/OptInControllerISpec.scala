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

package uk.gov.hmrc.agentpermissions.controllers

import org.jsoup.Jsoup
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout}
import play.api.test.{FakeRequest}
import play.api.test.Helpers._
import uk.gov.hmrc.agentpermissions.helpers.{BaseISpec, Css}
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import uk.gov.hmrc.auth.core.{Assistant, Enrolment, EnrolmentIdentifier, Enrolments, User}

import java.util.UUID

class OptInControllerISpec extends BaseISpec {

  val agentEnrolment = "HMRC-AS-AGENT"
  val agentReferenceNumberIdentifier = "AgentReferenceNumber"
  val validArn = "TARN0000001"
  lazy val controller: OptInController = fakeApplication().injector.instanceOf[OptInController]
  val agentEnrolmentIdentifiers: Seq[EnrolmentIdentifier] = Seq(EnrolmentIdentifier(agentReferenceNumberIdentifier, validArn))

  "GET /agent-permissions/opt-in/start" should {

    "go to opt-in page when user is valid agent user" in {

      val mockedAuthResponse = Enrolments(Set(Enrolment(agentEnrolment, agentEnrolmentIdentifiers, "Activated"))) and Some(User)
      stubAuthorisationGrantAccess(mockedAuthResponse)
      val request = FakeRequest("GET", "/agent-permission/opt-in/start")
        .withHeaders("Authorization" -> "Bearer some-token", "X-Session-ID" -> UUID.randomUUID().toString)

      val result = controller.start()(request)

      status(result) shouldBe 200

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

    "redirect to not permitted page when user is not an Agent" in {
      val nonAgentEnrolmentKey = "IR-SA"
      val mockedAuthResponse = Enrolments(Set(Enrolment(nonAgentEnrolmentKey, agentEnrolmentIdentifiers, "Activated"))) and Some(User)
      stubAuthorisationGrantAccess(mockedAuthResponse)
      val request = FakeRequest("GET", "/agent-permission/opt-in/start")
        .withHeaders("Authorization" -> "Bearer some-token", "X-Session-ID" -> UUID.randomUUID().toString)

      val result = controller.start()(request)

      status(result) shouldBe 303
      redirectLocation(result) shouldBe Some("whatever")

    }

    "redirect to not permitted page when user has correct enrolment but is not a 'User' (Admin)" in {

    }
  }

  "GET /agent-permissions/opt-in/do-you-want-to-opt-in" should {

  }


}
