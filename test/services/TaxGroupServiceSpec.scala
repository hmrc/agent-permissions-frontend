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

import connectors._
import helpers.BaseSpec
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

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
        .createTaxServiceGroup(_: Arn)( _: CreateTaxServiceGroupRequest)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *, *)
        .returning(Future successful "123456")
        .once()

      //when
      val response = await(service.createGroup(arn, payload))

      //then
      response shouldBe "123456"


    }
  }



}
