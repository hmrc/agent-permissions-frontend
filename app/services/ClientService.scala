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
import com.google.inject.ImplementedBy
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.{CLIENT_FILTER_INPUT, CLIENT_SEARCH_INPUT, CURRENT_PAGE_CLIENTS, FILTERED_CLIENTS, SELECTED_CLIENTS, ToFuture}
import models.DisplayClient
import play.api.libs.json.JsNumber
import play.api.mvc.Request
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client, GroupSummary, PaginatedList}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ClientServiceImpl])
trait ClientService {

  def getAllClients(arn: Arn)
                   (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayClient]]

  def getFilteredClientsElseAll(arn: Arn)
                               (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayClient]]

  def getPaginatedClients(arn: Arn)(page: Int, pageSize: Int)
                         (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext)
  : Future[PaginatedList[DisplayClient]]

  def getPaginatedClientsToAddToGroup(id: String)(page: Int, pageSize: Int, search: Option[String] = None, filter: Option[String] = None)
                                     (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext)
  : Future[(GroupSummary, PaginatedList[DisplayClient])]

  def getUnassignedClients(arn: Arn)(page: Int = 1, pageSize: Int = 20, search: Option[String] = None, filter: Option[String] = None)
                          (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[PaginatedList[DisplayClient]]

  def lookupClient(arn: Arn)(clientId: String)
                  (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DisplayClient]]

  def lookupClients(arn: Arn)(ids: Option[List[String]])
                   (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[DisplayClient]]

  def updateClientReference(arn: Arn, displayClient: DisplayClient, newName: String)
                           (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def getAvailableTaxServiceClientCount(arn: Arn)
                                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]]

}

