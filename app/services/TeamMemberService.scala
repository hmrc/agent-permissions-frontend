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

import com.google.inject.ImplementedBy
import connectors.AgentUserClientDetailsConnector
import controllers.{CLEAR_BUTTON, CONTINUE_BUTTON, CURRENT_PAGE_TEAM_MEMBERS, SELECTED_TEAM_MEMBERS, TEAM_MEMBER_SEARCH_INPUT, ToFuture, teamMemberFilteringKeys}
import models.{AddTeamMembersToGroup, TeamMember}
import play.api.libs.json.JsNumber
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

  def lookupTeamMember(arn: Arn)(id: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TeamMember]]

  def lookupTeamMembers(arn: Arn)(ids: Option[List[String]])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[TeamMember]]

}


@Singleton
class TeamMemberServiceImpl @Inject()(
                                       agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
                                       val sessionCacheService: SessionCacheService
                                     ) extends TeamMemberService with GroupMemberOps {


  /*  Regardless of submit (CONTINUE, CLEAR, FILTER, PAGINATION) saves:
  *    - the filter term from the form in session TODO - forgot why we bother to persist this way
  *    - process the selections OR de-selections on a given page and update the SELECTED_TEAM_MEMBERS in session
  *    returns the SELECTED_TEAM_MEMBERS after processing, to enable an .isEmpty check AFTER the form submit
  *
  *   If CONTINUE, deletes the TEAM_MEMBER_SEARCH_INPUT & FILTERED_TEAM_MEMBERS TODO - remove FILTERED_TEAM_MEMBERS if no longer used
  *   If CLEAR, deletes the TEAM_MEMBER_SEARCH_INPUT
  * */
  def savePageOfTeamMembers(formData: AddTeamMembersToGroup)
                           (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[Any]): Future[Seq[TeamMember]] = {

    val teamMembersInSession = for {
      _ <- formData.search.fold(sessionCacheService.delete(TEAM_MEMBER_SEARCH_INPUT))(term => sessionCacheService.put
      (TEAM_MEMBER_SEARCH_INPUT, term).map(_ => ()))
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
    teamMembersInSession.flatMap(_ =>
      formData.submit.trim match {
        case CONTINUE_BUTTON => sessionCacheService.deleteAll(teamMemberFilteringKeys).flatMap(_ => teamMembersInSession)
        case CLEAR_BUTTON => sessionCacheService.delete(TEAM_MEMBER_SEARCH_INPUT).flatMap(_ => teamMembersInSession)
        case _ => teamMembersInSession
      }
    )
  }


  /*  Gets team members from AUCD and processes UserDetails -> TeamMember
  *   If a search term is in session, searches for name or email match
  *   then paginates the results
  *
  *   using the SELECTED_TEAM_MEMBERS, marks Team members on the page as selected and stores the page state
  *   in CURRENT_PAGE_TEAM_MEMBERS so that a comparison can be made during savePageOfTeamMembers
  * */
  def getPageOfTeamMembers(arn: Arn)(page: Int = 1, pageSize: Int = 10)
                          (implicit hc: HeaderCarrier, ec: ExecutionContext, request: Request[_]): Future[PaginatedList[TeamMember]] = {
    for {
      searchTerm <- sessionCacheService.get(TEAM_MEMBER_SEARCH_INPUT)
      allArnMembers <- getTeamMembersFromConnector(arn)
      filteredMembers = searchTerm.fold(allArnMembers)(term => {
        val nameFiltered = allArnMembers.filter(_.name.toLowerCase.contains(term.toLowerCase))
        val emailFiltered = allArnMembers.filter(_.email.toLowerCase.contains(term.toLowerCase))
        (nameFiltered ++ emailFiltered).distinct.sortBy(_.name)
      })
      firstMemberInPage = (page - 1) * pageSize
      lastMemberInPage = page * pageSize
      //because someone might change the filter selection and click last page when there's only 1 page of results.
      pageOfMembers = if(filteredMembers.size > firstMemberInPage){
        filteredMembers.slice(firstMemberInPage, lastMemberInPage)
      }else{
        filteredMembers.slice(0, Math.min(pageSize, filteredMembers.size))
      }
      numPages = Math.ceil(filteredMembers.length.toDouble / pageSize.toDouble).toInt
      maybeSelectedTeamMembers <- sessionCacheService.get[Seq[TeamMember]](SELECTED_TEAM_MEMBERS)
      existingSelectedIds = maybeSelectedTeamMembers.getOrElse(Nil).map(_.id)
      pageOfMembersMarkedSelected = pageOfMembers
        .map(dc => if (existingSelectedIds.contains(dc.id)) dc.copy(selected = true) else dc)
      totalMembersSelected = maybeSelectedTeamMembers.fold(0)(_.length)
      _ <- sessionCacheService.put(CURRENT_PAGE_TEAM_MEMBERS, pageOfMembersMarkedSelected)
    } yield PaginatedList[TeamMember](
      pageContent = pageOfMembersMarkedSelected,
      paginationMetaData = PaginationMetaData(
        page == numPages,
        page == 1,
        filteredMembers.length,
        numPages,
        pageSize,
        page,
        pageOfMembers.length,
        extra = Some(Map("totalSelected" -> JsNumber(totalMembersSelected))) // This extra data is needed to display correct 'selected' count in front-end
      )
    )
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

  private def getTeamMembersFromConnector(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[TeamMember]] = {
    for {
      ugsUsers <- agentUserClientDetailsConnector.getTeamMembers(arn)
      ugsAsTeamMembers = ugsUsers.map(TeamMember.fromUserDetails)
    } yield ugsAsTeamMembers
  }

}
