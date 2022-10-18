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
import controllers.{FILTERED_TEAM_MEMBERS, HIDDEN_TEAM_MEMBERS_EXIST, SELECTED_TEAM_MEMBERS, TEAM_MEMBER_SEARCH_INPUT, ToFuture, selectingTeamMemberKeys}
import models.{AddTeamMembersToGroup, TeamMember}
import play.api.mvc.Request
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TeamMemberServiceImpl])
trait TeamMemberService {

  def getAllTeamMembers(arn: Arn)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[Option[Seq[TeamMember]]]

  def getTeamMembers(arn: Arn)(implicit hc: HeaderCarrier,
                               ec: ExecutionContext, request: Request[_]): Future[Option[Seq[TeamMember]]]

  def lookupTeamMember(arn: Arn)(id: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TeamMember]]

  def lookupTeamMembers(arn: Arn)(ids: Option[List[String]]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[List[TeamMember]]]

  def saveSelectedOrFilteredTeamMembers(buttonSelect: String)
                                       (arn: Arn)
                                       (formData: AddTeamMembersToGroup
                                       )(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Unit]


}


@Singleton
class TeamMemberServiceImpl @Inject()(
                                       agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
                                       val sessionCacheRepository: SessionCacheRepository
                                     ) extends TeamMemberService with GroupMemberOps {

  // returns team members from agent-user-client-details, selecting previously selected team members
  def getAllTeamMembers(arn: Arn)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[Option[Seq[TeamMember]]] = {
    for {
      ugsAsTeamMembers <- getFromUgsAsTeamMember(arn)
      maybeSelectedTeamMembers <- sessionCacheRepository
        .getFromSession[Seq[TeamMember]](SELECTED_TEAM_MEMBERS)
      ugsWithoutPreSelected = ugsAsTeamMembers.map(
        teamMembers =>
          teamMembers.filterNot(teamMember =>
            maybeSelectedTeamMembers.fold(false)(
              _.map(_.userId).contains(teamMember.userId))))
      mergedWithPreselected = ugsWithoutPreSelected
        .map(_.toList ::: maybeSelectedTeamMembers.getOrElse(List.empty).toList)
        .map(_.sortBy(_.name))
    } yield mergedWithPreselected
  }

  // returns team members from agent-user-client-details OR a filtered list, selecting previously selected team members
  def getTeamMembers(arn: Arn)
                    (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[Option[Seq[TeamMember]]] = {
    val fromUgs = for {
      ugsAsTeamMembers <- getFromUgsAsTeamMember(arn)
      maybeSelectedTeamMembers <- sessionCacheRepository
        .getFromSession[Seq[TeamMember]](SELECTED_TEAM_MEMBERS)
      ugsWithoutPreSelected = ugsAsTeamMembers.map(
        teamMembers =>
          teamMembers.filterNot(teamMember =>
            maybeSelectedTeamMembers.fold(false)(
              _.map(_.userId).contains(teamMember.userId))))
      mergedWithPreselected = ugsWithoutPreSelected
        .map(_.toList ::: maybeSelectedTeamMembers.getOrElse(List.empty).toList)
        .map(_.sortBy(_.name))
    } yield mergedWithPreselected

    for {
      filtered <- sessionCacheRepository.getFromSession(FILTERED_TEAM_MEMBERS)
      ugs <- fromUgs
    } yield filtered.orElse(ugs)
  }

  def lookupTeamMember(arn: Arn)(id: String)
                      (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TeamMember]] = {
    for {
      ugsAsTeamMembers <- getFromUgsAsTeamMember(arn)
      maybeTeamMember = ugsAsTeamMembers.flatMap(clients => clients.find(_.id == id))
    } yield maybeTeamMember
  }

  def lookupTeamMembers(arn: Arn)(ids: Option[List[String]])
                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[List[TeamMember]]] = {
    ids.fold(Option.empty[List[TeamMember]].toFuture) {
      ids =>
        getFromUgsAsTeamMember(arn)
          .map(_.map(teamMembers => ids
            .flatMap(id => teamMembers.find(_.id == id))))
    }
  }

  def saveSelectedOrFilteredTeamMembers(buttonSelect: String)
                                       (arn: Arn)
                                       (formData: AddTeamMembersToGroup
                                       )(implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Unit] = {

    buttonSelect match {
      case "clear" =>
        for {
          teamMembers <- lookupTeamMembers(arn)(formData.members)
          _ <- addSelectablesToSession(
            teamMembers.map(_.map(_.copy(selected = true))))(
            SELECTED_TEAM_MEMBERS,
            FILTERED_TEAM_MEMBERS
          )
          _ <- Future.traverse(selectingTeamMemberKeys)(key => sessionCacheRepository.deleteFromSession(key))
        } yield ()

      case "continue" =>
        for {
          teamMembers <- lookupTeamMembers(arn)(formData.members)
          _ <- addSelectablesToSession(
            teamMembers.map(_.map(_.copy(selected = true))))(
            SELECTED_TEAM_MEMBERS,
            FILTERED_TEAM_MEMBERS
          )
          _ <- Future.traverse(selectingTeamMemberKeys)(key => sessionCacheRepository.deleteFromSession(key))
        } yield ()

      case "filter" =>
        if (formData.search.isEmpty) {
          Future.successful(())
        } else {
          for {
            teamMembers <- lookupTeamMembers(arn)(formData.members)
            _ <- addSelectablesToSession(
              teamMembers.map(_.map(_.copy(selected = true))))(
              SELECTED_TEAM_MEMBERS,
              FILTERED_TEAM_MEMBERS
            )
            _ <- sessionCacheRepository.putSession(TEAM_MEMBER_SEARCH_INPUT, formData.search.getOrElse(""))
            _ <- filterTeamMembers(arn)(formData)
          } yield ()
        }
    }
  }

  private def filterTeamMembers(arn: Arn)(
    formData: AddTeamMembersToGroup
  )(implicit hc: HeaderCarrier,
    request: Request[Any],
    ec: ExecutionContext): Future[Option[Seq[TeamMember]]] =
    for {
      teamMembers <- getAllTeamMembers(arn)
        .map(_.map(_.toVector))
      maybeNameOrEmail = formData.search
      resultByName = maybeNameOrEmail.fold(teamMembers)(
        term =>
          teamMembers.map(
            _.filter(_.name.toLowerCase.contains(term.toLowerCase))))
      resultByEmail = maybeNameOrEmail.fold(teamMembers)(
        term =>
          teamMembers.map(
            _.filter(_.email.toLowerCase.contains(term.toLowerCase))))
      consolidatedResult = resultByName
        .map(_ ++ resultByEmail.getOrElse(Vector.empty))
        .map(_.distinct)
      result = consolidatedResult.map(_.toVector)
      _ <- result match {
        case Some(filteredResult) => sessionCacheRepository.putSession(FILTERED_TEAM_MEMBERS, filteredResult)
        case _ => Future.successful(())
      }
      hiddenTeamMembers = teamMembers.map(
        _.filter(_.selected) diff result
          .map(_.filter(_.selected))
          .getOrElse(Vector.empty)
      )
      _ <- hiddenTeamMembers match {
        case Some(hidden) if hidden.nonEmpty => sessionCacheRepository.putSession(HIDDEN_TEAM_MEMBERS_EXIST, true)
        case _ => Future.successful(())
      }
    } yield result


  private def getFromUgsAsTeamMember(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[TeamMember]]] = {
    for {
      ugsUsers <- agentUserClientDetailsConnector.getTeamMembers(arn)
      ugsAsTeamMembers = ugsUsers.map(list =>
        list.map(TeamMember.fromUserDetails))
    } yield ugsAsTeamMembers
  }

}