@Singleton
class ClientServiceImpl @Inject()(
                                   agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
                                   agentPermissionsConnector: AgentPermissionsConnector,
                                   val sessionCacheService: SessionCacheService
                                 ) extends ClientService with GroupMemberOps {

  // returns the es3 list sorted by name, selecting previously selected clients
  def getAllClients(arn: Arn)
                   (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayClient]] = {
    for {
      allClientsFromEs3 <- getFromEs3AsDisplayClients(arn)
      maybeSelectedClients <- sessionCacheService.get[Seq[DisplayClient]](SELECTED_CLIENTS)
      existingSelectedClients = maybeSelectedClients.getOrElse(Nil)
      selectedClientIds = existingSelectedClients.map(_.id)
      es3ClientsMinusSelectedOnes = allClientsFromEs3.filterNot(dc => selectedClientIds.contains(dc.id))
      clientsWithSelectedOnesMarkedAsSelected = es3ClientsMinusSelectedOnes.toList ::: existingSelectedClients.toList
      sortedClients = clientsWithSelectedOnesMarkedAsSelected.sortBy(_.name)
    } yield sortedClients

  }

  // returns clients from es3 OR a filtered list, selecting previously selected clients
  def getFilteredClientsElseAll(arn: Arn)
                               (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayClient]] = {
    val maybeFilteredClients = sessionCacheService.get(FILTERED_CLIENTS)
    maybeFilteredClients.flatMap { maybeClients =>
      if (maybeClients.isDefined) Future.successful(maybeClients.get)
      else getAllClients(arn)
    }
  }

  def getPaginatedClients(arn: Arn)(page: Int = 1, pageSize: Int = 20)
                         (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[PaginatedList[DisplayClient]] = {
    for {
      searchTerm <- sessionCacheService.get(CLIENT_SEARCH_INPUT) // TODO these search/filter terms should be passed from outside. This function description mentions nothing about filtering and finding this here is unexpected and confusing.
      filterTerm <- sessionCacheService.get(CLIENT_FILTER_INPUT)
      pageOfClients <-
        agentUserClientDetailsConnector.getPaginatedClients(arn)(page, pageSize, searchTerm, filterTerm)
      maybeSelectedClients <- sessionCacheService.get[Seq[DisplayClient]](SELECTED_CLIENTS) // TODO This logic to pre-mark clients should probably be done by the caller. Ideally this function should and not touch the session cache at all!
      existingSelectedClientIds = maybeSelectedClients.getOrElse(Nil).map(_.id)
      pageOfClientsMarkedSelected = pageOfClients
        .pageContent
        .map(cl => DisplayClient.fromClient(cl))
        .map(dc => if (existingSelectedClientIds.contains(dc.id)) dc.copy(selected = true) else dc)
      totalClientsSelected = maybeSelectedClients.fold(0)(_.length)
      metadataWithExtra = pageOfClients.paginationMetaData.copy(extra = Some(Map("totalSelected" -> JsNumber(totalClientsSelected)))) // This extra data is needed to display correct 'selected' count in front-end
      _ <- sessionCacheService.put(CURRENT_PAGE_CLIENTS, pageOfClientsMarkedSelected) // TODO this side-effect does not belong in this 'get' type function! Move it to the caller site!
    } yield PaginatedList(pageOfClientsMarkedSelected, metadataWithExtra)
  }
  def getPaginatedClientsToAddToGroup(id: String)(page: Int, pageSize: Int, search: Option[String] = None, filter: Option[String] = None)
                                     (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext)
  : Future[(GroupSummary, PaginatedList[DisplayClient])] = {
    for {
      maybeSelectedClients <- sessionCacheService.get[Seq[DisplayClient]](SELECTED_CLIENTS)
      existingSelectedClientIds = maybeSelectedClients.getOrElse(Nil).map(_.id)
      tuple <- agentPermissionsConnector.getPaginatedClientsToAddToGroup(id)(page, pageSize, search, filter)
      pageOfClientsMarkedSelected = tuple._2
        .pageContent
        .map(dc => if (maybeSelectedClients.contains(dc.id)) dc.copy(selected = true) else dc)
      _ <- sessionCacheService.put(CURRENT_PAGE_CLIENTS, pageOfClientsMarkedSelected)
      clientsMarkedAsSelected = tuple._2.pageContent.map(dc => if (existingSelectedClientIds.contains(dc.id)) dc.copy(selected = true) else dc)
      x = (tuple._1, PaginatedList[DisplayClient] (pageContent = clientsMarkedAsSelected, paginationMetaData = tuple._2.paginationMetaData))
    } yield x
  }

  def getUnassignedClients(arn: Arn)(page: Int = 1, pageSize: Int = 20, search: Option[String] = None, filter: Option[String] = None)
                          (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[PaginatedList[DisplayClient]] =
    agentPermissionsConnector.unassignedClients(arn)(page, pageSize, search, filter)

  def lookupClient(arn: Arn)
                  (clientId: String)
                  (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DisplayClient]] = {
    for {
      es3AsDisplayClients <- getFromEs3AsDisplayClients(arn)
      maybeClient = es3AsDisplayClients.find(_.id == clientId)
    } yield maybeClient
  }

  def lookupClients(arn: Arn)
                   (ids: Option[List[String]])
                   (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[DisplayClient]] = {
    ids.fold(List.empty[DisplayClient].toFuture) {
      ids => getFromEs3AsDisplayClients(arn).map(clients => clients.filter(c => ids.contains(c.id)).toList)
    }
  }

  private def getFromEs3AsDisplayClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayClient]] = {
    for {
      es3Clients <- agentUserClientDetailsConnector.getClients(arn)
      es3AsDisplayClients = es3Clients.map(client => DisplayClient.fromClient(client))
    } yield es3AsDisplayClients
  }

  def updateClientReference(arn: Arn, displayClient: DisplayClient, newName: String)(implicit request: Request[_],
                                                                                     hc: HeaderCarrier,
                                                                                     ec: ExecutionContext): Future[Done] = {
    val client = Client(displayClient.enrolmentKey, newName)
    agentUserClientDetailsConnector.updateClientReference(arn, client)
  }

  def getAvailableTaxServiceClientCount(arn: Arn)
                                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]] = {
    agentPermissionsConnector.getAvailableTaxServiceClientCount(arn)
  }

}