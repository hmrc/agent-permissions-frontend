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
import com.google.inject.ImplementedBy
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.{CLIENT_FILTER_INPUT, CLIENT_SEARCH_INPUT, CONTINUE_BUTTON, CURRENT_PAGE_CLIENTS, FILTERED_CLIENTS, FILTER_BUTTON, PAGINATION_BUTTON, SELECTED_CLIENTS, SELECTED_CLIENT_IDS, ToFuture, clientFilteringKeys}
import models.{AddClientsToGroup, DisplayClient}
import play.api.mvc.Request
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client, PaginatedList}
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


  def getUnassignedClients(arn: Arn)
                          (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayClient]]

  def lookupClient(arn: Arn)(clientId: String)
                  (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DisplayClient]]

  def lookupClients(arn: Arn)(ids: Option[List[String]])
                   (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[DisplayClient]]

  def saveSelectedOrFilteredClients(arn: Arn)
                                   (formData: AddClientsToGroup)(getClients: Arn => Future[Seq[DisplayClient]])
                       (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Unit]

  def savePageOfClients(formData: AddClientsToGroup)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Unit]

  def updateClientReference(arn: Arn, displayClient: DisplayClient, newName: String)
                           (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

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
      pageOfClients: PaginatedList[Client] <- agentUserClientDetailsConnector.getPaginatedClients(arn)(page, pageSize)
      maybeSelectedClientIds <- sessionCacheService.get[Seq[String]](SELECTED_CLIENT_IDS)
      existingSelectedClientIds = maybeSelectedClientIds.getOrElse(Nil)
      pageOfClientsMarkedSelected = pageOfClients
        .pageContent
        .map(cl => DisplayClient.fromClient(cl))
        .map(dc => if (existingSelectedClientIds.contains(dc.id)) dc.copy(selected = true) else dc)
      pageOfDisplayClients = PaginatedList(pageOfClientsMarkedSelected, pageOfClients.paginationMetaData)
      _ <- sessionCacheService.put(CURRENT_PAGE_CLIENTS, pageOfClientsMarkedSelected)
    } yield pageOfDisplayClients
  }

  def getUnassignedClients(arn: Arn)
                          (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayClient]] =
    agentPermissionsConnector.unassignedClients(arn)

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

  // getClients should be getAllClients or getUnassignedClients NOT getClients (maybe filtered)
  def saveSelectedOrFilteredClients(arn: Arn)
                                   (formData: AddClientsToGroup)
                                   (getClients: Arn => Future[Seq[DisplayClient]])
                                   (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Unit] = {

    val selectedClientIds = formData.clients.getOrElse(Seq.empty)

    val allClients = for {
      clients <- getClients(arn)
      selectedClientsToAddToSession = clients
        .filter(cl => selectedClientIds.contains(cl.id)).map(_.copy(selected = true)).toList
      _ <- addSelectablesToSession(selectedClientsToAddToSession)(SELECTED_CLIENTS, FILTERED_CLIENTS)
    } yield clients

    allClients.flatMap { clients =>
      formData.submit.trim match {
        case FILTER_BUTTON =>
          if (formData.search.isEmpty && formData.filter.isEmpty) {
            sessionCacheService.deleteAll(Seq(CLIENT_SEARCH_INPUT, CLIENT_FILTER_INPUT))
          } else {
            for {
              _ <- sessionCacheService.put(CLIENT_SEARCH_INPUT, formData.search.getOrElse(""))
              _ <- sessionCacheService.put(CLIENT_FILTER_INPUT, formData.filter.getOrElse(""))
              _ <- filterClients(formData)(clients)
            } yield ()
          }
        case _ => sessionCacheService.deleteAll(clientFilteringKeys)
      }
    }
  }

  def savePageOfClients(formData: AddClientsToGroup)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Unit] = {

    sessionCacheService.get(SELECTED_CLIENT_IDS).map(_.getOrElse(Seq.empty)).map(existingSelectedIds =>
      sessionCacheService.get(CURRENT_PAGE_CLIENTS).map(_.getOrElse(Seq.empty)).map(currentPageClients => {
        val clientIdsToAdd = formData.clients.getOrElse(Seq.empty)
        val idsToRemove = currentPageClients.map(_.id).diff(clientIdsToAdd)
        val newSelectedIds = existingSelectedIds.filterNot(r => idsToRemove.contains(r)) ++ clientIdsToAdd
        sessionCacheService.put(SELECTED_CLIENT_IDS, newSelectedIds.distinct)
      }
      )
    ).map { _ =>
      formData.submit.trim match {
        case CONTINUE_BUTTON => sessionCacheService.deleteAll(clientFilteringKeys)
        case _ => Future.successful()
      }
    }
  }

  def filterClients(formData: AddClientsToGroup)
                   (displayClients: Seq[DisplayClient])
                   (implicit request: Request[Any], ec: ExecutionContext)
  : Future[Seq[DisplayClient]] = {

    val filterTerm = formData.filter
    val searchTerm = formData.search
    val eventualSelectedClientIds = sessionCacheService.get(SELECTED_CLIENTS).map(_.map(_.map(_.id)))

    eventualSelectedClientIds.flatMap(maybeSelectedClientIds => {

      val selectedClientIds = maybeSelectedClientIds.getOrElse(Nil)

      for {
        clients <- Future.successful(displayClients)
        resultByTaxService = filterTerm.fold(clients)(term =>
          if (term == "TRUST") clients.filter(_.taxService.contains("HMRC-TERS"))
          else clients.filter(_.taxService == term)
        )
        resultByName = searchTerm.fold(resultByTaxService) { term =>
          resultByTaxService.filter(_.name.toLowerCase.contains(term.toLowerCase))
        }
        resultByTaxRef = searchTerm.fold(resultByTaxService) {
          term => resultByTaxService.filter(_.hmrcRef.toLowerCase.contains(term.toLowerCase))
        }
        consolidatedResult = (resultByName ++ resultByTaxRef).distinct
        result = consolidatedResult
          .map(dc => if (selectedClientIds.contains(dc.id)) dc.copy(selected = true) else dc)
          .toVector
        _ <- sessionCacheService.put(FILTERED_CLIENTS, result)
      } yield result
    }
    )

  }


  def updateClientReference(arn: Arn, displayClient: DisplayClient, newName: String)(implicit request: Request[_],
                                                                                     hc: HeaderCarrier,
                                                                                     ec: ExecutionContext): Future[Done] = {
    val client = Client(displayClient.enrolmentKey, newName)
    agentUserClientDetailsConnector.updateClientReference(arn, client)
  }

}