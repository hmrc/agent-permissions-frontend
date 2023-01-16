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

import akka.Done
import com.google.inject.ImplementedBy
import connectors.{AddMembersToAccessGroupRequest, AgentPermissionsConnector, AgentUserClientDetailsConnector, GroupRequest, UpdateAccessGroupRequest}
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, Arn, Client, PaginatedList, UserDetails, AccessGroupSummary => GroupSummary}
import controllers._
import models.TeamMember.toAgentUser
import models.{DisplayClient, TeamMember}
import play.api.Logging
import play.api.mvc.Request
import repository.SessionCacheRepository
import uk.gov.hmrc.http.HeaderCarrier
import utils.PaginatedListBuilder

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[GroupServiceImpl])
trait GroupService {

  def getGroup(groupId: String)
              (implicit hc: HeaderCarrier, ec: ExecutionContext) : Future[Option[AccessGroup]]


  def getTeamMembersFromGroup(arn: Arn)(teamMembersInGroup: Seq[TeamMember])
                             (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[TeamMember]]

  def createGroup(arn: Arn, groupName: String)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[Done]

  def updateGroup(groupId: String, group: UpdateAccessGroupRequest)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def deleteGroup(groupId: String)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def getGroupSummaries(arn: Arn)(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[GroupSummary]]

  def getPaginatedGroupSummaries(arn: Arn, filterTerm: String = "")(page: Int = 1, pageSize: Int = 5)(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[PaginatedList[GroupSummary]]

  def groupSummariesForClient(arn: Arn, client: DisplayClient)
                             (implicit request: Request[_],
                              ec: ExecutionContext,
                              hc: HeaderCarrier): Future[Seq[GroupSummary]]

  def groupSummariesForTeamMember(arn: Arn, teamMember: TeamMember)
                                 (implicit request: Request[_],
                                  ec: ExecutionContext,
                                  hc: HeaderCarrier): Future[Seq[GroupSummary]]

  def addMembersToGroup(id: String, groupRequest: AddMembersToAccessGroupRequest)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def groupNameCheck(arn: Arn, groupName: String)
                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean]

}


@Singleton
class GroupServiceImpl @Inject()(
                                  agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
                                  sessionCacheService: SessionCacheService,
                                  agentPermissionsConnector: AgentPermissionsConnector
                                ) extends GroupService with Logging {


  def getGroup(id: String)
              (implicit hc: HeaderCarrier, ec: ExecutionContext) : Future[Option[AccessGroup]] =
    agentPermissionsConnector.getGroup(id)


  // Compares users in group with users on ARN & fetches missing details (email & cred role)
  def getTeamMembersFromGroup(arn: Arn)
                             (teamMembersInGroup: Seq[TeamMember] = Seq.empty)
                             (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[TeamMember]] =
    for {
      ugsUsers: Seq[UserDetails] <- agentUserClientDetailsConnector.getTeamMembers(arn)
      ugsAsTeamMembers = ugsUsers.map(TeamMember.fromUserDetails)
      groupTeamMembers = ugsAsTeamMembers
        .filter(tm => teamMembersInGroup.map(_.userId).contains(tm.userId))
        .sortBy(_.name)
      groupTeamMembersSelected = groupTeamMembers.map(_.copy(selected = true)) // makes them selected
    } yield groupTeamMembersSelected

  def getGroupSummaries(arn: Arn)
            (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext)
  : Future[Seq[GroupSummary]] = agentPermissionsConnector.getGroupSummaries(arn)

  def getPaginatedGroupSummaries(arn: Arn, filterTerm: String = "")(page: Int = 1, pageSize: Int = 5)
                       (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext)
  : Future[PaginatedList[GroupSummary]] =
    for {
      summaries <- agentPermissionsConnector.getGroupSummaries(arn)
      filteredSummaries = summaries.filter(_.groupName.toLowerCase.contains(filterTerm.toLowerCase))
    } yield PaginatedListBuilder.build[GroupSummary](page, pageSize, filteredSummaries)

  def createGroup(arn: Arn, groupName: String)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[Done] = {
    for {
      clients <- sessionCacheService.get(SELECTED_CLIENTS).map(_.map(_.map(client => Client(client.enrolmentKey, client
        .name))))
      agentUsers <- sessionCacheService.get(SELECTED_TEAM_MEMBERS).map(_.map(_.map(tm => toAgentUser(tm))))
      groupRequest = GroupRequest(groupName, agentUsers, clients)
      _ <- agentPermissionsConnector.createGroup(arn)(groupRequest)
      _ <- sessionCacheService.deleteAll(creatingGroupKeys)
      _ <- sessionCacheService.put(NAME_OF_GROUP_CREATED, groupName)
    } yield Done
  }

  def groupSummariesForClient(arn: Arn, client: DisplayClient)
                             (implicit request: Request[_], ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[GroupSummary]] = {
      agentPermissionsConnector.getGroupsForClient(arn, client.enrolmentKey)
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

  def updateGroup(groupId: String, group: UpdateAccessGroupRequest)
                          (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] =
    agentPermissionsConnector.updateGroup(groupId, group)

  def deleteGroup(groupId: String)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] =
    agentPermissionsConnector.deleteGroup(groupId)

  def addMembersToGroup(id: String, groupRequest: AddMembersToAccessGroupRequest)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] =
    agentPermissionsConnector.addMembersToGroup(id, groupRequest)

  def groupNameCheck(arn: Arn, groupName: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    agentPermissionsConnector.groupNameCheck(arn, groupName)
}
