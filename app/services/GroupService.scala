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
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector, GroupSummary}
import controllers.{FILTERED_CLIENTS, FILTERED_GROUPS_INPUT, FILTERED_GROUP_SUMMARIES, SELECTED_CLIENTS, ToFuture}
import models.DisplayClient.toEnrolment
import models.TeamMember.toAgentUser
import models.{DisplayClient, TeamMember}
import play.api.Logging
import play.api.mvc.Request
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[GroupServiceImpl])
trait GroupService {

  def getClientsForManageGroups(displayClients: Future[Seq[DisplayClient]])(implicit request: Request[_],
                                                                            hc: HeaderCarrier,
                                                                            ec: ExecutionContext): Future[List[DisplayClient]]

  def getTeamMembersFromGroup(arn: Arn)(
    teamMembersInGroup: Option[Seq[TeamMember]]
  )(implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[Seq[TeamMember]]]

  def groupSummaries(arn: Arn)
                    (implicit request: Request[_],
                     ec: ExecutionContext,
                     hc: HeaderCarrier): Future[(Seq[GroupSummary], Seq[DisplayClient])]

  def groupSummariesForClient(arn: Arn, client: DisplayClient)
                             (implicit request: Request[_],
                              ec: ExecutionContext,
                              hc: HeaderCarrier): Future[Seq[GroupSummary]]

  def groupSummariesForTeamMember(arn: Arn, teamMember: TeamMember)
                                 (implicit request: Request[_],
                                  ec: ExecutionContext,
                                  hc: HeaderCarrier): Future[Seq[GroupSummary]]

  def filterByGroupName(searchTerm: String)(arn: Arn)
                       (implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): Future[Unit]


}


@Singleton
class GroupServiceImpl @Inject()(
    agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
    sessionCacheRepository: SessionCacheRepository,
    agentPermissionsConnector: AgentPermissionsConnector
) extends GroupService with Logging {

  def getClientsForManageGroups(displayClients: Future[Seq[DisplayClient]])(implicit request: Request[_],
                                          hc: HeaderCarrier,
                                          ec: ExecutionContext): Future[List[DisplayClient]] =
    for {
      maybeSelectedClients <- sessionCacheRepository
        .getFromSession[Seq[DisplayClient]](SELECTED_CLIENTS)
      dcWithoutPreselected <- displayClients.map(_.filterNot(clients =>
      maybeSelectedClients.fold(false)(_.map(_.hmrcRef).contains(clients.hmrcRef))))
      mergeWithPreselected = dcWithoutPreselected.toList  ::: maybeSelectedClients.getOrElse(List.empty).toList
      sorted = mergeWithPreselected.sortBy(_.name)
    } yield sorted



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


  def groupSummaries(arn: Arn)
                    (implicit request: Request[_], ec: ExecutionContext, hc: HeaderCarrier): Future[(Seq[GroupSummary], Seq[DisplayClient])] = {

   val groupSummaries = sessionCacheRepository.getFromSession[Seq[GroupSummary]](FILTERED_GROUP_SUMMARIES).flatMap {
     case Some(gs) => gs.toFuture
     case None =>
       agentPermissionsConnector.groupsSummaries(arn).map {
         case Some(gs) => gs._1
         case None => {
           logger.warn(s"no group summaries returned")
           Seq.empty
         }
       }
   }

   val clients = sessionCacheRepository.getFromSession[Seq[DisplayClient]](FILTERED_CLIENTS).flatMap {
     case Some(fc) => fc.toFuture
     case None =>
       agentPermissionsConnector.groupsSummaries(arn).flatMap {
         case Some(gs) => getClientsForManageGroups(gs._2.toFuture)
         case None => {
           logger.warn("no group summaries returned for unassigned clients result")
           Seq.empty.toFuture
         }
       }
   }
    for {
      g <- groupSummaries
      c <- clients
    } yield (g,c)
  }

  def groupSummariesForClient(arn: Arn, client: DisplayClient)
                    (implicit request: Request[_], ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[GroupSummary]] = {
    val enrolment = toEnrolment(client)
    val groupSummaries = agentPermissionsConnector.getGroupsForClient(arn, enrolment).map {
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

  def filterByGroupName(searchTerm: String)(arn: Arn)(implicit hc: HeaderCarrier, request: Request[_], ec: ExecutionContext): Future[Unit] = {
    for {
      (x,y) <- sessionCacheRepository.putSession[String](FILTERED_GROUPS_INPUT, searchTerm)
      maybeGroupSummaries <- agentPermissionsConnector.groupsSummaries(arn)
      filtered = maybeGroupSummaries.map { summaries =>
        summaries._1.filter(_.groupName.toLowerCase.contains(searchTerm.toLowerCase))
      }
      _ <- sessionCacheRepository.putSession[Seq[GroupSummary]](FILTERED_GROUP_SUMMARIES, filtered.getOrElse(Seq.empty))
    } yield ()
  }
}
