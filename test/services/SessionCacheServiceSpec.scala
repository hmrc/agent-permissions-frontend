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
import controllers.{DATA_KEY, routes}
import helpers.BaseSpec
import models.{Client, Group, JourneySession}
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
    "over-write group name and redirect" in {
      val session = JourneySession(optInStatus = OptedInNotReady, group = Some(Group(name = "my group name", clients = Some(Set(Client("123", "mr...", "vat"))))))

      val result = service.writeGroupNameAndRedirect("new group name")(routes.OptInController.start)(session)

      status(result) shouldBe SEE_OTHER

      val savedSession = await(sessionCacheRepo.getFromSession(DATA_KEY))

      savedSession.get.group.get.name shouldBe "new group name"
      savedSession.get.group.get.clients shouldBe Some(Set(Client("123", "mr...", "vat")))
      redirectLocation(result).get shouldBe routes.OptInController.start.url
    }
    "create new group if none exists and redirect" in {
      val session = JourneySession(optInStatus = OptedInNotReady)

      val result = service.writeGroupNameAndRedirect("new group name")(routes.OptInController.start)(session)

      status(result) shouldBe SEE_OTHER

      val savedSession = await(sessionCacheRepo.getFromSession(DATA_KEY))

      savedSession.get.group.get.name shouldBe "new group name"
      redirectLocation(result).get shouldBe routes.OptInController.start.url
    }
  }

  "confirmGroupNameAndRedirect" should {
    "update field in session if a group exists" in {
      val session = JourneySession(optInStatus = OptedInNotReady, group = Some(Group(name = "my group name", clients = Some(Set(Client("123", "mr...", "vat"))))))

      val result = service.confirmGroupNameAndRedirect(routes.OptInController.start)(session)

      val savedSession = await(sessionCacheRepo.getFromSession(DATA_KEY))

      status(result) shouldBe SEE_OTHER
      savedSession.get.group.get.nameConfirmed shouldBe true
      redirectLocation(result).get shouldBe routes.OptInController.start.url
    }

    "redirect to routes.GroupController.showCreateGroup when no group exists" in {
      val session = JourneySession(optInStatus = OptedInNotReady)

      val result = service.confirmGroupNameAndRedirect(routes.OptInController.start)(session)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.GroupController.showCreateGroup.url
    }
  }


}
