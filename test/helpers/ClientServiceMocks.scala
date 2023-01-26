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

import models.{AddClientsToGroup, DisplayClient}
import org.scalamock.scalatest.MockFactory
import play.api.mvc.Request
import services.ClientService
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, PaginatedList, PaginationMetaData}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait ClientServiceMocks extends MockFactory {

  def expectGetFilteredClientsFromService(arn: Arn)
                                         (clients: Seq[DisplayClient])
                                         (implicit clientService: ClientService): Unit =
    (clientService
      .getFilteredClientsElseAll(_: Arn)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .returning(Future successful clients).once()

  def expectGetAllClientsFromService(arn: Arn)
                                    (clients: Seq[DisplayClient])
                                    (implicit clientService: ClientService): Unit =
    (clientService
      .getAllClients(_: Arn)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .returning(Future successful clients).once()

  def expectGetAvailableTaxServiceClientCount(arn: Arn)
                               (numberOfEachService: List[Int])
                               (implicit clientService: ClientService): Unit = {
    val data : Map[String, Int] = Map(
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

  def expectSaveSearch(arn: Arn)(searchTerm: Option[String] = None, filterTerm: Option[String] = None)(implicit clientService: ClientService): Unit =
    (clientService
      .saveSearch(_: Arn)(_: Option[String], _: Option[String])
      (_: Request[_], _: HeaderCarrier, _: ExecutionContext))
      .expects(arn, searchTerm, filterTerm, *, *, *)
      .returning(Future.successful(()))
      .once()

  def expectSaveSelectedOrFilteredClients(arn: Arn)(implicit clientService: ClientService): Unit =
    (clientService
      .saveSelectedOrFilteredClients(_: Arn)(_: AddClientsToGroup)
      (_: Arn => Future[Seq[DisplayClient]])(_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
      .expects(arn, *, *, *, *, *)
      .returning(Future.successful(()))
      .once()

  def expectGetUnassignedClients(arn: Arn)
                                (clients: Seq[DisplayClient])
                                (implicit clientService: ClientService): Unit =
    (clientService
      .getUnassignedClients(_: Arn)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .returning(Future successful clients).once()

  def expectLookupClient(arn: Arn)
                        (client: DisplayClient)
                        (implicit clientService: ClientService): Unit =
    (clientService
      .lookupClient(_: Arn)(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, client.id, *, *)
      .returning(Future successful Some(client)).once()

  def expectLookupClientNotFound(arn: Arn)
                                (clientId: String)
                                (implicit clientService: ClientService): Unit =
    (clientService
      .lookupClient(_: Arn)(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, clientId, *, *)
      .returning(Future successful None).once()


  def expectGetPageOfClients(arn: Arn, page: Int = 1, pageSize: Int = 20)
                                (clients: Seq[DisplayClient])
                                (implicit clientService: ClientService): Unit = {
    val paginatedList = PaginatedList(pageContent = clients,
      paginationMetaData = PaginationMetaData(lastPage = false, firstPage = page == 1, 40, 40 / pageSize, pageSize, page, clients.length))
    (clientService
      .getPaginatedClients(_: Arn)(_: Int, _: Int)( _: Request[_], _: HeaderCarrier,
        _: ExecutionContext))
      .expects(arn, page, pageSize, *, *, *)
      .returning(Future successful paginatedList)
  }

  def expectSavePageOfClients(formData: AddClientsToGroup, members: Seq[DisplayClient] = Seq.empty)
                                 (implicit clientService: ClientService): Unit =
    (clientService
      .savePageOfClients(_: AddClientsToGroup)
      (_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
      .expects(formData, *, *, *)
      .returning(Future successful members)

}
