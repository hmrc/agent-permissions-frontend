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
import connectors.{UpdateAccessGroupRequest, UpdateTaxServiceGroupRequest}
import controllers.GroupType.{CUSTOM, isCustom}
import controllers.actions.{GroupAction, SessionAction}
import forms._
import models.TeamMember.toAgentUser
import models.{AddTeamMembersToGroup, GroupId, SearchFilter, TeamMember}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{GroupService, SessionCacheService, TeamMemberService}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, PaginatedList, PaginationMetaData}
import uk.gov.hmrc.agents.accessgroups.{AccessGroup, CustomGroup, GroupSummary, TaxGroup}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.create.members.confirm_remove_member
import views.html.groups.manage.members.{existing_group_team_members, review_update_team_members, team_members_update_complete, update_paginated_team_members}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageGroupTeamMembersController @Inject()
(
  groupAction: GroupAction,
  sessionAction: SessionAction,
  mcc: MessagesControllerComponents,
  val sessionCacheService: SessionCacheService,
  groupService: GroupService,
  teamMemberService: TeamMemberService,
  existing_group_team_members: existing_group_team_members,
  confirm_remove_member: confirm_remove_member,
  update_paginated_team_members: update_paginated_team_members,
  review_update_team_members: review_update_team_members,
  team_members_update_complete: team_members_update_complete,
)
(
  implicit val appConfig: AppConfig,
  ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc)

  with I18nSupport
  with Logging {

  import groupAction._
  import sessionAction.withSessionItem

  private val controller: ReverseManageGroupTeamMembersController = routes.ManageGroupTeamMembersController


  def showExistingGroupTeamMembers(groupId: GroupId, groupType: String, page: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withAccessGroupForAuthorisedOptedAgent(groupId, GroupType.isCustom(groupType)) { (group, arn) =>
      val convertedTeamMembers = agentUsersInGroupAsTeamMembers(group)
      groupService
        .getTeamMembersFromGroup(arn)(convertedTeamMembers).map { members =>
        val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
        val paginatedMembers = paginationForMembers(members = members, page = page.getOrElse(1))
        searchFilter.submit.fold( //i.e. fresh page load
          Ok(
            existing_group_team_members(
              paginatedMembers,
              SearchAndFilterForm.form(),
              group = GroupSummary.of(group),
            )
          )
        ) {
          case CLEAR_BUTTON =>
            Redirect(controller.showExistingGroupTeamMembers(groupId, groupType, page))
          case FILTER_BUTTON =>
            val lowerCaseSearchTerm = searchFilter.search.getOrElse("").toLowerCase
            val filteredMembers = members
              .filter(tm =>
                tm.name.toLowerCase.contains(lowerCaseSearchTerm) || tm.email.toLowerCase.contains(lowerCaseSearchTerm)
              )
            val form = SearchAndFilterForm.form().fill(searchFilter)
            val paginatedMembers = paginationForMembers(members = filteredMembers, page = page.getOrElse(1))
            Ok(existing_group_team_members(paginatedMembers, form, GroupSummary.of(group)))
        }
      }
    }
  }

  def showConfirmRemoveTeamMember(groupId: GroupId, groupType: String, memberId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId, isCustom(groupType)) { (summary: GroupSummary, arn: Arn) => {
      teamMemberService
        .lookupTeamMember(arn)(memberId)
        .flatMap(maybeClient =>
          maybeClient.fold(Redirect(controller.showExistingGroupTeamMembers(groupId, groupType, None)).toFuture)(teamMember =>
            sessionCacheService
              .put(MEMBER_TO_REMOVE, teamMember)
              .map(_ =>
                Ok(
                  confirm_remove_member(
                    YesNoForm.form(),
                    summary.groupName,
                    teamMember,
                    backLink = controller.showExistingGroupTeamMembers(groupId, groupType, None),
                    formAction = controller.submitConfirmRemoveTeamMember(groupId, groupType)
                  )
                )
              )
          )
        )
    }
    }
  }

  def submitConfirmRemoveTeamMember(groupId: GroupId, groupType: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId, isCustom(groupType)) { (group: GroupSummary, _: Arn) => {
      withSessionItem[TeamMember](MEMBER_TO_REMOVE) { maybeMember =>
        maybeMember.fold(
          Redirect(controller.showExistingGroupTeamMembers(group.groupId, CUSTOM, None)).toFuture
        )(teamMemberToRemove =>
          YesNoForm
            .form("group.member.remove.error")
            .bindFromRequest
            .fold(
              formWithErrors => {
                Ok(
                  confirm_remove_member(
                    formWithErrors,
                    group.groupName,
                    teamMemberToRemove,
                    backLink = controller.showExistingGroupTeamMembers(groupId, groupType, None),
                    formAction = controller.submitConfirmRemoveTeamMember(groupId, groupType)
                  )
                ).toFuture
              }, (yes: Boolean) => {
                if (yes) {
                  groupService
                    .removeTeamMemberFromGroup(groupId, teamMemberToRemove.userId.get, isCustom(groupType))
                    .map(_ => Redirect(controller.showExistingGroupTeamMembers(groupId, groupType, None)))
                }
                else Redirect(controller.showExistingGroupTeamMembers(groupId, groupType, None)).toFuture
              }
            )
        )
      }
    }
    }
  }

  def showManageGroupTeamMembers(groupId: GroupId, groupType: String, page: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withAccessGroupForAuthorisedOptedAgent(groupId, isCustom(groupType)) { (group, _) =>
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
        Ok(
          update_paginated_team_members(
            result._1.pageContent,
            GroupSummary.of(group),
            AddTeamMembersToGroupForm.form().fill(
              AddTeamMembersToGroup(search = teamMembersSearchTerm, members = None)
            ),
            msgKey = "update",
            Option(result._1.paginationMetaData)
          )
        )
      }
    }
  }

  def submitManageGroupTeamMembers(groupId: GroupId, groupType: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId, isCustom(groupType)) { (group, arn) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { maybeSelected =>
        val hasPreSelected = maybeSelected.getOrElse(Seq.empty).nonEmpty
        AddTeamMembersToGroupForm
          .form(hasPreSelected)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              // render page with empty selection error
              teamMemberService
                .getPageOfTeamMembers(arn)(1,10)
                .map(paginatedList =>
                  Ok(
                    update_paginated_team_members(
                      paginatedList.pageContent,
                      group,
                      formWithErrors,
                      msgKey = "update",
                      paginationMetaData = Some(paginatedList.paginationMetaData)

                    )
                  )
                )
            },
            formData => {
              teamMemberService
                .savePageOfTeamMembers(formData)
                .flatMap(nowSelectedMembers => {
                if (formData.submit == CONTINUE_BUTTON) {
                    // check selected there are still selections after saving
                    if (nowSelectedMembers.nonEmpty) {
                      Redirect(controller.showReviewSelectedTeamMembers(groupId, groupType, None)).toFuture
                    } else {
                      // render page with empty selection error
                      teamMemberService
                        .getPageOfTeamMembers(arn)(1, 10)
                        .map(paginatedList =>
                        Ok(
                            update_paginated_team_members(
                              paginatedList.pageContent,
                              group,
                              form = AddTeamMembersToGroupForm
                                .form()
                                .fill(AddTeamMembersToGroup(search = formData.search))
                                .withError("members", "error.select-members.empty"),
                              msgKey = "update",
                              paginationMetaData = Some(paginatedList.paginationMetaData)

                            )
                          )
                        )
                    }
                } else if (formData.submit.startsWith(PAGINATION_BUTTON)) {
                  val pageToShow = formData.submit.replace(s"${PAGINATION_BUTTON}_", "").toInt
                  Redirect(controller.showManageGroupTeamMembers(groupId, groupType, Option(pageToShow))).toFuture
                }
                else Redirect(controller.showManageGroupTeamMembers(groupId, groupType, None)).toFuture
                }

              )
            }
          )
      }
    }
  }

  def showReviewSelectedTeamMembers(groupId: GroupId, groupType: String, page: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId, isCustom(groupType)) { (group, _) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { selectedMembers =>
        selectedMembers
          .fold(Redirect(controller.showManageGroupTeamMembers(groupId, groupType, None)).toFuture
          )(members => {
            Ok(
              review_update_team_members(
                paginationForMembers(members, 10, page.getOrElse(1)),
                group,
                YesNoForm.form()
              )
            ).toFuture
          }
          )
      }
    }
  }

  def submitReviewSelectedTeamMembers(groupId: GroupId, groupType: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId, isCustom(groupType)) { (group, _) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { maybeSelectedTeamMembers =>
        maybeSelectedTeamMembers
          .fold(
            Redirect(controller.showExistingGroupTeamMembers(groupId, groupType, None)).toFuture
          ) { members => {
            YesNoForm
              .form("group.teamMembers.review.error")
              .bindFromRequest
              .fold(
                formWithErrors => {
                  Ok(
                    review_update_team_members(
                      paginationForMembers(members),
                      group,
                      formWithErrors
                    )
                  ).toFuture
                }, (yes: Boolean) => {
                  if (yes)
                    Redirect(controller.showManageGroupTeamMembers(group.groupId, groupType, None)).toFuture
                  else {
                    val selectedMembers = Some(members.map(tm => toAgentUser(tm)).toSet)
                    if (group.isTaxGroup) {
                      val groupRequest = UpdateTaxServiceGroupRequest(teamMembers = selectedMembers)
                      taxGroupService.updateGroup(groupId, groupRequest).map(_ =>
                        Redirect(controller.showGroupTeamMembersUpdatedConfirmation(groupId, groupType))
                      )
                    } else {
                      val groupRequest = UpdateAccessGroupRequest(teamMembers = selectedMembers)
                      groupService.updateGroup(groupId, groupRequest).map(_ =>
                        Redirect(controller.showGroupTeamMembersUpdatedConfirmation(groupId, groupType))
                      )
                    }
                  }
                }
              )
          }
          }
      }
    }
  }

  def showGroupTeamMembersUpdatedConfirmation(groupId: GroupId, groupType: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId, isCustom(groupType)) { (group, _) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { selectedTeamMembers =>
        sessionCacheService.delete(SELECTED_TEAM_MEMBERS).map(_ =>
          if (selectedTeamMembers.isDefined)
            Ok(team_members_update_complete(group.groupName))
          else
            Redirect(controller.showManageGroupTeamMembers(groupId, groupType, None))
        )
      }
    }
  }

  def agentUsersInGroupAsTeamMembers(group: AccessGroup): Seq[TeamMember] = (group match {
    case cg: CustomGroup => cg.teamMembers
    case tg: TaxGroup => tg.teamMembers
  }).toSeq.map(au => TeamMember(
    name = au.name,
    email = "",
    userId = Some(au.id),
    credentialRole = None
  ))

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
    PaginatedList[TeamMember](currentPageOfMembers, meta)
  }

}
