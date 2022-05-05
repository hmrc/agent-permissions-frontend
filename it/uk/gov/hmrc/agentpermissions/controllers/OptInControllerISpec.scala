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
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.agentpermissions.helpers.{BaseISpec, Css}
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments, User}

class OptInControllerISpec extends BaseISpec {

  val agentEnrolment = "HMRC-AS-AGENT"
  val agentReferenceNumberIdentifier = "AgentReferenceNumber"
  val validArn = "TARN0000001"
  lazy val controller: OptInController = fakeApplication().injector.instanceOf[OptInController]

  "GET /agent-permissions/opt-in" should {
    "go to opt-in page when user is valid agent user" in {

      val enrolmentIdentifiers: Seq[EnrolmentIdentifier] = Seq(EnrolmentIdentifier(agentReferenceNumberIdentifier, validArn))
      val mockedAuthResponse = Enrolments(Set(Enrolment(agentEnrolment, enrolmentIdentifiers, "Activated"))) and Some(User)
      stubAuthorisationGrantAccess(mockedAuthResponse)
      val request = FakeRequest("GET", "/agent-permission/opt-in")
        .withHeaders("Authorization" -> "Bearer some-token", "X-Session-ID" -> "whatever")

      val result = controller.start()(request)

      Helpers.status(result) shouldBe 200
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Opting in to use access groups - Manage Agent Permissions - GOV.UK"
      html.select(Css.H1).text() shouldBe "Opting in to use access groups"
      //if adding a para please test it!
//      html.select(Css.paragraphs).size() shouldBe 1
    }
  }

//  "GET /agent-permissions/opt-in" should {
//    "redirect to not permitted page when user is not a valid agent user" in {
//      val mockedAuthResponse = Set(Enrolment("????")) and Some(Assistant)
//      stubAuthorisationGrantAccess(mockedAuthResponse)
//      //      val request = FakeRequest("GET", "/agent-permission/opt-in")
//
//
//    }
//  }


}
