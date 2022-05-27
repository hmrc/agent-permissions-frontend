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
import controllers.{DATA_KEY, ToFuture, routes}
import models.{Group, JourneySession}
import play.api.mvc.Results.Redirect
import play.api.mvc.{Call, Request}
import repository.SessionCacheRepository
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class SessionCacheService @Inject()(sessionCacheRepository: SessionCacheRepository) {


  def writeGroupNameAndRedirect(name: String)(call: Call)(session: JourneySession)(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext) = {
    val group = session.group.getOrElse(Group(name = name)).copy(name = name)
    sessionCacheRepository.putSession(DATA_KEY, session.copy(group = Some(group))).map(_ => Redirect(call))
  }

  def confirmGroupNameAndRedirect(call: Call)(session: JourneySession)(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext) = {
    session.group.fold(Redirect(routes.GroupController.showCreateGroup).toFuture) { grp =>
      sessionCacheRepository.putSession(DATA_KEY, session.copy(group = Some(grp.copy(nameConfirmed = true)))).map(_ => Redirect(call))
    }
  }
}
