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

import controllers.{CLIENT_FILTER_INPUT, CLIENT_SEARCH_INPUT, CONTINUE_BUTTON, CURRENT_PAGE_CLIENTS, SELECTED_CLIENTS, clientFilteringKeys}
import models.{AddClientsToGroup, DisplayClient}
import play.api.mvc.Request

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/**  Utility service to perform some complex operations on session cache values. */
@Singleton
class SessionCacheOperationsService @Inject()(val sessionCacheService: SessionCacheService) extends GroupMemberOps {

  def saveSearch(searchTerm: Option[String], filterTerm: Option[String])
                (implicit request: Request[_], ec: ExecutionContext): Future[Unit] = {
    if (searchTerm.getOrElse("").isEmpty && filterTerm.getOrElse("").isEmpty) {
      sessionCacheService.deleteAll(Seq(CLIENT_SEARCH_INPUT, CLIENT_FILTER_INPUT))
    } else {
      for {
        _ <- sessionCacheService.put(CLIENT_SEARCH_INPUT, searchTerm.getOrElse(""))
        _ <- sessionCacheService.put(CLIENT_FILTER_INPUT, filterTerm.getOrElse(""))
      } yield ()
    }
  }

  def savePageOfClients(formData: AddClientsToGroup)
                       (implicit ec: ExecutionContext, request: Request[Any]): Future[Seq[DisplayClient]] = {

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
        case CONTINUE_BUTTON => sessionCacheService.deleteAll(clientFilteringKeys).flatMap(_ => clientsInSession) // TODO Should this call be in this function? This function mentions nothing (and should know nothing) about filtering
        case _ => clientsInSession
      }
    )

  }

}
