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

import controllers.AuthAction
import helpers.BaseISpec
import play.api.http.Status.SEE_OTHER
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, status}
import uk.gov.hmrc.auth.core.{AuthConnector, MissingBearerToken}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class AuthActionISpec  extends BaseISpec {

  "Auth Action" when {
    "the user hasn't logged in" must {
      "redirect the user to log in " in {
        val authAction = new AuthAction(
          new FakeFailingAuthConnector(new MissingBearerToken),
          fakeApplication().environment,
          fakeApplication().configuration
        )

        implicit val request = FakeRequest("GET", "/agent-permission/opt-in/start")
          .withHeaders("Authorization" -> "Bearer some-token", "X-Session-ID" -> UUID.randomUUID().toString)

        val result = authAction.withAuthorisedAgent((arn) => Future.successful(Ok("whatever")))
        status(result) shouldBe SEE_OTHER
        //        redirectLocation(result).get
      }
    }


  }
}

class FakeFailingAuthConnector(exceptionToReturn: Throwable) extends AuthConnector {
  val serviceUrl: String = ""

  override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
    Future.failed(exceptionToReturn)


}
