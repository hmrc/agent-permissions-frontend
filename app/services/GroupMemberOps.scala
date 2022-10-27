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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.{ExecutionContext, Future}

trait GroupMemberOps {

  val sessionCacheService: SessionCacheService

  def addSelectablesToSession[T <: Selectable](selectables: List[T])
                                              (selectedKey: DataKey[Seq[T]],
                                               filteredKey: DataKey[Seq[T]])
                                              (implicit hc: HeaderCarrier, request: Request[Any],
                                               ec: ExecutionContext, reads: Reads[Seq[T]], writes: Writes[Seq[T]]): Future[Unit] = {

    for {
      selectedInSession <- sessionCacheService.get[Seq[T]](selectedKey).map(_.map(_.toList))

      filteredSelected <- sessionCacheService.get[Seq[T]](filteredKey)
        .map(_.map(_.filter(_.selected == true).toList))

      deSelected = filteredSelected.orElse(selectedInSession).map(_ diff selectables)
      added = selectables.diff(filteredSelected.getOrElse(Nil))

      toSave = added ::: selectedInSession.getOrElse(Nil) diff deSelected.getOrElse(Nil)
      _ <- sessionCacheService.put[Seq[T]](selectedKey, toSave.distinct)

    } yield ()
  }

}
