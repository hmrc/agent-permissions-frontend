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
import connectors.AgentPermissionsConnectorImpl
import helpers.{AgentPermissionsConnectorMocks, BaseISpec, HttpClientMocks}
import models.JourneySession
import play.api.Application
import play.api.mvc.Results
import play.api.test.Helpers._
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, OptedInReady, OptedOutEligible}
import uk.gov.hmrc.http.{HttpClient, HttpResponse}
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.Future

class SessionBehaviourSpec extends BaseISpec with HttpClientMocks with AgentPermissionsConnectorMocks {

  implicit val mockHttpClient: HttpClient = mock[HttpClient]

  override def moduleWithOverrides = new AbstractModule() {
    override def configure(): Unit = {
      bind(classOf[HttpClient]).toInstance(mockHttpClient)
      bind(classOf[SessionCacheRepository]).toInstance(sessioncacheRepo)
    }
  }

  override implicit lazy val fakeApplication: Application = appBuilder
    .configure("mongodb.uri" -> mongoUri)
    .build()


    val testSessionBehaviour: SessionBehaviour =
    new SessionBehaviour {
      override val agentPermissionsConnector = app.injector.instanceOf[AgentPermissionsConnectorImpl]
      override val sessionCacheRepository: SessionCacheRepository = app.injector.instanceOf[SessionCacheRepository]
  }

  "withEligibleToOptIn" should {
    "execute body when status is OptedOutEligible and store status in journey session if no session exists" in  {

      mockHttpGet[HttpResponse](HttpResponse.apply(200, s""" "Opted-Out_ELIGIBLE" """))
      val result = await(testSessionBehaviour.withEligibleToOptIn(Arn(validArn)){ Future successful Results.Ok("")})
      val sessionStored = await(testSessionBehaviour.sessionCacheRepository.getFromSession[JourneySession](DataKey("opting")))

      sessionStored.isDefined shouldBe true
      status(result) shouldBe 200
    }

    "if a journey session is available then don't make a call to backend" in  {

      await(testSessionBehaviour.sessionCacheRepository.putSession(DataKey("opting"),JourneySession(optinStatus = OptedOutEligible)))
      //no call required
      val result = await(testSessionBehaviour.withEligibleToOptIn(Arn(validArn)){ Future successful Results.Ok("")})

      status(result) shouldBe 200
    }

    "if not eligible to opt-in then return 403" in {

      mockHttpGet[HttpResponse](HttpResponse.apply(200, s""" "Opted-Out_SINGLE_USER" """))
      val result = await(testSessionBehaviour.withEligibleToOptIn(Arn(validArn)){ Future successful Results.Ok("")})
      val sessionStored = await(testSessionBehaviour.sessionCacheRepository.getFromSession[JourneySession](DataKey("opting")))

      sessionStored.isDefined shouldBe true

      status(result) shouldBe 403
      bodyOf(result) shouldBe "not_eligible_to_opt-in"
    }
  }

  "withEligibleToOptOut" should {
    "execute body when status is any OptedIn status and store status in journey session if no session exists" in  {

      mockHttpGet[HttpResponse](HttpResponse.apply(200, s""" "Opted-In_READY" """))
      val result = await(testSessionBehaviour.withEligibleToOptOut(Arn(validArn)){ Future successful Results.Ok("")})
      val sessionStored = await(testSessionBehaviour.sessionCacheRepository.getFromSession[JourneySession](DataKey("opting")))

      sessionStored.isDefined shouldBe true
      status(result) shouldBe 200
    }

    "if a journey session is available then don't make a call to backend" in  {

      await(testSessionBehaviour.sessionCacheRepository.putSession(DataKey("opting"),JourneySession(optinStatus = OptedInReady)))
      //no call required
      val result = await(testSessionBehaviour.withEligibleToOptOut(Arn(validArn)){ Future successful Results.Ok("")})

      status(result) shouldBe 200
    }

    "if not eligible to opt-out then return 403" in {

      mockHttpGet[HttpResponse](HttpResponse.apply(200, s""" "Opted-Out_SINGLE_USER" """))
      val result = await(testSessionBehaviour.withEligibleToOptOut(Arn(validArn)){ Future successful Results.Ok("")})
      val sessionStored = await(testSessionBehaviour.sessionCacheRepository.getFromSession[JourneySession](DataKey("opting")))

      sessionStored.isDefined shouldBe true

      status(result) shouldBe 403
      bodyOf(result) shouldBe "not_eligible_to_opt-out"
    }
  }

  lazy val sessioncacheRepo: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)

}
