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
import controllers._
import helpers.BaseSpec
import models.{DisplayClient, TeamMember}
import play.api.Application
import play.api.test.Helpers.{await, defaultAwaitTimeout}
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


  "saveSelectedClients" should {

    "Add selected clients to SessionCache Repo" in {
      //given
      val clients = Seq(DisplayClient("whatever", "", "", ""))

      val (initialEmptySession, savedClients) = (for {
        emptySession <- sessionCacheRepo.getFromSession(SELECTED_CLIENTS)
        _ <- service.saveSelectedClients(clients)
        savedClients <- sessionCacheRepo.getFromSession(SELECTED_CLIENTS)
      } yield (emptySession, savedClients)).futureValue

      //then
      initialEmptySession.isDefined shouldBe false
      savedClients.isDefined shouldBe true
      savedClients.get.head.hmrcRef shouldBe "whatever"
    }
  }

  "saveSelectedTeamMembers" should {
    "puts Selected Team members in SessionCache Repo" in {
      //given
      val teamMembers: Seq[TeamMember] =
        (1 to 3).map(i => TeamMember(s"name$i", s"x$i@xyz.com", Some(s"user$i")))

      //when
      await(service.saveSelectedTeamMembers(teamMembers))

      //then
      val maybeMembers = await(
        sessionCacheRepo.getFromSession[Seq[TeamMember]](
          SELECTED_TEAM_MEMBERS))

      maybeMembers.isDefined shouldBe true
      maybeMembers.get(0) shouldBe TeamMember("name1",
                                              "x1@xyz.com",
        Some("user1"),
                                              None,
                                              true)
      maybeMembers.get(1) shouldBe TeamMember("name2",
                                              "x2@xyz.com",
        Some("user2"),
                                              None,
                                              true)
      maybeMembers.get(2) shouldBe TeamMember("name3",
                                              "x3@xyz.com",
        Some("user3"),
                                              None,
                                              true)
    }
  }

  "clearSelectedTeamMembers" should {
    "Remove selected Team members from SessionCache Repo" in {
      //given
      val teamMembers: Seq[TeamMember] =
        (1 to 3).map(i => TeamMember(s"name$i", s"x$i@xyz.com", Some(s"user$i")))

      val (membersInSessionCache, clearedSessionCacheValue) = (for {
        _ <- sessionCacheRepo.putSession(SELECTED_TEAM_MEMBERS,
                                         teamMembers)
        membersInSessionCache <- sessionCacheRepo.getFromSession(
          SELECTED_TEAM_MEMBERS)
        _ <- service.clearSelectedTeamMembers()
        clearedSessionCache <- sessionCacheRepo.getFromSession(
          SELECTED_TEAM_MEMBERS)
      } yield (membersInSessionCache, clearedSessionCache)).futureValue

      membersInSessionCache.get.length shouldBe 3
      clearedSessionCacheValue.isDefined shouldBe false
    }
  }

}
