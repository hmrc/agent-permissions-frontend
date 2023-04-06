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

package helpers

import akka.Done
import models.{DisplayClient, GroupId}
import org.scalamock.scalatest.MockFactory
import play.api.mvc.Request
import services.ClientService
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, PaginatedList, PaginationMetaData}
import uk.gov.hmrc.agentmtdidentifiers.utils.PaginatedListBuilder
import uk.gov.hmrc.agents.accessgroups.GroupSummary
import uk.gov.hmrc.http.HeaderCarrier
import utils.FilterUtils

import scala.concurrent.{ExecutionContext, Future}

trait ClientServiceMocks extends MockFactory {

  def expectGetAvailableTaxServiceClientCount(arn: Arn)
                                             (numberOfEachService: List[Int])
                                             (implicit clientService: ClientService): Unit = {
    val data: Map[String, Int] = Map(
      "HMRC-MTD-IT" -> numberOfEachService.head,
      "HMRC-MTD-VAT" -> numberOfEachService(1),
      "HMRC-CGT-PD" -> numberOfEachService(2),
      "HMRC-PPT-ORG" -> numberOfEachService(3),
      "HMRC-TERS" -> numberOfEachService(4)
    ).filter { case (service, count) => count != 0 }
    (clientService
      .getAvailableTaxServiceClientCount(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful data).once()

  }

  def expectGetUnassignedClients(arn: Arn)
                                (clients: Seq[DisplayClient], page: Int = 1, pageSize: Int = 20, search: Option[String] = None, filter: Option[String] = None)
                                (implicit clientService: ClientService): Unit =
    (clientService
      .getUnassignedClients(_: Arn)(_: Int, _: Int, _: Option[String], _: Option[String])(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
      .expects(arn, page, pageSize, search, filter, *, *, *)
      .returning(Future.successful(PaginatedListBuilder.build(page = page, pageSize = pageSize, fullList = FilterUtils.filterClients(clients, search, filter)))).once()

  def expectLookupClient(arn: Arn)
                        (client: DisplayClient)
                        (implicit clientService: ClientService): Unit =
    (clientService
      .lookupClient(_: Arn)(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, client.id, *, *)
      .returning(Future successful Some(client))
      .once()

  def expectGetClient(arn: Arn)
                     (client: DisplayClient)
                     (implicit clientService: ClientService): Unit =
    (clientService
      .getClient(_: Arn)(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, client.id, *, *)
      .returning(Future successful Some(client))
      .once()

  def expectLookupClientNone(arn: Arn)
                            (implicit clientService: ClientService): Unit =
    (clientService
      .lookupClient(_: Arn)(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .returning(Future successful None)
      .once()

  def expectUpdateClientReference(arn: Arn, client: DisplayClient, newName: String)
                                 (implicit clientService: ClientService): Unit =
    (clientService
      .updateClientReference(_: Arn, _: DisplayClient, _: String)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
      .expects(arn, client, newName, *, *, *)
      .returning(Future successful Done)
      .once()

  def expectLookupClientNotFound(arn: Arn)
                                (clientId: String)
                                (implicit clientService: ClientService): Unit =
    (clientService
      .lookupClient(_: Arn)(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, clientId, *, *)
      .returning(Future successful None).once()

  def expectGetClientNotFound(arn: Arn)
                             (clientId: String)
                             (implicit clientService: ClientService): Unit =
    (clientService
      .getClient(_: Arn)(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, clientId, *, *)
      .returning(Future successful None).once()


  def expectGetPageOfClients(arn: Arn, page: Int = 1, pageSize: Int = 20)
                            (clients: Seq[DisplayClient])
                            (implicit clientService: ClientService): Unit = {
    val paginatedList = PaginatedList(pageContent = clients,
      paginationMetaData = PaginationMetaData(lastPage = false, firstPage = page == 1, 40, 40 / pageSize, pageSize, page, clients.length))
    (clientService
      .getPaginatedClients(_: Arn)(_: Int, _: Int)(_: Request[_], _: HeaderCarrier,
        _: ExecutionContext))
      .expects(arn, page, pageSize, *, *, *)
      .returning(Future successful paginatedList)
  }

  def expectGetPaginatedClientsToAddToGroup(groupId: GroupId,
                                            page: Int = 1,
                                            pageSize: Int = 20,
                                            search: Option[String] = None,
                                            filter: Option[String] = None)
                                           (groupSummary: GroupSummary, clients: Seq[DisplayClient])
                                           (implicit clientService: ClientService): Unit = {
    val paginatedList = PaginatedList(pageContent = clients,
      paginationMetaData = PaginationMetaData(lastPage = false, firstPage = page == 1, 40, 40 / pageSize, pageSize, page, clients.length))
    (clientService
      .getPaginatedClientsToAddToGroup(_: GroupId)(_: Int, _: Int, _: Option[String], _: Option[String])(_: Request[_], _: HeaderCarrier,
        _: ExecutionContext))
      .expects(groupId, page, pageSize, search, filter, *, *, *)
      .returning(Future.successful((groupSummary, paginatedList)))
  }

  def expectGetPageOfClientsNone(arn: Arn, page: Int = 1, pageSize: Int = 10)
                                (implicit clientService: ClientService): Unit = {
    val paginatedList = PaginatedList(
      pageContent = Seq.empty[DisplayClient],
      paginationMetaData = PaginationMetaData(lastPage = false, firstPage = false, 0, 0, 0, 0, 0)
    )
    (clientService
      .getPaginatedClients(_: Arn)(_: Int, _: Int)(_: Request[_], _: HeaderCarrier,
        _: ExecutionContext))
      .expects(arn, page, pageSize, *, *, *)
      .returning(Future successful paginatedList)
  }
}
