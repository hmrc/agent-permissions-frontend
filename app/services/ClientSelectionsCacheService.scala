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

import controllers.SELECTED_CLIENTS
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Request
import repository.ClientSelectionsCacheRepo
import uk.gov.hmrc.mongo.cache.DataKey

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientSelectionsCacheService @Inject()(clientSelectionsRepo: ClientSelectionsCacheRepo) {

  def clearSelectedClients()(implicit request: Request[_]): Future[Unit] = {
    clientSelectionsRepo.deleteFromSession(SELECTED_CLIENTS)
  }

  def get[T](dataKey: DataKey[T])
            (implicit reads: Reads[T], request: Request[_]): Future[Option[T]] = {
    clientSelectionsRepo.getFromSession[T](dataKey)
  }

  def put[T](dataKey: DataKey[T], value: T)
            (implicit writes: Writes[T], request: Request[_], ec: ExecutionContext): Future[(String, String)] = {
    clientSelectionsRepo.putSession(dataKey, value)
  }

  def delete[T](dataKey: DataKey[T])
            (implicit request: Request[_]): Future[Unit] = {
    clientSelectionsRepo.deleteFromSession(dataKey)
  }

  def deleteAll(dataKeys: Seq[DataKey[_]])
            (implicit request: Request[_], ec: ExecutionContext): Future[Unit] = {
    Future.traverse(dataKeys)(key=> clientSelectionsRepo.deleteFromSession(key)).map(_ => ())
  }
}
