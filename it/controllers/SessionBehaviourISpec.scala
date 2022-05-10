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

import connectors.AgentPermissionsConnector
import connectors.mocks.MockAgentPermissionsConnector
import helpers.BaseISpec
import models.JourneySession
import play.api.mvc.Results
import play.api.test.Helpers._
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, OptedOutEligible}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.mongo.cache.DataKey
import utils.MockHttpClient

import scala.concurrent.Future

class SessionBehaviourISpec extends BaseISpec with MockAgentPermissionsConnector with MockHttpClient {

  val sessionCacheRepo: SessionCacheRepository = fakeApplication().injector.instanceOf[SessionCacheRepository]

  val sessionBehaviour = new SessionBehaviour {
    override val agentPermissionsConnector = new AgentPermissionsConnector(mockHttpClient)
    override val sessionCacheRepository: SessionCacheRepository = sessionCacheRepo
  }

  "withEligibleToOptIn" should {
    "execute body when status is OptedOutEligible" in  {
      mockHttpGet[HttpResponse](HttpResponse.apply(200, s""" "Opted-Out_ELIGIBLE" """))
      val result = await((sessionBehaviour.withEligibleToOptIn(Arn(validArn)){ Future successful Results.Ok("")}))

      val sessionStored = await(sessionCacheRepo.getFromSession[JourneySession](DataKey("optin")))

      sessionStored.isDefined shouldBe true
      result shouldBe Results.Ok("")
    }

    "execute body when status is OptedOutEligible, not make call to get optin status when a session is available" in  {
      await(sessionCacheRepo.putSession(DataKey("opting"),JourneySession(sessionId = "session-x", optinStatus = OptedOutEligible)))

      val result = (sessionBehaviour.withEligibleToOptIn(Arn(validArn)){ Future successful Results.Ok("")}).futureValue

      result shouldBe Results.Ok("")
    }
  }

}
