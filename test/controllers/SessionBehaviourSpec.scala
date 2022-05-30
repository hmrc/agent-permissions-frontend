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
import config.{AppConfig, AppConfigImpl}
import connectors.AgentPermissionsConnectorImpl
import helpers.{AgentPermissionsConnectorMocks, BaseSpec, HttpClientMocks}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}
import play.api.mvc.Results
import play.api.test.Helpers._
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, OptedInReady, OptedInSingleUser, OptedOutEligible, OptedOutSingleUser, OptedOutWrongClientCount, OptinStatus}
import uk.gov.hmrc.http.{HttpClient, HttpResponse}
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.Future

class SessionBehaviourSpec extends BaseSpec with HttpClientMocks with AgentPermissionsConnectorMocks {

  implicit val mockHttpClient: HttpClient = mock[HttpClient]

  lazy val sessioncacheRepo: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)

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

  "isEligibleToOptIn" should {
    "execute body when status is OptedOutEligible and store status in journey session if no session exists" in  {

      mockHttpGet[HttpResponse](HttpResponse.apply(200, s""" "Opted-Out_ELIGIBLE" """))
      val result = await(testSessionBehaviour.isEligibleToOptIn(Arn(validArn)){ (_: OptinStatus) => Future successful Results.Ok("")})
      val sessionStored = await(testSessionBehaviour.sessionCacheRepository.getFromSession[OptinStatus](OPTIN_STATUS))

      sessionStored.isDefined shouldBe true
      status(result) shouldBe 200
    }

    "if a journey session is available then don't make a call to backend" in  {

      await(testSessionBehaviour.sessionCacheRepository.putSession(OPTIN_STATUS, OptedOutEligible))
      //no call to agent-permissions is required
      val result = await(testSessionBehaviour.isEligibleToOptIn(Arn(validArn)){ (_: OptinStatus) => Future successful Results.Ok("")})

      status(result) shouldBe 200
    }

    "if not eligible to opt-in then redirect to root" in {

      mockHttpGet[HttpResponse](HttpResponse.apply(200, s""" "Opted-Out_SINGLE_USER" """))
      val result = await(testSessionBehaviour.isEligibleToOptIn(Arn(validArn)){ (_: OptinStatus) => Future successful Results.Ok("")})
      val sessionStored = await(testSessionBehaviour.sessionCacheRepository.getFromSession[OptinStatus](OPTIN_STATUS))

      sessionStored.isDefined shouldBe true

      status(result) shouldBe SEE_OTHER
      redirectLocation(result.toFuture).get shouldBe routes.RootController.start.url
    }
  }

  "isOptedIn" should {
    "execute body when status is any OptedIn status and store status in journey session if no session exists" in  {

      mockHttpGet[HttpResponse](HttpResponse.apply(200, s""" "Opted-In_READY" """))
      val result = await(testSessionBehaviour.isOptedIn(Arn(validArn)){ (_: OptinStatus) => Future successful Results.Ok("")})
      val sessionStored = await(testSessionBehaviour.sessionCacheRepository.getFromSession[OptinStatus](OPTIN_STATUS))

      sessionStored.isDefined shouldBe true
      status(result) shouldBe 200
    }

    "if a journey session is available then don't make a call to backend" in  {

      await(testSessionBehaviour.sessionCacheRepository.putSession(OPTIN_STATUS, OptedInReady))
      //no call to agent-permissions to get optInStatus is required
      val result = await(testSessionBehaviour.isOptedIn(Arn(validArn)){ (_: OptinStatus) => Future successful Results.Ok("")})

      status(result) shouldBe 200
    }

    "if not eligible to opt-out then redirect to root" in {

      mockHttpGet[HttpResponse](HttpResponse.apply(OK, s""" "Opted-Out_SINGLE_USER" """))

      val result = await(testSessionBehaviour.isOptedIn(Arn(validArn)){ (_: OptinStatus) => Future successful Results.Ok("")})

      val sessionStored = await(testSessionBehaviour.sessionCacheRepository.getFromSession[OptinStatus](OPTIN_STATUS))

      sessionStored.isDefined shouldBe true

      status(result) shouldBe SEE_OTHER
      redirectLocation(result.toFuture).get shouldBe routes.RootController.start.url
    }
  }

  "isOptedInComplete" should {
    "execute body when there is a session and the status is Opted-In_READY" in {
      await(testSessionBehaviour.sessionCacheRepository.putSession(OPTIN_STATUS, OptedInReady))
      val result = await(testSessionBehaviour.isOptedInComplete(Arn(validArn))((_: OptinStatus) => Future successful Results.Ok("")))
      status(result) shouldBe OK
    }
    "redirect to root when there is a session and the status is not Opted-In_READY" in {
      await(testSessionBehaviour.sessionCacheRepository.putSession(OPTIN_STATUS, OptedInSingleUser))
      val result = testSessionBehaviour.isOptedInComplete(Arn(validArn))((_: OptinStatus) => Future successful Results.Ok(""))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.RootController.start.url
    }
    "initialise session and execute body when there is no session and the status from the backend is Opted-In_READY" in {
      mockHttpGet[HttpResponse](HttpResponse.apply(OK, s""" "Opted-In_READY" """))
      val result = testSessionBehaviour.isOptedInComplete(Arn(validArn))((_: OptinStatus) => Future successful Results.Ok(""))
      status(result) shouldBe OK
    }

  }

  "isOptedOut" should {
    "execute body when there is a session and the status is Opted-Out_ELIGIBLE" in {
      await(testSessionBehaviour.sessionCacheRepository.putSession(OPTIN_STATUS, OptedOutEligible))
      val result = await(testSessionBehaviour.isOptedOut(Arn(validArn))((_: OptinStatus) => Future successful Results.Ok("")))
      status(result) shouldBe OK
    }
    "execute body when there is a session and the status is Opted-Out_SINGLE_USER" in {
      await(testSessionBehaviour.sessionCacheRepository.putSession(OPTIN_STATUS, OptedOutSingleUser))
      val result = await(testSessionBehaviour.isOptedOut(Arn(validArn))((_: OptinStatus) => Future successful Results.Ok("")))
      status(result) shouldBe OK
    }
    "execute body when there is a session and the status is Opted-Out_WRONG_CLIENT_COUNT" in {
      await(testSessionBehaviour.sessionCacheRepository.putSession(OPTIN_STATUS, OptedOutWrongClientCount))
      val result = await(testSessionBehaviour.isOptedOut(Arn(validArn))((_: OptinStatus) => Future successful Results.Ok("")))
      status(result) shouldBe OK
    }
    "redirect to root when there is a session and the status is Opted-In_READY" in {
      await(testSessionBehaviour.sessionCacheRepository.putSession(OPTIN_STATUS, OptedInReady))
      val result = testSessionBehaviour.isOptedOut(Arn(validArn))((_: OptinStatus) => Future successful Results.Ok(""))
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.RootController.start.url
    }
    "initialise session and execute body when there is no session and the status from the backend is Opted-Out_ELIGIBLE" in {
      mockHttpGet[HttpResponse](HttpResponse.apply(OK, s""" "Opted-Out_ELIGIBLE" """))
      val result = testSessionBehaviour.isOptedOut(Arn(validArn))((_: OptinStatus) => Future successful Results.Ok(""))
      status(result) shouldBe OK
    }
  }
}
