/*
 * Copyright 2023 HM Revenue & Customs
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
import controllers.{CLIENT_FILTER_INPUT, CLIENT_SEARCH_INPUT, SELECTED_CLIENTS}
import helpers.BaseSpec
import models.DisplayClient
import play.api.Application
import repository.SessionCacheRepository

class SessionCacheServiceSpec extends BaseSpec {

  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

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

  // TODO test get, put

  "delete" should {
    "delete given key from session cache repo" in {
      (for {
        _ <- sessionCacheRepo.putSession(SELECTED_CLIENTS, Seq(DisplayClient("whatever", "", "", "")))
        _ <- sessionCacheRepo.putSession(CLIENT_SEARCH_INPUT, "searchTerm")
        _ <- service.delete(CLIENT_SEARCH_INPUT)
      } yield ()).futureValue

      sessionCacheRepo.getFromSession(SELECTED_CLIENTS).futureValue shouldBe defined
      sessionCacheRepo.getFromSession(CLIENT_SEARCH_INPUT).futureValue shouldBe empty
    }
  }

  "deleteAll" should {
    "delete all given keys from session cache repo" in {
      (for {
        _ <- sessionCacheRepo.putSession(SELECTED_CLIENTS, Seq(DisplayClient("whatever", "", "", "")))
        _ <- sessionCacheRepo.putSession(CLIENT_SEARCH_INPUT, "searchTerm")
        _ <- sessionCacheRepo.putSession(CLIENT_FILTER_INPUT, "filterTerm")
        _ <- service.deleteAll(Seq(CLIENT_SEARCH_INPUT, CLIENT_FILTER_INPUT))
      } yield ()).futureValue

      sessionCacheRepo.getFromSession(SELECTED_CLIENTS).futureValue shouldBe defined
      sessionCacheRepo.getFromSession(CLIENT_SEARCH_INPUT).futureValue shouldBe empty
      sessionCacheRepo.getFromSession(CLIENT_FILTER_INPUT).futureValue shouldBe empty
    }
  }

}
