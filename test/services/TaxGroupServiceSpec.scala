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

import akka.Done
import connectors._
import helpers.BaseSpec
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Arn, TaxGroup}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime.MIN
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class TaxGroupServiceSpec extends BaseSpec {

  implicit val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]

  val service = new TaxGroupServiceImpl(mockAgentPermissionsConnector)

  "getTaxGroupClientCount" should {
    "delegate to AP connector" in {
      //expect
      expectGetTaxGroupClientCountFromConnector(arn)

      //when
      await(service.getTaxGroupClientCount(arn))

    }
  }

  "create group" should {
    "call createTaxGroup on agentPermissionsConnector" in {

      //given
      val payload = CreateTaxServiceGroupRequest("blah", None, "blah")
      (mockAgentPermissionsConnector
        .createTaxServiceGroup(_: Arn)(_: CreateTaxServiceGroupRequest)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *, *)
        .returning(Future successful "123456")
        .once()

      //when
      val response = await(service.createGroup(arn, payload))

      //then
      response shouldBe "123456"


    }
  }

  "get group" should {
    "call get on agentPermissionsConnector" in {

      //given
      val groupId = UUID.randomUUID().toString
      val agentUser = AgentUser("agent1", "Bob Smith")
      val expectedGroup = TaxGroup(Arn("arn1"), "Bangers & Mash", MIN, MIN, agentUser, agentUser, None, "", automaticUpdates = true, None)

      (mockAgentPermissionsConnector
        .getTaxServiceGroup(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(groupId, *, *)
        .returning(Future successful Some(expectedGroup))
        .once()

      //when
      val response = await(service.getGroup(groupId))

      //then
      response shouldBe Some(expectedGroup)


    }
  }

  "delete group" should {
    "call delete on agentPermissionsConnector" in {

      //given
      val groupId = UUID.randomUUID().toString

      (mockAgentPermissionsConnector
        .deleteTaxGroup(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(groupId, *, *)
        .returning(Future successful Done)
        .once()

      //when
      val response = await(service.deleteGroup(groupId))

      //then
      response shouldBe Done

    }
  }

  "update group" should {
    "call patch/update on agentPermissionsConnector" in {

      //given
      val groupId = UUID.randomUUID().toString
      val payload = UpdateTaxServiceGroupRequest(groupName = Some("Bangers & Mash"))

      (mockAgentPermissionsConnector
        .updateTaxGroup(_: String, _: UpdateTaxServiceGroupRequest)(_: HeaderCarrier, _: ExecutionContext))
        .expects(groupId, payload, *, *)
        .returning(Future successful Done)
        .once()

      //when
      val response = await(service.updateGroup(groupId, payload))

      //then
      response shouldBe Done


    }
  }

  "add team member to a group" should {
    "call patch/update on agentPermissionsConnector" in {

      //given
      val groupId = UUID.randomUUID().toString
      val agent = AgentUser("agentId", "Bob Builder")
      val payload = AddOneTeamMemberToGroupRequest(agent)

      (mockAgentPermissionsConnector
        .addOneTeamMemberToTaxGroup(_: String, _: AddOneTeamMemberToGroupRequest)(_: HeaderCarrier, _: ExecutionContext))
        .expects(groupId, payload, *, *)
        .returning(Future successful Done)
        .once()

      //when
      val response = service.addOneMemberToGroup(groupId, payload).futureValue

      //then
      response shouldBe Done


    }
  }

}
