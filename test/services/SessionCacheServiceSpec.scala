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
import controllers._
import helpers.BaseSpec
import models.TeamMember
import play.api.Application
import play.api.http.Status.SEE_OTHER
import play.api.test.Helpers.{await, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository

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


  "saveSelectedTeamMembers" should {
    "puts Selected Team members in SessionCache Repo" in {
      //given
      val teamMembers: Seq[TeamMember] = (1 to 3).map(i => TeamMember(s"name$i", s"x$i@xyz.com"))

      //when
      await(service.saveSelectedTeamMembers(teamMembers))

      //then
      val maybeMembers = await(sessionCacheRepo.getFromSession[Seq[TeamMember]](GROUP_TEAM_MEMBERS_SELECTED))

      maybeMembers.isDefined shouldBe true
      maybeMembers.get(0) shouldBe TeamMember("name1", "x1@xyz.com", None, None, true)
      maybeMembers.get(1) shouldBe TeamMember("name2", "x2@xyz.com", None, None, true)
      maybeMembers.get(2) shouldBe TeamMember("name3", "x3@xyz.com", None, None, true)
    }
  }


}
