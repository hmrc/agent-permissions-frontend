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
import connectors.AgentUserClientDetailsConnector
import controllers.{CLEAR_BUTTON, CONTINUE_BUTTON, FILTERED_TEAM_MEMBERS, FILTER_BUTTON, SELECTED_TEAM_MEMBERS, TEAM_MEMBER_SEARCH_INPUT, ToFuture, teamMemberFilteringKeys}
import models.{AddTeamMembersToGroup, TeamMember}
import play.api.mvc.Request
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TeamMemberServiceImpl])
trait TeamMemberService {

  def getAllTeamMembers(arn: Arn)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[Seq[TeamMember]]

  def getFilteredTeamMembersElseAll(arn: Arn)(implicit hc: HeaderCarrier,
                                              ec: ExecutionContext, request: Request[_]): Future[Seq[TeamMember]]

  def lookupTeamMember(arn: Arn)(id: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TeamMember]]

  def lookupTeamMembers(arn: Arn)(ids: Option[List[String]])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[TeamMember]]

  def saveSelectedOrFilteredTeamMembers(buttonSelect: String)
                                       (arn: Arn)
                                       (formData: AddTeamMembersToGroup
                                       )(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Unit]


}


@Singleton
class TeamMemberServiceImpl @Inject()(
                                       agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
                                       val sessionCacheService: SessionCacheService
                                     ) extends TeamMemberService with GroupMemberOps {

  // returns team members from agent-user-client-details, selecting previously selected team members
  def getAllTeamMembers(arn: Arn)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[Seq[TeamMember]] = {
    for {
      ugsAsTeamMembers <- getFromUgsAsTeamMember(arn)
      maybeSelectedTeamMembers <- sessionCacheService.get[Seq[TeamMember]](SELECTED_TEAM_MEMBERS)
      ugsWithoutPreSelected = ugsAsTeamMembers.filterNot(teamMember =>
        maybeSelectedTeamMembers.fold(false)(_.map(_.userId).contains(teamMember.userId)))
      mergedWithPreselected = (ugsWithoutPreSelected.toList ::: maybeSelectedTeamMembers.getOrElse(List.empty).toList)
        .sortBy(_.name)
    } yield mergedWithPreselected
  }

  def getFilteredTeamMembersElseAll(arn: Arn)
                                   (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[Seq[TeamMember]] = {
    val eventualMaybeTeamMembers = sessionCacheService.get(FILTERED_TEAM_MEMBERS)
    eventualMaybeTeamMembers.flatMap { maybeMembers =>
      if (maybeMembers.isDefined) Future.successful(maybeMembers.get)
      else getAllTeamMembers(arn)
    }
  }

  def lookupTeamMember(arn: Arn)(id: String)
                      (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TeamMember]] = {
    for {
      ugsAsTeamMembers <- getFromUgsAsTeamMember(arn)
      maybeTeamMember = ugsAsTeamMembers.find(_.id == id)
    } yield maybeTeamMember
  }

  def lookupTeamMembers(arn: Arn)(ids: Option[List[String]])
                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[TeamMember]] = {
    ids.fold(List.empty[TeamMember].toFuture) {
      ids => getFromUgsAsTeamMember(arn).map(tms => tms.filter(tm => ids.contains(tm.id)).toList)
    }
  }

  def saveSelectedOrFilteredTeamMembers(buttonSelect: String)
                                       (arn: Arn)
                                       (formData: AddTeamMembersToGroup
                                       )(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Unit] = {

    val commonTasks = for {
      teamMembers <- lookupTeamMembers(arn)(formData.members)
      _ <- addSelectablesToSession(teamMembers.map(_.copy(selected = true)))(SELECTED_TEAM_MEMBERS, FILTERED_TEAM_MEMBERS)
    } yield ()

    commonTasks.flatMap(_ =>
      buttonSelect match {
        case CLEAR_BUTTON | CONTINUE_BUTTON =>
          sessionCacheService.delete(TEAM_MEMBER_SEARCH_INPUT)
        case FILTER_BUTTON =>
          if (formData.search.isEmpty) {
            sessionCacheService.delete(TEAM_MEMBER_SEARCH_INPUT)
          } else {
            for {
              _ <- sessionCacheService.put(TEAM_MEMBER_SEARCH_INPUT, formData.search.getOrElse(""))
              _ <- filterTeamMembers(arn)(formData)
            } yield ()
          }
      }
    )
  }

  private def filterTeamMembers(arn: Arn)
                               (formData: AddTeamMembersToGroup)
                               (implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext): Future[Seq[TeamMember]] =
    for {
      teamMembers <- getAllTeamMembers(arn).map(_.toVector)
      maybeNameOrEmail = formData.search
      resultByName = maybeNameOrEmail.fold(teamMembers)(
        searchTerm => teamMembers.filter(_.name.toLowerCase.contains(searchTerm.toLowerCase)))
      resultByEmail = maybeNameOrEmail.fold(teamMembers)(
        searchTerm => teamMembers.filter(_.email.toLowerCase.contains(searchTerm.toLowerCase)))
      consolidatedResult = (resultByName ++ resultByEmail).distinct
      result = consolidatedResult
      _ <- if (result.nonEmpty) sessionCacheService.put(FILTERED_TEAM_MEMBERS, result)
      else Future.successful(())
    } yield result


  private def getFromUgsAsTeamMember(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[TeamMember]] = {
    for {
      ugsUsers <- agentUserClientDetailsConnector.getTeamMembers(arn)
      ugsAsTeamMembers = ugsUsers.map(TeamMember.fromUserDetails)
    } yield ugsAsTeamMembers
  }

}
