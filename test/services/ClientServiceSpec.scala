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
import controllers.{CLIENT_FILTER_INPUT, CLIENT_SEARCH_INPUT, CONTINUE_BUTTON, FILTERED_CLIENTS, FILTER_BUTTON, SELECTED_CLIENTS, clientFilteringKeys}
import helpers.BaseSpec
import models.{AddClientsToGroup, DisplayClient}
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

  "saveSelectedOrFilteredClients" should {

    "work for CONTINUE_BUTTON" in {

      val CLIENTS = displayClients.take(8)
      //expect
      expectGetClients(arn)(fakeClients.take(8))
      expectGetSessionItem(SELECTED_CLIENTS, CLIENTS.take(2), 2)
      expectGetSessionItem(FILTERED_CLIENTS, CLIENTS.takeRight(1))
      //TODO: this looks like a bug too. Bug is in the GroupMemberOps
      val expectedPutSelected = List(
        DisplayClient("123456781","friendly name 1","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456782","friendly name 2","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456783","friendly name 3","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456784","friendly name 4","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456785","friendly name 5","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456786","friendly name 6","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456787","friendly name 7","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456788","friendly name 8","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456781","friendly name 1","HMRC-MTD-VAT","VRN",false),
        DisplayClient("123456782","friendly name 2","HMRC-MTD-VAT","VRN",false)
      )
      expectDeleteSessionItems(clientFilteringKeys)
      expectPutSessionItem(SELECTED_CLIENTS, expectedPutSelected)

      val formData = AddClientsToGroup(None, None, Some(CLIENTS.map(_.id).toList), CONTINUE_BUTTON)

      //when
      await(service.saveSelectedOrFilteredClients(arn)(formData)(service.getAllClients))

    }

    "work for FILTER_BUTTON" in {

      val CLIENTS = displayClients.take(8)
      //expect
      expectGetClients(arn)(fakeClients.take(8))
      expectGetSessionItem(SELECTED_CLIENTS, CLIENTS.take(2), 2)
      expectGetSessionItem(FILTERED_CLIENTS, CLIENTS.takeRight(1))
      val expectedPutSelected = List(
        DisplayClient("123456781","friendly name 1","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456782","friendly name 2","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456783","friendly name 3","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456784","friendly name 4","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456785","friendly name 5","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456786","friendly name 6","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456787","friendly name 7","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456788","friendly name 8","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456781","friendly name 1","HMRC-MTD-VAT","VRN",false),
        DisplayClient("123456782","friendly name 2","HMRC-MTD-VAT","VRN",false)
      )
      expectPutSessionItem(SELECTED_CLIENTS, expectedPutSelected)

      val formData = AddClientsToGroup(
        Some("searchTerm"),
        Some("filterTerm"),
        Some(CLIENTS.map(_.id).toList),
        FILTER_BUTTON
      )
      expectPutSessionItem(CLIENT_SEARCH_INPUT, "searchTerm")
      expectPutSessionItem(CLIENT_FILTER_INPUT, "filterTerm")
//      expectDeleteSessionItems(clientFilteringKeys)

      //when
      await(service.saveSelectedOrFilteredClients(arn)(formData)(service.getAllClients))

    }


  }

  "addSelectablesToSession" should {
    
    "work as expected with none set as selected " in{
      //given existing session state
      expectGetSessionItem(SELECTED_CLIENTS, displayClients.take(2))
      expectGetSessionItem(FILTERED_CLIENTS, displayClients.takeRight(1))

      //we expect the sesion to be changed like this
      expectPutSessionItem(SELECTED_CLIENTS, displayClients.take(3))

      //when
      await(service.addSelectablesToSession(displayClients.take(3).toList)(SELECTED_CLIENTS, FILTERED_CLIENTS))
    }
    
    "work as expected with selected in session " in{
      //TODO: don't think this is the desired behaviour
      //given existing session state
      expectGetSessionItem(SELECTED_CLIENTS, displayClients.take(2).map(_.copy(selected = true)))
      expectGetSessionItem(FILTERED_CLIENTS, displayClients.takeRight(1).map(_.copy(selected = true)))

      val expectedPayload = List(
        DisplayClient("123456781","friendly name 1","HMRC-MTD-VAT","VRN",false),
        DisplayClient("123456782","friendly name 2","HMRC-MTD-VAT","VRN",false),
        DisplayClient("123456783","friendly name 3","HMRC-MTD-VAT","VRN",false),
        DisplayClient("123456781","friendly name 1","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456782","friendly name 2","HMRC-MTD-VAT","VRN",true)
      )
      //we expect the sesion to be changed like this
      expectPutSessionItem(SELECTED_CLIENTS, expectedPayload)

      //when
      await(service.addSelectablesToSession(displayClients.take(3).toList)(SELECTED_CLIENTS, FILTERED_CLIENTS))
    }
  }
}
