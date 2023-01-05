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
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.{CLIENT_FILTER_INPUT, CLIENT_SEARCH_INPUT, CONTINUE_BUTTON, CURRENT_PAGE_CLIENTS, FILTERED_CLIENTS, FILTER_BUTTON, SELECTED_CLIENTS, clientFilteringKeys}
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
      //given
      val CLIENTS = displayClients.take(8)

      val formData = AddClientsToGroup(
        Some("searchTerm"),
        Some("filterTerm"),
        Some(CLIENTS.map(_.id).toList),
        FILTER_BUTTON
      )

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

      //expect
      expectGetClients(arn)(fakeClients.take(8))
      expectGetSessionItem(SELECTED_CLIENTS, CLIENTS.take(2), 3)
      expectGetSessionItem(FILTERED_CLIENTS, CLIENTS.takeRight(1))
      expectPutSessionItem(SELECTED_CLIENTS, expectedPutSelected)
      expectPutSessionItem(CLIENT_SEARCH_INPUT, "searchTerm")
      expectPutSessionItem(CLIENT_FILTER_INPUT, "filterTerm")
      expectPutSessionItem(FILTERED_CLIENTS, Nil)

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

  "filteredClients save filtered clients to session and return filtered clients" should {
    "WHEN the search term doesn't match any" in {
      //given
      val formData = AddClientsToGroup(clients = Some(displayClients.map(_.id).toList), search = Some("zzzzzz"))
      val selectedClients = displayClients.take(1)
      expectGetSessionItem(SELECTED_CLIENTS, displayClients.takeRight(2))
      expectPutSessionItem(FILTERED_CLIENTS, Nil)

      //when
      val filteredClients = await(service.filterClients(formData)(selectedClients))

      filteredClients shouldBe Nil
    }

    "WHEN the search term DOES MATCH SOME but none are selected" in {
      //given
      val formData = AddClientsToGroup(clients = Some(displayClients.map(_.id).toList), search = Some("friendly name"))
      val clients = displayClients.take(1)
      expectGetSessionItem(SELECTED_CLIENTS, displayClients.takeRight(2))
      expectPutSessionItem(FILTERED_CLIENTS, displayClients.take(1))

      //when
      val filteredClients = await(service.filterClients(formData)(clients))

      filteredClients shouldBe displayClients.take(1)

    }

    "WHEN the search term DOES MATCH SOME and there are SELECTED clients that match those passed in" in {
      //given
      val formData = AddClientsToGroup(clients = Some(displayClients.map(_.id).toList), search = Some("friendly name"))
      val clients = displayClients.take(5)
      //the ones that match the SELECTED_CLIENTS session items will be marked as selected = true
      val expectedClients = clients.zipWithIndex.map(zip => if(zip._2 < 3) zip._1.copy(selected = true) else zip._1)
      expectGetSessionItem(SELECTED_CLIENTS, clients.take(3))
      expectPutSessionItem(FILTERED_CLIENTS, expectedClients)

      //when
      val filteredClients = await(service.filterClients(formData)(clients))

      //first 2 clients should be
      filteredClients shouldBe expectedClients

    }
  }

  "saveSearch" should {

    "PUT search terms to session" in {
      //expect
      val searchTerm = Some("blah")
      val filterTerm = Some("MTD-VAT")
      expectPutSessionItem[String](CLIENT_SEARCH_INPUT, searchTerm.get)
      expectPutSessionItem[String](CLIENT_FILTER_INPUT, filterTerm.get)

      //when
      await(service.saveSearch(arn)(searchTerm, filterTerm))

    }

    "delete session items if both are empty" in {
      //expect
      val searchTerm = Some("")
      val filterTerm = Some("")
      expectDeleteSessionItems(Seq(CLIENT_SEARCH_INPUT, CLIENT_FILTER_INPUT))

      //when
      await(service.saveSearch(arn)(searchTerm, filterTerm))

    }
  }

  "savePageOfClients" should {

    "ADD selected clients to SELECTED_CLIENTS for current page" in {

      //expect
      val clientsSelectedOnThisPage = displayClients.take(4)
      val selectedClientIdsPosted = clientsSelectedOnThisPage.map(_.id).toList
      val alreadySelectedClients = displayClients.takeRight(2)
      expectGetSessionItem(SELECTED_CLIENTS, alreadySelectedClients)
      expectGetSessionItem(CURRENT_PAGE_CLIENTS, displayClients.take(6))
      expectDeleteSessionItems(clientFilteringKeys)
      val expectedToBeSaved = (alreadySelectedClients ++ clientsSelectedOnThisPage).map(_.copy(selected = true)).sortBy(_.name)

      expectPutSessionItem(SELECTED_CLIENTS, expectedToBeSaved)

      val formData = AddClientsToGroup(None, None, Some(selectedClientIdsPosted), CONTINUE_BUTTON)

      //when
      await(service.savePageOfClients(formData))

    }

    "ADD selected clients to SELECTED_CLIENTS for current page and REMOVE existing ones on page that are not selected" in {

      //expect
      val clientsSelectedOnThisPage = displayClients.take(4).takeRight(2)
      val selectedClientIdsPosted = clientsSelectedOnThisPage.map(_.id).toList
      val alreadySelectedClients = displayClients.take(2) // <-- these were the already selected clients
      expectGetSessionItem(SELECTED_CLIENTS, alreadySelectedClients)
      expectGetSessionItem(CURRENT_PAGE_CLIENTS, displayClients.take(6))
      expectDeleteSessionItems(clientFilteringKeys)
      //we expect to save in session ordered by name and 'selected = true'
      val expectedToBeSaved = (clientsSelectedOnThisPage).map(_.copy(selected = true)).sortBy(_.name)
      expectPutSessionItem(SELECTED_CLIENTS, expectedToBeSaved)


      val formData = AddClientsToGroup(None, None, Some(selectedClientIdsPosted), CONTINUE_BUTTON)

      //when
      await(service.savePageOfClients(formData))

    }
  }

  "getAvailableTaxServiceClientCount" should {
    "delegate to AP connector" in {
      implicit lazy val mockAPConnectorz: AgentPermissionsConnector = mock[AgentPermissionsConnector] // seems a bit weird it's implicit

      //expect
      expectGetAvailableTaxServiceClientCountFromConnector(arn)

      //when
      await(service.getAvailableTaxServiceClientCount(arn))

    }
  }

  "getAvailableTaxServiceClientCount" should {
    "delegate to AP connector" in {
      implicit lazy val mockAPConnectorz: AgentPermissionsConnector = mock[AgentPermissionsConnector]

      //expect
      expectGetTaxGroupClientCountFromConnector(arn)

      //when
      await(service.getTaxGroupClientCount(arn))

    }
  }
}
