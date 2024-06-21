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
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Request
import repository.SessionCacheRepository
import uk.gov.hmrc.mongo.cache.DataKey

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SessionCacheServiceImpl])
trait SessionCacheService {
  def get[T](dataKey: DataKey[T])(implicit reads: Reads[T], request: Request[_]): Future[Option[T]]
  def put[T](dataKey: DataKey[T], value: T)(implicit
    writes: Writes[T],
    request: Request[_],
    ec: ExecutionContext
  ): Future[(String, String)]
  def delete[T](dataKey: DataKey[T])(implicit request: Request[_]): Future[Unit]
  def deleteAll(dataKeys: Seq[DataKey[_]])(implicit request: Request[_], ec: ExecutionContext): Future[Unit]
}

@Singleton
class SessionCacheServiceImpl @Inject() (sessionCacheRepository: SessionCacheRepository) extends SessionCacheService {

  def get[T](dataKey: DataKey[T])(implicit reads: Reads[T], request: Request[_]): Future[Option[T]] =
    sessionCacheRepository.getFromSession[T](dataKey)

  def put[T](dataKey: DataKey[T], value: T)(implicit
    writes: Writes[T],
    request: Request[_],
    ec: ExecutionContext
  ): Future[(String, String)] =
    sessionCacheRepository.putSession(dataKey, value)

  def delete[T](dataKey: DataKey[T])(implicit request: Request[_]): Future[Unit] =
    sessionCacheRepository.deleteFromSession(dataKey)

  def deleteAll(dataKeys: Seq[DataKey[_]])(implicit request: Request[_], ec: ExecutionContext): Future[Unit] =
    Future.traverse(dataKeys)(key => sessionCacheRepository.deleteFromSession(key)).map(_ => ())
}

// In-memory implementation to use in tests.
class InMemorySessionCacheService(initialValues: Map[String, Any] = Map.empty) extends SessionCacheService {
// TODO test this class.
  val values: scala.collection.mutable.Map[String, Any] = scala.collection.mutable.Map(initialValues.toSeq: _*)

  def get[T](dataKey: DataKey[T])(implicit reads: Reads[T], request: Request[_]): Future[Option[T]] =
    Future.successful(values.get(dataKey.unwrap).map(_.asInstanceOf[T]))
  def put[T](dataKey: DataKey[T], value: T)(implicit
    writes: Writes[T],
    request: Request[_],
    ec: ExecutionContext
  ): Future[(String, String)] = {
    values += dataKey.unwrap -> (value: Any)
    Future.successful(("", ""))
  }
  def delete[T](dataKey: DataKey[T])(implicit request: Request[_]): Future[Unit] = {
    values.remove(dataKey.unwrap)
    Future.successful(())
  }
  def deleteAll(dataKeys: Seq[DataKey[_]])(implicit request: Request[_], ec: ExecutionContext): Future[Unit] = {
    dataKeys.map(_.unwrap).foreach(values.remove)
    Future.successful(())
  }

}
