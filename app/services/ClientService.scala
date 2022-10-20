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
import controllers.{CLEAR_BUTTON, CLIENT_FILTER_INPUT, CLIENT_REFERENCE, CLIENT_SEARCH_INPUT, CONTINUE_BUTTON, FILTERED_CLIENTS, FILTER_BUTTON, HIDDEN_CLIENTS_EXIST, SELECTED_CLIENTS, ToFuture, selectingClientsKeys}
import models.{AddClientsToGroup, DisplayClient}
import play.api.mvc.Request
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ClientServiceImpl])
trait ClientService {

  def getAllClients(arn: Arn)
                   (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[DisplayClient]]]

  def getFilteredClientsElseAll(arn: Arn)
                               (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[DisplayClient]]]

  def getUnassignedClients(arn: Arn)
                          (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayClient]]

  def getMaybeUnassignedClients(arn: Arn)(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[DisplayClient]]]

  def lookupClient(arn: Arn)(clientId: String)
                  (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DisplayClient]]

  def lookupClients(arn: Arn)(ids: Option[List[String]])
                   (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[List[DisplayClient]]]

  def saveSelectedOrFilteredClients(arn: Arn)
                                   (formData: AddClientsToGroup)(getClients: Arn => Future[Option[Seq[DisplayClient]]])
                                   (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Unit]

  def updateClientReference(arn: Arn, displayClient: DisplayClient, newName: String)
                           (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def getNewNameFromSession()(implicit request: Request[_], ec: ExecutionContext): Future[Option[String]]

}

