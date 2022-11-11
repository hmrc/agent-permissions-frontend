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

import models.Selectable
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Request
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.{ExecutionContext, Future}

trait GroupMemberOps {

  val sessionCacheService: SessionCacheService

  def addSelectablesToSession[T <: Selectable](selectables: List[T])
                                              (selectedKey: DataKey[Seq[T]],
                                               filteredKey: DataKey[Seq[T]])
                                              (implicit request: Request[Any],
                                               ec: ExecutionContext, reads: Reads[Seq[T]], writes: Writes[Seq[T]]): Future[Unit] = {

    for {
      //ALL the existing selected items, whether visible to user or not
      currentlySelectedInSession <- sessionCacheService.get[Seq[T]](selectedKey).map(_.map(_.toList))

      //if a filter is on, we have these items. The ones that are also
      // selected are "currentlySelectedInFilteredSession"
      currentlySelectedInFilteredSession <- sessionCacheService
        .get[Seq[T]](filteredKey)
        .map(_.map(_.filter(_.selected == true).toList))

      //we want to remove the previously selected items in session in case they were de-selected by user.
      toRemoveFromSession = currentlySelectedInFilteredSession.orElse(currentlySelectedInSession).map(_ diff selectables)

      //These are the new items to add that were selected by the user but not already in the session
      toAdd = selectables.diff(currentlySelectedInFilteredSession.getOrElse(Nil))

      //the new session is the addition o
      currentlySelectedMinusThoseToRemove = currentlySelectedInSession
        .getOrElse(Nil)
        .diff(toRemoveFromSession.getOrElse(Nil))

      //now add the new ones to the existing session
      toSave = toAdd ::: currentlySelectedMinusThoseToRemove

      //add selected items to session
      _ <- sessionCacheService.put[Seq[T]](selectedKey, toSave.distinct)

    } yield ()
  }

}
