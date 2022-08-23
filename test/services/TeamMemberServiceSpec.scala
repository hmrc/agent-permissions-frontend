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

import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector}
import helpers.BaseSpec
import models.TeamMember
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.UserDetails

class TeamMemberServiceSpec extends BaseSpec {

  implicit val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector =
    mock[AgentUserClientDetailsConnector]
  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

  val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]

  val service = new TeamMemberServiceImpl(mockAgentPermissionsConnector,mockAgentUserClientDetailsConnector,sessionCacheRepo)

  val users: Seq[UserDetails] = (1 to 3)
    .map(
      i =>
        UserDetails(userId = Option(s"user$i"),
          None,
          Some(s"Name $i"),
          Some(s"bob$i@accounting.com")))

  "getTeamMembers" should {
    "Get TeamMembers from agentUserClientDetailsConnector and merge selected ones" in {
      //given
      stubGetTeamMembersOk(arn)(users)

      //when
      val maybeTeamMembers: Option[Seq[TeamMember]] =
        await(service.getTeamMembers(arn))
      val teamMembers: Seq[TeamMember] = maybeTeamMembers.get

      //then
      teamMembers.size shouldBe 3
      teamMembers.head.name shouldBe "Name 1"
      teamMembers.head.userId shouldBe Some("user1")
      teamMembers.head.email shouldBe "bob1@accounting.com"
      teamMembers.head.selected shouldBe false

    }
  }

  "filterTeamMembers" should {}

}
