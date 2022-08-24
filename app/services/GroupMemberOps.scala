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
import repository.SessionCacheRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.{ExecutionContext, Future}

trait GroupMemberOps {

  val sessionCacheRepository: SessionCacheRepository

  def addSelectablesToSession[T <: Selectable](formData: Option[List[T]])
                                              (sessionMembersDataKey: DataKey[Seq[T]],
                                               filteredMembersDataKey: DataKey[Seq[T]])
                                              (implicit hc: HeaderCarrier, request: Request[Any],
                                               ec: ExecutionContext, reads: Reads[Seq[T]], writes: Writes[Seq[T]]): Future[Unit] =
    for {
      inSession <- sessionCacheRepository.getFromSession[Seq[T]](sessionMembersDataKey).map(_.map(_.toList))

      filteredSelected <- sessionCacheRepository
        .getFromSession[Seq[T]](filteredMembersDataKey)
        .map(_.map(_.filter(_.selected == true).toList))

      deSelected = filteredSelected.orElse(inSession).map(_ diff formData.getOrElse(Nil))
      added = formData.map(_ diff filteredSelected.getOrElse(Nil))

      toSave = added.getOrElse(Nil) ::: inSession.getOrElse(Nil) diff deSelected.getOrElse(Nil)
      _ <- sessionCacheRepository.putSession[Seq[T]](sessionMembersDataKey, toSave.distinct)

    } yield ()

}