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

import connectors.AgentPermissionsConnectorImpl
import connectors.mocks.{MockAgentPermissionsConnector, MockHttpClient}
import helpers.BaseISpec
import models.JourneySession
import play.api.mvc.Results
import play.api.test.Helpers._
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, OptedOutEligible}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.Future

class SessionBehaviourISpec extends BaseISpec with MockHttpClient with MockAgentPermissionsConnector {

  val testSessionBehaviour: SessionBehaviour =
    new SessionBehaviour {
      override val agentPermissionsConnector = new AgentPermissionsConnectorImpl(mockHttpClient)
      override val sessionCacheRepository: SessionCacheRepository = mongoSessionCacheRepository
  }

  "withEligibleToOptIn" should {
    "execute body when status is OptedOutEligible and store status in journey session if no session exists" in  {

      mockHttpGet[HttpResponse](HttpResponse.apply(200, s""" "Opted-Out_ELIGIBLE" """))
      val result = testSessionBehaviour.withEligibleToOptIn(Arn(validArn)){ Future successful Results.Ok("")}.futureValue
      val sessionStored = await(mongoSessionCacheRepository.getFromSession[JourneySession](DataKey("opting")))

      sessionStored.isDefined shouldBe true
      status(result) shouldBe 200
    }

    "if a journey session is available then don't make a call to backend" in  {

      await(mongoSessionCacheRepository.putSession(DataKey("opting"),JourneySession(optinStatus = OptedOutEligible)))
      val result = testSessionBehaviour.withEligibleToOptIn(Arn(validArn)){ Future successful Results.Ok("")}.futureValue

      status(result) shouldBe 200
    }

    "if not eligible to opt-in then return 403" in {

      mockHttpGet[HttpResponse](HttpResponse.apply(200, s""" "Opted-Out_SINGLE_USER" """))
      val result = (testSessionBehaviour.withEligibleToOptIn(Arn(validArn)){ Future successful Results.Ok("")}).futureValue
      val sessionStored = await(mongoSessionCacheRepository.getFromSession[JourneySession](DataKey("opting")))

      sessionStored.isDefined shouldBe true

      status(result) shouldBe 403
      bodyOf(result) shouldBe "not_eligible_to_opt-in"
    }
  }

}
