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
import controllers.{CLIENT_FILTER_INPUT, CLIENT_SEARCH_INPUT, CURRENT_PAGE_CLIENTS, FILTERED_CLIENTS, SELECTED_CLIENTS}
import helpers.BaseSpec
import models.DisplayClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client, PaginatedList}
import uk.gov.hmrc.agentmtdidentifiers.utils.PaginatedListBuilder
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class ClientServiceSpec extends BaseSpec {

  implicit val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit lazy val sessionCacheService: SessionCacheService = mock[SessionCacheService]
  implicit val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
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

  "getUnassignedClients" should {
    "Gets them from mockAgentPermissionsConnector" in {
      //given
      (mockAgentPermissionsConnector.unassignedClients(_: Arn)(_: Int, _: Int, _: Option[String], _: Option[String])(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, 1, 20, *, *, *, *)
        .returning(Future.successful(PaginatedListBuilder.build(page = 1, pageSize = 20, fullList = displayClients))).once()

      //when
      val unassignedClients: PaginatedList[DisplayClient] = await(service.getUnassignedClients(arn)(1, 20))


      //then
      unassignedClients.pageContent shouldBe displayClients
    }
  }

  "getPaginatedClients" should {

    "work as expected" in{
        expectGetSessionItem(CLIENT_FILTER_INPUT, "f")
        expectGetSessionItem(CLIENT_SEARCH_INPUT, "s")
        expectGetSessionItemNone(SELECTED_CLIENTS)
        expectPutSessionItem(CURRENT_PAGE_CLIENTS, displayClients)
        expectGetPaginatedClients(arn)(fakeClients)(search = Some("s"), filter = Some("f"))

        val paginatedList = await(service.getPaginatedClients(arn)())

        paginatedList.pageContent shouldBe displayClients
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

  //TODO remove? test seems to be duplicated in SessionCacheOperationsServiceSpec
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

  "getAvailableTaxServiceClientCount" should {
    "delegate to AP connector" in {
      //expect
      expectGetAvailableTaxServiceClientCountFromConnector(arn)

      //when
      await(service.getAvailableTaxServiceClientCount(arn))

    }
  }

}
