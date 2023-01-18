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

package controllers

import config.AppConfig
import connectors.UpdateAccessGroupRequest
import controllers.actions.{GroupAction, SessionAction}
import forms._
import models.TeamMember.toAgentUser
import models.{AddTeamMembersToGroup, SearchFilter, TeamMember}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{GroupService, SessionCacheService, TeamMemberService}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups._
import views.html.groups.manage._
import views.html.groups.manage.members.{existing_team_members, team_members_update_complete, update_paginated_team_members}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageGroupTeamMembersController @Inject()(
                                                  groupAction: GroupAction,
                                                  sessionAction: SessionAction,
                                                  mcc: MessagesControllerComponents,
                                                  val sessionCacheService: SessionCacheService,
                                                  groupService: GroupService,
                                                  teamMemberService: TeamMemberService,
                                                  existing_team_members: existing_team_members,
                                                  team_members_list: team_members_list,
                                                  update_paginated_team_members: update_paginated_team_members,
                                                  review_update_team_members: review_update_team_members,
                                                  team_members_update_complete: team_members_update_complete,
                                                )
                                                (implicit val appConfig: AppConfig, ec: ExecutionContext, implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

  with I18nSupport
  with Logging {

  import groupAction._
  import sessionAction.withSessionItem

  private val controller: ReverseManageGroupTeamMembersController = routes.ManageGroupTeamMembersController

  def showExistingGroupTeamMembers(groupId: String, page: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      val convertedTeamMembers = agentUsersInGroupAsTeamMembers(group)
      groupService.getTeamMembersFromGroup(group.arn)(convertedTeamMembers).map { members =>
        val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
        val paginatedMembers = paginationForMembers(members = members, page = page.getOrElse(1))
        searchFilter.submit.fold( //i.e. fresh page load
          Ok(
            existing_team_members(
              paginatedMembers._1,
              SearchAndFilterForm.form(),
              group,
              paginatedMembers._2
            )
          )
        ) {
          case CLEAR_BUTTON =>
            Redirect(controller.showExistingGroupTeamMembers(groupId, page))
          case FILTER_BUTTON =>
            val lowerCaseSearchTerm = searchFilter.search.getOrElse("").toLowerCase
            val filteredMembers = members
              .filter(tm =>
                tm.name.toLowerCase.contains(lowerCaseSearchTerm) || tm.email.toLowerCase.contains(lowerCaseSearchTerm)
              )
            val form = SearchAndFilterForm.form().fill(searchFilter)
            val paginatedMembers = paginationForMembers(members = filteredMembers, page = page.getOrElse(1))
            Ok(existing_team_members(paginatedMembers._1, form, group, paginatedMembers._2))
        }
      }
    }
  }

  def showManageGroupTeamMembers(groupId: String, page: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      val teamMembers = agentUsersInGroupAsTeamMembers(group)
      val result = for {
        selectedTeamMembers <- groupService.getTeamMembersFromGroup(group.arn)(teamMembers)
        _ <- sessionCacheService.get(SELECTED_TEAM_MEMBERS).map(maybeTeamMembers => {
          if (maybeTeamMembers.isEmpty) {
            sessionCacheService.put[Seq[TeamMember]](SELECTED_TEAM_MEMBERS, selectedTeamMembers)
          }
        }
        )
        maybeFilterTerm <- sessionCacheService.get[String](TEAM_MEMBER_SEARCH_INPUT)
        pageMembersForArn <- teamMemberService.getPageOfTeamMembers(group.arn)(page.getOrElse(1), 10)
      } yield (pageMembersForArn: PaginatedList[TeamMember], maybeFilterTerm)
      result.map { result =>
        val teamMembersSearchTerm = result._2
        val backUrl = Some(controller.showExistingGroupTeamMembers(groupId, None).url)
        Ok(
          update_paginated_team_members(
            result._1.pageContent,
            group,
            AddTeamMembersToGroupForm.form().fill(
              AddTeamMembersToGroup(search = teamMembersSearchTerm, members = None)
            ),
            msgKey = "update",
            formAction = controller.submitManageGroupTeamMembers(groupId),
            backUrl = backUrl,
            Option(result._1.paginationMetaData)
          )
        )
      }
    }
  }

  private def paginationForMembers(members: Seq[TeamMember], pageSize: Int = 10, page: Int = 1) = {
    val firstMemberInPage = (page - 1) * pageSize
    val lastMemberInPage = page * pageSize
    val currentPageOfMembers = members.slice(firstMemberInPage, lastMemberInPage)
    val numPages = Math.ceil(members.length.toDouble / pageSize.toDouble).toInt
    val meta = PaginationMetaData(
      lastPage = page == numPages,
      firstPage = page == 1,
      totalSize = members.length,
      totalPages = numPages,
      pageSize = pageSize,
      currentPageNumber = page,
      currentPageSize = currentPageOfMembers.length
    )
    (currentPageOfMembers, meta)
  }

  def submitManageGroupTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { maybeSelected =>
        val hasPreSelected = maybeSelected.getOrElse(Seq.empty).nonEmpty
        AddTeamMembersToGroupForm
          .form(hasPreSelected)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              teamMemberService.getFilteredTeamMembersElseAll(group.arn).map {
                maybeTeamMembers =>
                  Ok(team_members_list(
                    maybeTeamMembers,
                    group.groupName,
                    formWithErrors,
                    formAction = controller.submitManageGroupTeamMembers(groupId),
                    backUrl = Some(controller.showExistingGroupTeamMembers(groupId, None).url)
                  ))
              }
            },
            formData => {
              teamMemberService
                .savePageOfTeamMembers(formData).flatMap(_ =>
                if (formData.submit == CONTINUE_BUTTON) {
                  // checks selected from session AFTER saving (removed de-selections)
                  val hasSelected = for {
                    selected <- sessionCacheService.get(SELECTED_TEAM_MEMBERS)
                    // if "empty" returns Some(Vector()) so .nonEmpty on it's own returns true
                  } yield selected.isDefined

                  hasSelected.flatMap(selectedNotEmpty => {
                    if (selectedNotEmpty) {
                      Redirect(controller.showReviewSelectedTeamMembers(groupId)).toFuture
                    } else { // render page with empty error
                      for {
                        teamMembers <- teamMemberService.getAllTeamMembers(group.arn)
                      } yield
                        Ok(
                          team_members_list(
                            teamMembers,
                            group.groupName,
                            AddTeamMembersToGroupForm.form().withError("members", "error.select-members.empty"),
                            formAction = controller.submitManageGroupTeamMembers(groupId),
                            backUrl = Some(controller.showExistingGroupTeamMembers(groupId, None).url),
                          )
                        )
                    }
                  })
                } else if (formData.submit.startsWith(PAGINATION_BUTTON)) {
                  val pageToShow = formData.submit.replace(s"${PAGINATION_BUTTON}_", "").toInt
                  Redirect(controller.showManageGroupTeamMembers(groupId, Option(pageToShow))).toFuture
                }
                else Redirect(controller.showManageGroupTeamMembers(groupId, None)).toFuture
              )
            }
          )
      }
    }
  }

  def showReviewSelectedTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { selectedMembers =>
        selectedMembers
          .fold(Redirect(controller.showManageGroupTeamMembers(groupId, None)).toFuture
          )(members => Ok(review_update_team_members(members, group, YesNoForm.form())).toFuture)
      }
    }
  }

  def submitReviewSelectedTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { maybeSelectedTeamMembers =>
        maybeSelectedTeamMembers
          .fold(
            Redirect(controller.showExistingGroupTeamMembers(groupId, None)).toFuture
          ) { members => {
            YesNoForm
              .form("group.teamMembers.review.error")
              .bindFromRequest
              .fold(
                formWithErrors => {
                  Ok(review_update_team_members(members, group, formWithErrors)).toFuture
                }, (yes: Boolean) => {
                  if (yes)
                    Redirect(controller.showManageGroupTeamMembers(group._id.toString, None)).toFuture
                  else {
                    val selectedMembers = Some(members.map(tm => toAgentUser(tm)).toSet)
                    val groupRequest = UpdateAccessGroupRequest(teamMembers = selectedMembers)
                    groupService.updateGroup(groupId, groupRequest).map(_ =>
                      Redirect(controller.showGroupTeamMembersUpdatedConfirmation(groupId))
                    )
                  }
                }
              )
          }
          }
      }
    }
  }

  def showGroupTeamMembersUpdatedConfirmation(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { selectedTeamMembers =>
        sessionCacheService.delete(SELECTED_TEAM_MEMBERS).map(_ =>
          if (selectedTeamMembers.isDefined) Ok(team_members_update_complete(group.groupName))
          else Redirect(controller.showManageGroupTeamMembers(groupId, None))
        )
      }
    }
  }

  def agentUsersInGroupAsTeamMembers(group: AccessGroup): Seq[TeamMember] = {
    group.teamMembers.map { maybeUsers: Set[AgentUser] =>
      maybeUsers.toSeq
        .map(UserDetails.fromAgentUser)
        .map(TeamMember.fromUserDetails)
    }.getOrElse(Seq.empty[TeamMember])
  }

}
