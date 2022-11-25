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
import controllers.{CLEAR_BUTTON, CONTINUE_BUTTON, CURRENT_PAGE_TEAM_MEMBERS, FILTERED_TEAM_MEMBERS, FILTER_BUTTON, SELECTED_TEAM_MEMBERS, TEAM_MEMBER_SEARCH_INPUT, ToFuture, teamMemberFilteringKeys}
import models.{AddTeamMembersToGroup, TeamMember}
import play.api.mvc.Request
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, PaginatedList, PaginationMetaData}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TeamMemberServiceImpl])
trait TeamMemberService {
  def savePageOfTeamMembers(formData: AddTeamMembersToGroup)
                           (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Seq[TeamMember]]

  def getPageOfTeamMembers(arn: Arn)(page: Int = 1, pageSize: Int = 10)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[PaginatedList[TeamMember]]

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


  def savePageOfTeamMembers(formData: AddTeamMembersToGroup)
                           (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Seq[TeamMember]] ={

    val teamMembersInSession = for{
      _ <- formData.search.fold(sessionCacheService.delete(TEAM_MEMBER_SEARCH_INPUT))(term => sessionCacheService.put
      (TEAM_MEMBER_SEARCH_INPUT, term).map(_=> ()))
      existingSelectedTeamMembers <- sessionCacheService.get(SELECTED_TEAM_MEMBERS).map(_.getOrElse(Seq.empty))
      currentPageTeamMembers <- sessionCacheService.get(CURRENT_PAGE_TEAM_MEMBERS).map(_.getOrElse(Seq.empty))
      teamMemberIdsToAdd = formData.members.getOrElse(Seq.empty)
      currentTeamMembersToAddOrKeep = currentPageTeamMembers.filter(cl => teamMemberIdsToAdd.contains(cl.id))
      idsToRemove = currentPageTeamMembers.map(_.id).diff(teamMemberIdsToAdd)
      newSelectedTeamMembers = (existingSelectedTeamMembers ++ currentTeamMembersToAddOrKeep)
        .map(_.copy(selected = true))
        .distinct
        .filterNot(cl => idsToRemove.contains(cl.id))
        .sortBy(_.name)
      _ <- sessionCacheService.put(SELECTED_TEAM_MEMBERS, newSelectedTeamMembers)
    } yield (newSelectedTeamMembers)
    teamMembersInSession.flatMap(_=>
      formData.submit.trim match {
        case CONTINUE_BUTTON => sessionCacheService.deleteAll(teamMemberFilteringKeys).flatMap(_=> teamMembersInSession)
        case CLEAR_BUTTON => sessionCacheService.delete(TEAM_MEMBER_SEARCH_INPUT).flatMap(_=> teamMembersInSession)
        case _ => teamMembersInSession
      }
    )
  }

  def getPageOfTeamMembers(arn: Arn)(page: Int = 1, pageSize: Int = 10)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[PaginatedList[TeamMember]] = {
    for {
      searchTerm <- sessionCacheService.get(TEAM_MEMBER_SEARCH_INPUT)
      allArnMembers <- getTeamMembersFromConnector(arn)
      filteredMembers = searchTerm.fold(allArnMembers)(term => {
        val nameFiltered = allArnMembers.filter(_.name.toLowerCase.contains(term.toLowerCase))
        val emailFiltered = allArnMembers.filter(_.name.toLowerCase.contains(term.toLowerCase))
        (nameFiltered ++ emailFiltered).distinct.sortBy(_.name)
      })
      firstMemberInPage = (page - 1) * pageSize
      lastMemberInPage = page * pageSize
      pageOfMembers = filteredMembers.slice(firstMemberInPage, lastMemberInPage)
      numPages = Math.ceil(filteredMembers.length.toDouble / pageSize.toDouble).toInt
      maybeSelectedTeamMembers <- sessionCacheService.get[Seq[TeamMember]](SELECTED_TEAM_MEMBERS)
      existingSelectedIds = maybeSelectedTeamMembers.getOrElse(Nil).map(_.id)
      pageOfMembersMarkedSelected = pageOfMembers
        .map(dc => if (existingSelectedIds.contains(dc.id)) dc.copy(selected = true) else dc)
      pageOfDisplayTeamMembers = PaginatedList[TeamMember](
        pageContent = pageOfMembersMarkedSelected,
        paginationMetaData =
          PaginationMetaData(page == numPages, page == 1, filteredMembers.length, numPages, pageSize, page, pageOfMembers.length)

      )
      _ <- sessionCacheService.put(CURRENT_PAGE_TEAM_MEMBERS, pageOfMembersMarkedSelected)
    } yield pageOfDisplayTeamMembers
  }

  // returns team members from agent-user-client-details, selecting previously selected team members
  def getAllTeamMembers(arn: Arn)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[Seq[TeamMember]] = {
    for {
      members <- getTeamMembersFromConnector(arn)
      maybeSelectedTeamMembers <- sessionCacheService.get[Seq[TeamMember]](SELECTED_TEAM_MEMBERS)
      membersWithoutPreSelected = members
        .filterNot(teamMember => maybeSelectedTeamMembers
          .fold(false)(_.map(_.userId).contains(teamMember.userId)))
      mergedWithPreselected = (membersWithoutPreSelected.toList ::: maybeSelectedTeamMembers.getOrElse(List.empty).toList)
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
      ugsAsTeamMembers <- getTeamMembersFromConnector(arn)
      maybeTeamMember = ugsAsTeamMembers.find(_.id == id)
    } yield maybeTeamMember
  }

  def lookupTeamMembers(arn: Arn)(ids: Option[List[String]])
                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[TeamMember]] = {
    ids.fold(List.empty[TeamMember].toFuture) {
      ids => getTeamMembersFromConnector(arn).map(tms => tms.filter(tm => ids.contains(tm.id)).toList)
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
          sessionCacheService.deleteAll(teamMemberFilteringKeys)
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


  private def getTeamMembersFromConnector(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[TeamMember]] = {
    for {
      ugsUsers <- agentUserClientDetailsConnector.getTeamMembers(arn)
      ugsAsTeamMembers = ugsUsers.map(TeamMember.fromUserDetails)
    } yield ugsAsTeamMembers
  }

}
