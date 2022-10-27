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

import akka.Done
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.{FILTERED_CLIENTS, SELECTED_CLIENTS}
import helpers.BaseSpec
import models.DisplayClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class ClientServiceSpec extends BaseSpec {

  implicit val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit lazy val sessionCacheService: SessionCacheService = mock[SessionCacheService]
  val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  val service = new ClientServiceImpl(mockAgentUserClientDetailsConnector, mockAgentPermissionsConnector,sessionCacheService)

  val fakeClients: Seq[Client] = (1 to 10)
    .map(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly name $i"))

  val displayClients: Seq[DisplayClient] =
    fakeClients.map(DisplayClient.fromClient(_))

  "updateClientReference" should {
    "PUT client to agentUserClientDetailsConnector" in {
      //given
      val newName = "The new name"
      expectUpdateClientReferenceSuccess()

      //when
      val updateRef = await(service.updateClientReference(arn, displayClients.head, newName))

      //then
      updateRef shouldBe Done
    }
  }

  "getFilteredClientsElseAll" should {

    "Get filtered clients from session when available " in {
      //given
      expectGetSessionItem(FILTERED_CLIENTS, displayClients)

      //when
      val clients =  await(service.getFilteredClientsElseAll(arn))

      //then
      clients.size shouldBe 10
      clients.head.name shouldBe "friendly name 1"
      clients.head.identifierKey shouldBe "VRN"
      clients.head.hmrcRef shouldBe "123456781"
      clients.head.taxService shouldBe "HMRC-MTD-VAT"

      clients(5).name shouldBe "friendly name 6"
      clients(5).identifierKey shouldBe "VRN"
      clients(5).hmrcRef shouldBe "123456786"
      clients(5).taxService shouldBe "HMRC-MTD-VAT"

      clients(9).name shouldBe "friendly name 10"
      clients(9).hmrcRef shouldBe "1234567810"
      clients(9).identifierKey shouldBe "VRN"
      clients(9).taxService shouldBe "HMRC-MTD-VAT"

    }

    "Get clients from session else agentUserClientDetailsConnector" in {
      //given
      expectGetSessionItemNone(FILTERED_CLIENTS) // <-- no filtered clients in session
      expectGetClients(arn)(fakeClients)
      expectGetSessionItem(SELECTED_CLIENTS, displayClients.take(2))

      //when
      val clients =  await(service.getFilteredClientsElseAll(arn))

      //then
      clients.size shouldBe 10
      clients.head.name shouldBe "friendly name 1"
      clients.head.identifierKey shouldBe "VRN"
      clients.head.hmrcRef shouldBe "123456781"
      clients.head.taxService shouldBe "HMRC-MTD-VAT"

      clients(5).name shouldBe "friendly name 5"
      clients(5).identifierKey shouldBe "VRN"
      clients(5).hmrcRef shouldBe "123456785"
      clients(5).taxService shouldBe "HMRC-MTD-VAT"

      clients(9).name shouldBe "friendly name 9"
      clients(9).hmrcRef shouldBe "123456789"
      clients(9).identifierKey shouldBe "VRN"
      clients(9).taxService shouldBe "HMRC-MTD-VAT"

    }
  }

  "getUnassignedClients" should {
    "Gets them from mockAgentPermissionsConnector" in {
      //given
      (mockAgentPermissionsConnector.unassignedClients(_: Arn)( _: HeaderCarrier, _: ExecutionContext))
        .expects(arn, *, *)
        .returning(Future.successful(displayClients)).once()

      //when
      val unassignedClients: Seq[DisplayClient] = await(service.getUnassignedClients(arn))


      //then
      unassignedClients shouldBe displayClients
    }
  }

  "lookup clients" should {

    "gets clients by id" in {
      //given
      expectGetClients(arn)(fakeClients)

      //when
      val unassignedClients = await(service.lookupClients(arn)(Some(displayClients.take(2).map(_.id).toList)))

      //then
      unassignedClients shouldBe displayClients.take(2)
    }

    "return empty list when no ids provided" in {
      //when
      val unassignedClients = await(service.lookupClients(arn)(None))

      //then
      unassignedClients shouldBe Seq.empty[DisplayClient]
    }
  }

  "lookup client" should {
    "return None when nothing returned from agentUserClientConnector" in {
      //given no clients returned from agentUserClientConnector
      expectGetClients(arn)(Seq.empty)

      //when
      val client = await(service.lookupClient(arn)("62f21dd4af97f775cde0b421"))

      //then
      client shouldBe None
    }

    "return None when no match for id from clients returned from agentUserClientConnector" in {
      //given no clients returned from agentUserClientConnector
      expectGetClients(arn)(fakeClients.take(5))

      //when
      val client = await(service.lookupClient(arn)("non-matching-id"))

      //then
      client shouldBe None
    }

    "get all clients for arn then filter for required one" in {
      //given no clients returned from agentUserClientConnector
      expectGetClients(arn)(fakeClients.take(5))

      //when
      val client = await(service.lookupClient(arn)(displayClients.head.id))

      //then
      client.get shouldBe displayClients.head
    }
  }
}
