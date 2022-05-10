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

import play.api.http.Status.{FORBIDDEN, SEE_OTHER}
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation, status}
import helpers.BaseISpec
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.{AuthConnector, InsufficientEnrolments, MissingBearerToken}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AuthActionISpec  extends BaseISpec {

//  override lazy val conf = fakeApplication().configuration
//  override lazy val env = fakeApplication().environment
//
//  "Auth Action" when {
//    "the user hasn't logged in" should {
//      "redirect the user to log in " in {
//        val authAction = new AuthAction(new FakeFailingAuthConnector(new MissingBearerToken), env, conf)
//
//        implicit val request = FakeRequest("GET", "/BLAH")
//
//        val result = authAction.withAuthorisedAgent((arn) => Future.successful(Ok("whatever")))
//        status(result) shouldBe SEE_OTHER
//        redirectLocation(result).get shouldBe
//          "http://localhost:9553/bas-gateway/sign-in?continue_url=http%3A%2F%2Flocalhost%3A9452%2FBLAH&origin=agent-permissions-frontend"
//      }
//
//      "redirect the user to log in when insufficient enrolments" in {
//        val authAction = new AuthAction(new FakeFailingAuthConnector(new InsufficientEnrolments), env, conf)
//
//        implicit val request = FakeRequest("GET", "/BLAH")
//
//        val result = authAction.withAuthorisedAgent((arn) => Future.successful(Ok("whatever")))
//        status(result) shouldBe FORBIDDEN
//      }
//    }
//
//
//  }
//}
//
//class FakeFailingAuthConnector(exceptionToReturn: Throwable) extends AuthConnector {
//  val serviceUrl: String = ""
//
//  override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
//    Future.failed(exceptionToReturn)


}
