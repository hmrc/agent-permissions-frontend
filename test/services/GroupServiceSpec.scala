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

import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector, GroupSummary}
import controllers.{FILTERED_GROUPS_INPUT, FILTERED_GROUP_SUMMARIES}
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

  val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]

  val service =
    new GroupServiceImpl(mockAgentUserClientDetailsConnector, sessionCacheRepo, mockAgentPermissionsConnector)

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

  "filterGroupsByName" should {
    "Filter out Groups from  agentPermissionsConnector and select those matching filter" in {

      //given
      val expectedFilteredGroup = GroupSummary("1", "Potatoes", 1, 1)
      val groupSummaries = Seq(
        expectedFilteredGroup,
        GroupSummary("2", "Carrots", 1, 1),
        GroupSummary("3", "cars", 1, 1),
        GroupSummary("4", "chickens", 1, 1),
      )
      (mockAgentPermissionsConnector
        .groupsSummaries(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future successful Some(groupSummaries, Seq.empty[DisplayClient]))

      //when
      await(service.filterByGroupName("pot")(arn))

      //then
      val maybeSummaries = await(sessionCacheRepo.getFromSession(FILTERED_GROUP_SUMMARIES))
      val maybeInput = await(sessionCacheRepo.getFromSession(FILTERED_GROUPS_INPUT))
      maybeSummaries.isDefined shouldBe true
      maybeSummaries.get.size shouldBe 1
      maybeSummaries.get.head shouldBe expectedFilteredGroup
      maybeInput.get shouldBe "pot"
    }
  }

}
