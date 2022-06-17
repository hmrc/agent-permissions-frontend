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

import controllers.{GROUP_CLIENTS_SELECTED, GROUP_NAME, GROUP_NAME_CONFIRMED, GROUP_TEAM_MEMBERS_SELECTED}
import models.{DisplayClient, TeamMember}
import play.api.mvc.Results.Redirect
import play.api.mvc.{Call, Request}
import repository.SessionCacheRepository
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class SessionCacheService @Inject()(sessionCacheRepository: SessionCacheRepository) {


  def writeGroupNameAndRedirect(name: String)(call: Call)(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext) = {
    for {
      _ <- sessionCacheRepository.putSession[String](GROUP_NAME, name)
      _ <- sessionCacheRepository.putSession[Boolean](GROUP_NAME_CONFIRMED, false)
    } yield Redirect(call)
  }

  def confirmGroupNameAndRedirect(call: Call)(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext) = {
    for {
      _ <- sessionCacheRepository.putSession[Boolean](GROUP_NAME_CONFIRMED, true)
    } yield Redirect(call)
  }

  def saveSelectedClients(clients: Seq[DisplayClient])
                         (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext) = {
    sessionCacheRepository.putSession[Seq[DisplayClient]](
      GROUP_CLIENTS_SELECTED,
      clients.map(dc => dc.copy(selected = true))
    )
  }

  def clearSelectedClients()(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext) = {
      sessionCacheRepository.deleteFromSession(GROUP_CLIENTS_SELECTED)

  }

  def saveSelectedTeamMembers(teamMembers: Seq[TeamMember])
                             (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext) = {

    sessionCacheRepository.putSession[Seq[TeamMember]](
      GROUP_TEAM_MEMBERS_SELECTED,
      teamMembers.map(member => member.copy(selected = true))
    )
  }

  def clearSelectedTeamMembers()(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext) = {
    sessionCacheRepository.deleteFromSession(GROUP_TEAM_MEMBERS_SELECTED)
  }
}