@Singleton
class ClientServiceImpl @Inject()(
                                   agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
                                   agentPermissionsConnector: AgentPermissionsConnector,
                                   val sessionCacheRepository: SessionCacheRepository
                                 ) extends ClientService with GroupMemberOps {

  // returns the es3 list sorted by name, selecting previously selected clients
  def getAllClients(arn: Arn)
                   (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[DisplayClient]]] = {
    for {
      es3AsDisplayClients <- getFromEs3AsDisplayClients(arn)
      maybeSelectedClients <- sessionCacheRepository.getFromSession[Seq[DisplayClient]](SELECTED_CLIENTS)
      es3WithoutPreSelected = es3AsDisplayClients.map(
        _.filterNot(
          dc => maybeSelectedClients.fold(false)(_.map(_.hmrcRef).contains(dc.hmrcRef))
        )
      )
      mergedWithPreselected = es3WithoutPreSelected.map(
        _.toList ::: maybeSelectedClients.getOrElse(List.empty).toList
      )
      maybeClientsSortedByName = mergedWithPreselected.map(_.sortBy(_.name))
    } yield maybeClientsSortedByName
  }

  // returns clients from es3 OR a filtered list, selecting previously selected clients
  def getFilteredClientsElseAll(arn: Arn)(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext)
  : Future[Option[Seq[DisplayClient]]] = {

    val maybeFilteredClients = sessionCacheRepository.getFromSession(FILTERED_CLIENTS)
    maybeFilteredClients.flatMap { maybeClients =>
      if (maybeClients.isDefined) Future.successful(maybeClients)
      else getAllClients(arn)
    }
  }

  def getUnassignedClients(arn: Arn)
                          (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayClient]] =
    agentPermissionsConnector.unassignedClients(arn)


  def getMaybeUnassignedClients(arn: Arn)
                               (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext)
  : Future[Option[Seq[DisplayClient]]] = {
    getUnassignedClients(arn).map(clients => if (clients.isEmpty) None else Some(clients))
  }

  def lookupClient(arn: Arn)
                  (clientId: String)
                  (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DisplayClient]] = {
    for {
      es3AsDisplayClients <- getFromEs3AsDisplayClients(arn)
      maybeClient = es3AsDisplayClients.flatMap(clients => clients.find(_.id == clientId))
    } yield maybeClient
  }

  def lookupClients(arn: Arn)
                   (ids: Option[List[String]])
                   (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[List[DisplayClient]]] = {
    ids.fold(Option.empty[List[DisplayClient]].toFuture) {
      ids => getFromEs3AsDisplayClients(arn).map(_.map(clients => ids.flatMap(id => clients.find(_.id == id))))
    }
  }

  private def getFromEs3AsDisplayClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[DisplayClient]]] = {
    for {
      es3Clients <- agentUserClientDetailsConnector.getClients(arn)
      es3AsDisplayClients = es3Clients.map(clientSeq =>
        clientSeq.map(client => DisplayClient.fromClient(client)))
    } yield es3AsDisplayClients
  }

  private def clearSessionForSelectingClients()(implicit Request: Request[_], ec: ExecutionContext): Future[Unit] =
    Future.traverse(selectingClientsKeys)(key => sessionCacheRepository.deleteFromSession(key)).map(_ => ())

  // getClients should be getAllClients or getUnassignedClients NOT getClients (maybe filtered)
  def saveSelectedOrFilteredClients(arn: Arn)
                                   (formData: AddClientsToGroup)
                                   (getClients: Arn => Future[Option[Seq[DisplayClient]]])
                                   (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Unit] = {

    val selectedClientIds = formData.clients
    val common = for {
      clients <- lookupClients(arn)(selectedClientIds)
      maybeClients = clients.map(_.map(_.copy(selected = true)))
      _ <- addSelectablesToSession(maybeClients)(SELECTED_CLIENTS, FILTERED_CLIENTS)
    } yield ()
    common.map { _ =>
      formData.submit.trim match {
        case FILTER_BUTTON =>
          for {
            _ <- sessionCacheRepository.putSession(CLIENT_FILTER_INPUT, formData.filter.getOrElse(""))
            _ <- sessionCacheRepository.putSession(CLIENT_SEARCH_INPUT, formData.search.getOrElse(""))
            _ <- filterClients(arn)(formData)(getClients)
          } yield ()
        case _ =>
          for {
            _ <- sessionCacheRepository.deleteFromSession(CLIENT_FILTER_INPUT)
            _ <- sessionCacheRepository.deleteFromSession(CLIENT_SEARCH_INPUT)
          } yield ()
      }
    }
  }

  private def filterClients(arn: Arn)(formData: AddClientsToGroup)
                           (getClients: Arn => Future[Option[Seq[DisplayClient]]])
                           (implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext)
  : Future[Option[Seq[DisplayClient]]] = {

    val filterTerm = formData.filter
    val searchTerm = formData.search

    for {
      clients <- getClients(arn).map(_.map(_.toVector))
      resultByTaxService = filterTerm.fold(clients)(term =>
        if (term == "TRUST") clients.map(_.filter(_.taxService.contains("HMRC-TERS")))
        else clients.map(_.filter(_.taxService == term))
      )
      resultByName = searchTerm.fold(resultByTaxService) { term =>
        resultByTaxService.map(_.filter(_.name.toLowerCase.contains(term.toLowerCase)))
      }
      resultByTaxRef = searchTerm.fold(resultByTaxService) {
        term => resultByTaxService.map(_.filter(_.hmrcRef.toLowerCase.contains(term.toLowerCase)))
      }
      consolidatedResult = resultByName.map(_ ++ resultByTaxRef.getOrElse(Vector.empty)).map(_.distinct)
      result: Option[Vector[DisplayClient]] = consolidatedResult.map(_.toVector)
      _ <- result match {
        case Some(filteredResult) => sessionCacheRepository.putSession(FILTERED_CLIENTS, filteredResult)
        case None => Future.successful(())
      }
      hiddenClients = clients.map(_.filter(_.selected) diff result.getOrElse(Vector.empty).filter(_.selected))
      _ <- hiddenClients match {
        case Some(hidden) if hidden.nonEmpty => sessionCacheRepository.putSession(HIDDEN_CLIENTS_EXIST, true)
        case _ => Future.successful(())
      }
      _ <- Future.successful(println("*************"))
      _ <- Future.successful(println(clients.map(_.map(_.name)).mkString(",")))
      _ <- Future.successful(println("clients: " + clients.getOrElse(Seq.empty).length))
      _ <- Future.successful(println(""))
      _ <- Future.successful(println(result.map(_.map(_.name)).mkString(",")))
      _ <- Future.successful(println("result: " + result.getOrElse(Seq.empty).length))
      _ <- Future.successful(println(""))
      str = hiddenClients.map(_.map(_.name)).mkString(",")
      _ <- Future.successful(println("hidden: " + hiddenClients.getOrElse(Seq.empty).length))
      _ <- Future.successful(println(str))
      _ <- Future.successful(println("*************"))
    } yield result
  }


  def updateClientReference(arn: Arn, displayClient: DisplayClient, newName: String)(implicit request: Request[_],
                                                                                     hc: HeaderCarrier,
                                                                                     ec: ExecutionContext): Future[Done] = {
    val client = Client(displayClient.enrolmentKey, newName)
    agentUserClientDetailsConnector.updateClientReference(arn, client)
  }

  def getNewNameFromSession()(implicit request: Request[_],
                              ec: ExecutionContext): Future[Option[String]] = {
    for {
      newName <- sessionCacheRepository.getFromSession(CLIENT_REFERENCE)
    } yield newName
  }

}