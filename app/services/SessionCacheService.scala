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

import controllers.{GROUP_NAME, GROUP_NAME_CONFIRMED, SELECTED_CLIENTS, SELECTED_TEAM_MEMBERS, creatingGroupKeys, selectingClientsKeys, selectingTeamMemberKeys}
import models.{DisplayClient, TeamMember}
import play.api.mvc.Results.Redirect
import play.api.mvc.{Call, Request, Result}
import repository.SessionCacheRepository
import uk.gov.hmrc.mongo.cache.DataKey

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SessionCacheService @Inject()(
                                     sessionCacheRepository: SessionCacheRepository) {

  def writeGroupNameAndRedirect(name: String)(call: Call)(
    implicit request: Request[_],
    ec: ExecutionContext): Future[Result] = {
    for {
      _ <- sessionCacheRepository.putSession[String](GROUP_NAME, name)
      _ <- sessionCacheRepository
        .putSession[Boolean](GROUP_NAME_CONFIRMED, false)
    } yield Redirect(call)
  }

  def confirmGroupNameAndRedirect(call: Call)(implicit request: Request[_],
                                              ec: ExecutionContext): Future[Result] = {
    for {
      _ <- sessionCacheRepository
        .putSession[Boolean](GROUP_NAME_CONFIRMED, true)
    } yield Redirect(call)
  }

  def saveSelectedClients(clients: Seq[DisplayClient])(
    implicit request: Request[_],
    ec: ExecutionContext): Future[(String, String)] = {
    sessionCacheRepository.putSession[Seq[DisplayClient]](
      SELECTED_CLIENTS,
      clients.map(dc => dc.copy(selected = true))
    )
  }

  def clearSelectedClients()(implicit request: Request[_]): Future[Unit] = {
    sessionCacheRepository.deleteFromSession(SELECTED_CLIENTS)
  }

  def clearCreateGroupSession()(implicit r: Request[_], ec: ExecutionContext): Future[Unit] = {
    val sessionKeys: Seq[DataKey[_]] = selectingClientsKeys ++ selectingTeamMemberKeys ++ creatingGroupKeys
    Future.traverse(sessionKeys)(key => sessionCacheRepository.deleteFromSession(key)).map(_ => ())
  }

  def saveSelectedTeamMembers(teamMembers: Seq[TeamMember])(
    implicit request: Request[_],
    ec: ExecutionContext): Future[(String, String)] = {

    sessionCacheRepository.putSession[Seq[TeamMember]](
      SELECTED_TEAM_MEMBERS,
      teamMembers.map(member => member.copy(selected = true))
    )
  }

  def clearSelectedTeamMembers()(implicit request: Request[_]): Future[Unit] = {
    sessionCacheRepository.deleteFromSession(SELECTED_TEAM_MEMBERS)
  }
}
