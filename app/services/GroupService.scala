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

import com.google.inject.ImplementedBy
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector, GroupRequest, GroupSummary}
import controllers._
import models.TeamMember.toAgentUser
import models.{DisplayClient, TeamMember}
import play.api.Logging
import play.api.mvc.Request
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[GroupServiceImpl])
trait GroupService {

  def getTeamMembersFromGroup(arn: Arn)(
    teamMembersInGroup: Option[Seq[TeamMember]]
  )(implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[Seq[TeamMember]]]

  def createGroup(arn: Arn, groupName: String)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[Unit]

  def groups(arn: Arn)(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[GroupSummary]]

  def groupSummariesForClient(arn: Arn, client: DisplayClient)
                             (implicit request: Request[_],
                              ec: ExecutionContext,
                              hc: HeaderCarrier): Future[Seq[GroupSummary]]

  def groupSummariesForTeamMember(arn: Arn, teamMember: TeamMember)
                                 (implicit request: Request[_],
                                  ec: ExecutionContext,
                                  hc: HeaderCarrier): Future[Seq[GroupSummary]]

}


@Singleton
class GroupServiceImpl @Inject()(
    agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
    sessionCacheRepository: SessionCacheRepository,
    agentPermissionsConnector: AgentPermissionsConnector
) extends GroupService with Logging {


  // Compares users in group with users on ARN & fetches missing details (email & cred role)
  def getTeamMembersFromGroup(arn: Arn)(
    teamMembersInGroup: Option[Seq[TeamMember]] = None
  )(implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[Seq[TeamMember]]] =
    for {
      ugsUsers <- agentUserClientDetailsConnector.getTeamMembers(arn)
      ugsAsTeamMembers = ugsUsers.map(list =>
        list.map(TeamMember.fromUserDetails))
      groupTeamMembers = ugsAsTeamMembers.map(
        teamMembers =>
          teamMembers.filter(teamMember =>
            teamMembersInGroup.fold(true)(
              _.map(_.userId).contains(teamMember.userId))))
        .map(_.sortBy(_.name))
      groupTeamMembersSelected = groupTeamMembers.map(_.map(_.copy(selected = true))) // makes them selected
    } yield groupTeamMembersSelected

  def groups(arn: Arn)
            (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext)
  : Future[Seq[GroupSummary]] = agentPermissionsConnector.groups(arn)

  def createGroup(arn: Arn, groupName: String)(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[Unit] = {
    for {
      clients    <- sessionCacheRepository.getFromSession(SELECTED_CLIENTS).map(_.map(_.map(client => Client(client.enrolmentKey, client.name))))
      agentUsers    <- sessionCacheRepository.getFromSession(SELECTED_TEAM_MEMBERS).map(_.map(_.map(tm => toAgentUser(tm))))
      groupRequest  = GroupRequest(groupName, agentUsers, clients)
      _             <- agentPermissionsConnector.createGroup(arn)(groupRequest)
      _             <- Future.sequence(creatingGroupKeys.map(key => sessionCacheRepository.deleteFromSession(key)))
      _             <- sessionCacheRepository.putSession(NAME_OF_GROUP_CREATED, groupName)
    } yield ()
  }

  def groupSummariesForClient(arn: Arn, client: DisplayClient)
                    (implicit request: Request[_], ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[GroupSummary]] = {
    val groupSummaries = agentPermissionsConnector.getGroupsForClient(arn, client.enrolmentKey).map {
          case Some(gs) => gs
          case None => Seq.empty
        }
    for {
      g <- groupSummaries
    } yield g
  }

  def groupSummariesForTeamMember(arn: Arn, teamMember: TeamMember)
                                 (implicit request: Request[_], ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[GroupSummary]] = {
    val agentUser = toAgentUser(teamMember)
    val groupSummaries = agentPermissionsConnector.getGroupsForTeamMember(arn, agentUser).map {
      case Some(gs) => gs
      case None => Seq.empty
    }
    for {
      g <- groupSummaries
    } yield g
  }

}
