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

import connectors.AgentUserClientDetailsConnector
import helpers.BaseSpec
import models.{DisplayClient, TeamMember}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client, UserDetails}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class GroupServiceSpec extends BaseSpec {

  val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector =
    mock[AgentUserClientDetailsConnector]
  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

  val service =
    new GroupService(mockAgentUserClientDetailsConnector, sessionCacheRepo)

  val fakeClients: Seq[Client] = (1 to 10)
    .map(i => Client(s"tax$i~enrolmentKey$i~hmrcRef$i", s"friendlyName$i"))

  val users: Seq[UserDetails] = (1 to 3)
    .map(
      i =>
        UserDetails(userId = Option(s"user$i"),
                    None,
                    Some(s"Name $i"),
                    Some(s"bob$i@accounting.com")))

  val fakeTeamMembers: Seq[TeamMember] = (1 to 5)
    .map(i => {
      TeamMember(
        s"John $i",
        "User",
        Some("John"),
        Some(s"john$i@abc.com"),
      )
    })

  "getClients" should {
    "Get clients from agentUserClientDetailsConnector and merge selected ones" in {
      //given
      (mockAgentUserClientDetailsConnector
        .getClients(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future successful Some(fakeClients))

      //when
      val maybeClients: Option[Seq[DisplayClient]] =
        await(service.getClients(arn))
      val clients: Seq[DisplayClient] = maybeClients.get

      //then
      clients.size shouldBe 10
      clients(0).name shouldBe "friendlyName1"
      clients(0).identifierKey shouldBe "enrolmentKey1"
      clients(0).hmrcRef shouldBe "hmrcRef1"
      clients(0).taxService shouldBe "tax1"

      clients(5).name shouldBe "friendlyName5"
      clients(5).identifierKey shouldBe "enrolmentKey5"
      clients(5).hmrcRef shouldBe "hmrcRef5"
      clients(5).taxService shouldBe "tax5"

      clients(9).name shouldBe "friendlyName9"
      clients(9).hmrcRef shouldBe "hmrcRef9"
      clients(9).identifierKey shouldBe "enrolmentKey9"
      clients(9).taxService shouldBe "tax9"

    }
  }

  "getTeamMembers" should {
    "Get TeamMembers from agentUserClientDetailsConnector and merge selected ones" in {
      //given
      (mockAgentUserClientDetailsConnector
        .getTeamMembers(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future successful Some(users))

      //when
      val maybeTeamMembers: Option[Seq[TeamMember]] =
        await(service.getTeamMembers(arn)(None))
      val teamMembers: Seq[TeamMember] = maybeTeamMembers.get

      //then
      teamMembers.size shouldBe 3
      teamMembers(0).name shouldBe "Name 1"
      teamMembers(0).userId shouldBe Some("user1")
      teamMembers(0).email shouldBe "bob1@accounting.com"
      teamMembers(0).selected shouldBe false

    }
  }

  // TODO implement tests for addClient/addTeamMember, refactor processFormData?
  "filterClients" should {}

  "filterTeamMembers" should {}

}
