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
import helpers.BaseSpec
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
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
    new GroupServiceImpl(
      mockAgentUserClientDetailsConnector,
      sessionCacheRepo,
      mockAgentPermissionsConnector
    )

  "get groups" should {
    "Return groups from agentPermissionsConnector" in {

      //given
      val groupSummaries = Seq(
        GroupSummary("2", "Carrots", 1, 1),
        GroupSummary("3", "Potatoes", 1, 1),
      )

      (mockAgentPermissionsConnector
        .groups(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future successful groupSummaries).once()

      //when
      val summaries = await(service.groups(arn))

      //then
      summaries shouldBe groupSummaries


    }
  }

}
