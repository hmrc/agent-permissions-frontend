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

package controllers.action

import play.api.libs.json.Reads
import play.api.mvc.{Request, Result}
import services.SessionCacheService
import uk.gov.hmrc.mongo.cache.DataKey

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SessionAction @Inject()(sessionCacheService: SessionCacheService) {

  def withSessionItem[T](dataKey: DataKey[T])
                        (body: Option[T] => Future[Result])
                        (implicit reads: Reads[T], request: Request[_], ec: ExecutionContext): Future[Result] = {
    sessionCacheService.get[T](dataKey).flatMap(data => body(data))
  }
}
