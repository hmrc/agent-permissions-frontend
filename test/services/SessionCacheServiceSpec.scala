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

package services

import com.google.inject.AbstractModule
import controllers.{GROUP_NAME, GROUP_NAME_CONFIRMED, OPTIN_STATUS, routes}
import helpers.BaseSpec
import models.{Client, Group}
import play.api.Application
import play.api.http.Status.SEE_OTHER
import play.api.test.Helpers.{await, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.OptedInNotReady

class SessionCacheServiceSpec extends BaseSpec {

  lazy val sessionCacheRepo: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)


  override def moduleWithOverrides = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepo)
    }
  }


  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val service = fakeApplication.injector.instanceOf[SessionCacheService]

  "writeGroupNameAndRedirect" should {
    "over-write an existing group name and redirect" in {

      await(sessionCacheRepo.putSession[String](GROUP_NAME, "shady"))
      val result = service.writeGroupNameAndRedirect("new group name")(routes.OptInController.start)

      status(result) shouldBe SEE_OTHER

      val savedSession = await(sessionCacheRepo.getFromSession[String](GROUP_NAME))

      savedSession.get shouldBe "new group name"

      redirectLocation(result).get shouldBe routes.OptInController.start.url
    }

    "create new group if none exists and redirect" in {

      val result = service.writeGroupNameAndRedirect("new group name")(routes.OptInController.start)

      status(result) shouldBe SEE_OTHER

      val savedSession = await(sessionCacheRepo.getFromSession[String](GROUP_NAME))

      savedSession.get shouldBe "new group name"

      redirectLocation(result).get shouldBe routes.OptInController.start.url
    }
  }

  "confirmGroupNameAndRedirect" should {
    "update field in session if a group name exists" in {

      await(sessionCacheRepo.putSession[String](GROUP_NAME, "shady"))
      await(sessionCacheRepo.putSession[Boolean](GROUP_NAME_CONFIRMED, false))

      val result = service.confirmGroupNameAndRedirect(routes.OptInController.start)

      val savedSession = await(sessionCacheRepo.getFromSession[Boolean](GROUP_NAME_CONFIRMED))

      status(result) shouldBe SEE_OTHER
      savedSession.get shouldBe true
      redirectLocation(result).get shouldBe routes.OptInController.start.url
    }
  }


}
