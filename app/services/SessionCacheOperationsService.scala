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

import com.google.inject.ImplementedBy
import controllers.{CLIENT_FILTER_INPUT, CLIENT_SEARCH_INPUT, CONTINUE_BUTTON, CURRENT_PAGE_CLIENTS, FILTERED_CLIENTS, FILTER_BUTTON, SELECTED_CLIENTS, clientFilteringKeys}
import models.{AddClientsToGroup, DisplayClient}
import play.api.mvc.Request
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

// TODO! Once we rewrite all tests that mock any of the methods below, we can just turn this into a mixable trait (like [[GroupMemberOps]] - or even merged with [[GroupMembersOps]]) instead of a service...
// (we must rewrite the tests so that they check the state of session values rather than check for specific calls)

/**
 * Utility service to perform some complex operations on session cache values.
 */
@Singleton
class SessionCacheOperationsService @Inject()(val sessionCacheService: SessionCacheService) extends GroupMemberOps {

  // TODO this whole class needs documenting, it needs to be clear what it's doing. Note: I did not originally write it

  def saveSearch(searchTerm: Option[String], filterTerm: Option[String])
                (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    if (searchTerm.getOrElse("").isEmpty && filterTerm.getOrElse("").isEmpty) {
      sessionCacheService.deleteAll(Seq(CLIENT_SEARCH_INPUT, CLIENT_FILTER_INPUT))
    } else {
      for {
        _ <- sessionCacheService.put(CLIENT_SEARCH_INPUT, searchTerm.getOrElse(""))
        _ <- sessionCacheService.put(CLIENT_FILTER_INPUT, filterTerm.getOrElse(""))
      } yield ()
    }
  }

  // TODO Needs documenting - very confusing! -- DG
  // TODO What is the difference between this and savePageOfClients? -- DG
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
                       (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Seq[DisplayClient]] = {

    val clientsInSession = for {
      _ <- formData.search.fold(Future.successful(("", "")))(term => sessionCacheService.put(CLIENT_SEARCH_INPUT, term))
      _ <- formData.filter.fold(Future.successful(("", "")))(term => sessionCacheService.put(CLIENT_FILTER_INPUT, term))
      existingSelectedClients <- sessionCacheService.get(SELECTED_CLIENTS).map(_.getOrElse(Seq.empty))
      currentPageClients <- sessionCacheService.get(CURRENT_PAGE_CLIENTS).map(_.getOrElse(Seq.empty))
      clientIdsToAdd = formData.clients.getOrElse(Seq.empty)
      currentClientsToAddOrKeep = currentPageClients.filter(cl => clientIdsToAdd.contains(cl.id))
      idsToRemove = currentPageClients.map(_.id).diff(clientIdsToAdd)
      newSelectedClients = (existingSelectedClients ++ currentClientsToAddOrKeep)
        .map(_.copy(selected = true))
        .distinct
        .filterNot(cl => idsToRemove.contains(cl.id))
        .sortBy(_.name)
      _ <- sessionCacheService.put(SELECTED_CLIENTS, newSelectedClients)
    } yield (newSelectedClients)
    clientsInSession.flatMap(_ =>
      formData.submit.trim match {
        case CONTINUE_BUTTON => sessionCacheService.deleteAll(clientFilteringKeys).flatMap(_ => clientsInSession) // TODO This call should not be in this function. This function mentions nothing (and should know nothing) about filtering
        case _ => clientsInSession
      }
    )

  }

  // TODO Needs documenting - very confusing! -- DG
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
}