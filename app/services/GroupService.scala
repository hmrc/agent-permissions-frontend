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
import connectors.{AddMembersToAccessGroupRequest, AddOneTeamMemberToGroupRequest, AgentPermissionsConnector, AgentUserClientDetailsConnector, GroupRequest, UpdateAccessGroupRequest}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, PaginatedList, PaginationMetaData}
import controllers._
import models.TeamMember.toAgentUser
import models.{DisplayClient, GroupId, TeamMember}
import play.api.Logging
import play.api.mvc.Request
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.agentmtdidentifiers.utils.PaginatedListBuilder
import uk.gov.hmrc.agents.accessgroups.{Client, CustomGroup, GroupSummary, UserDetails}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[GroupServiceImpl])
trait GroupService {

  @deprecated("group could be too big with 5000+ clients - use getCustomGroupSummary & paginated lists instead")
  def getGroup(groupId: GroupId)
              (implicit hc: HeaderCarrier, ec: ExecutionContext) : Future[Option[CustomGroup]]

  def getCustomSummary(groupId: GroupId)
                      (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[GroupSummary]]

  def getPaginatedClientsForCustomGroup(groupId: GroupId)
                                       (page: Int, pageSize: Int)
                                       (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[(Seq[DisplayClient], PaginationMetaData)]

  def getTeamMembersFromGroup(arn: Arn)(teamMembersInGroup: Seq[TeamMember])
                             (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[TeamMember]]

  def createGroup(arn: Arn, groupName: String)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[Done]

  def updateGroup(groupId: GroupId, group: UpdateAccessGroupRequest)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def deleteGroup(groupId: GroupId)
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

  def addMembersToGroup(id: GroupId, groupRequest: AddMembersToAccessGroupRequest)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def addOneMemberToGroup(id: GroupId, groupRequest: AddOneTeamMemberToGroupRequest)
                         (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def groupNameCheck(arn: Arn, groupName: String)
                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean]

  def removeClientFromGroup(id: GroupId, clientId: String)
                  (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def removeTeamMemberFromGroup(id: GroupId, clientId: String, isCustom: Boolean)
                  (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

}


@Singleton
class GroupServiceImpl @Inject()(
                                  agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
                                  sessionCacheService: SessionCacheService,
                                  agentPermissionsConnector: AgentPermissionsConnector
                                ) extends GroupService with Logging {

  @deprecated("group could be too big with 5000+ clients - use getCustomGroupSummary & paginated lists instead")
  def getGroup(id: GroupId)
              (implicit hc: HeaderCarrier, ec: ExecutionContext) : Future[Option[CustomGroup]] =
    agentPermissionsConnector.getGroup(id)

  def getCustomSummary(id: GroupId)
              (implicit hc: HeaderCarrier, ec: ExecutionContext) : Future[Option[GroupSummary]] =
    agentPermissionsConnector.getCustomSummary(id)

  def getPaginatedClientsForCustomGroup(groupId: GroupId)
                                       (page: Int, pageSize: Int)
                                       (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext) : Future[(Seq[DisplayClient], PaginationMetaData)] = {
    for {
      searchTerm <- sessionCacheService.get(CLIENT_SEARCH_INPUT)
      filterTerm <- sessionCacheService.get(CLIENT_FILTER_INPUT)
      list <- agentPermissionsConnector.getPaginatedClientsForCustomGroup(groupId)(page, pageSize, searchTerm, filterTerm)
      displayList = list.pageContent.map(client => DisplayClient.fromClient(client))
    } yield (displayList, list.paginationMetaData)
  }

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

  def updateGroup(groupId: GroupId, group: UpdateAccessGroupRequest)
                          (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] =
    agentPermissionsConnector.updateGroup(groupId, group)

  def deleteGroup(groupId: GroupId)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] =
    agentPermissionsConnector.deleteGroup(groupId)

  def addMembersToGroup(id: GroupId, groupRequest: AddMembersToAccessGroupRequest)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] =
    agentPermissionsConnector.addMembersToGroup(id, groupRequest)

  def addOneMemberToGroup(id: GroupId, groupRequest: AddOneTeamMemberToGroupRequest)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] =
    agentPermissionsConnector.addOneTeamMemberToGroup(id, groupRequest)

  def groupNameCheck(arn: Arn, groupName: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    agentPermissionsConnector.groupNameCheck(arn, groupName)

  def removeClientFromGroup(groupId: GroupId, clientId: String)
                           (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    agentPermissionsConnector.removeClientFromGroup(groupId, clientId)
  }

  def removeTeamMemberFromGroup(groupId: GroupId, memberId: String, isCustom: Boolean)
                           (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    agentPermissionsConnector.removeTeamMemberFromGroup(groupId, memberId, isCustom)
  }
}
