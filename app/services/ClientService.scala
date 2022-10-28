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
import controllers.{CLIENT_FILTER_INPUT, CLIENT_SEARCH_INPUT, FILTERED_CLIENTS, FILTER_BUTTON, SELECTED_CLIENTS, ToFuture, clientFilteringKeys}
import models.{AddClientsToGroup, DisplayClient}
import play.api.mvc.Request
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ClientServiceImpl])
trait ClientService {

  def getAllClients(arn: Arn)
                   (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayClient]]

  def getFilteredClientsElseAll(arn: Arn)
                               (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayClient]]

  def getUnassignedClients(arn: Arn)
                          (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayClient]]

  def lookupClient(arn: Arn)(clientId: String)
                  (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DisplayClient]]

  def lookupClients(arn: Arn)(ids: Option[List[String]])
                   (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[DisplayClient]]

  def saveSelectedOrFilteredClients(arn: Arn)
                                   (formData: AddClientsToGroup)(getClients: Arn => Future[Seq[DisplayClient]])
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
      es3AsDisplayClients <- getFromEs3AsDisplayClients(arn)
      maybeSelectedClients <- sessionCacheService.get[Seq[DisplayClient]](SELECTED_CLIENTS)
      es3WithoutPreSelected = es3AsDisplayClients.filterNot(
          dc => maybeSelectedClients.fold(false)(_.map(_.hmrcRef).contains(dc.hmrcRef))
      )
      mergedWithPreselected = es3WithoutPreSelected.toList ::: maybeSelectedClients.getOrElse(List.empty).toList
      maybeClientsSortedByName = mergedWithPreselected.sortBy(_.name)
    } yield maybeClientsSortedByName

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
    val clientsMarkedSelected = for {
      allClients <- getClients(arn)
      selectedClients = allClients.filter(cl => selectedClientIds.contains(cl.id))
        .map(_.copy(selected = true)).toList
      _ <- addSelectablesToSession(selectedClients)(SELECTED_CLIENTS, FILTERED_CLIENTS)
    } yield allClients

    clientsMarkedSelected.flatMap { clients =>
      formData.submit.trim match {
        case FILTER_BUTTON =>
          if (formData.search.isEmpty && formData.filter.isEmpty) {
            sessionCacheService.deleteAll(clientFilteringKeys)
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

  private def filterClients(formData: AddClientsToGroup)
                           (selectedClients : Seq[DisplayClient])
                           (implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext)
  : Future[Seq[DisplayClient]] = {

    val filterTerm = formData.filter
    val searchTerm = formData.search

    for {
      clients <- Future.successful(selectedClients)
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
      result: Vector[DisplayClient] = consolidatedResult.toVector
      _ <- if(result.nonEmpty) sessionCacheService.put(FILTERED_CLIENTS, result)
      else Future.successful(())
    } yield result
  }


  def updateClientReference(arn: Arn, displayClient: DisplayClient, newName: String)(implicit request: Request[_],
                                                                                     hc: HeaderCarrier,
                                                                                     ec: ExecutionContext): Future[Done] = {
    val client = Client(displayClient.enrolmentKey, newName)
    agentUserClientDetailsConnector.updateClientReference(arn, client)
  }

}