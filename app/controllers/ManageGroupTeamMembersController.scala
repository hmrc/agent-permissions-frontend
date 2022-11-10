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

package controllers

import config.AppConfig
import connectors.UpdateAccessGroupRequest
import controllers.action.SessionAction
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
                                                  review_update_team_members: review_update_team_members,
                                                  team_members_update_complete: team_members_update_complete,
                                                )
                                                (implicit val appConfig: AppConfig, ec: ExecutionContext, implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

  with I18nSupport
  with Logging {

  import groupAction._
  import sessionAction.withSessionItem

  private val controller: ReverseManageGroupTeamMembersController = routes.ManageGroupTeamMembersController

  def showExistingGroupTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      val convertedTeamMembers = agentUsersInGroupAsTeamMembers(group)
      groupService.getTeamMembersFromGroup(group.arn)(convertedTeamMembers).map { members =>
        val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
        searchFilter.submit.fold( //i.e. fresh page load
          Ok(existing_team_members(members, SearchAndFilterForm.form(), group))
        )(button => button match {
          case CLEAR_BUTTON =>
            Redirect(controller.showExistingGroupTeamMembers(groupId))
          case FILTER_BUTTON =>
            val lowerCaseSearchTerm = searchFilter.search.getOrElse("").toLowerCase
            val filteredMembers = members
              .filter(tm =>
                tm.name.toLowerCase.contains(lowerCaseSearchTerm) ||
                  tm.email.toLowerCase.contains(lowerCaseSearchTerm)
              )
            val form = SearchAndFilterForm.form().fill(searchFilter)
            Ok(existing_team_members(filteredMembers, form, group))
        }
        )
      }
    }
  }

  def showManageGroupTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      val teamMembers = agentUsersInGroupAsTeamMembers(group)
      val result = for {
        selectedTeamMembers <- groupService.getTeamMembersFromGroup(group.arn)(teamMembers)
        _ <- sessionCacheService.get(SELECTED_TEAM_MEMBERS).map(maybeTeamMembers =>
          if (maybeTeamMembers.isEmpty) {
            sessionCacheService.put[Seq[TeamMember]](SELECTED_TEAM_MEMBERS, selectedTeamMembers)
          }
        )
        filteredTeamMembers <- sessionCacheService.get[Seq[TeamMember]](FILTERED_TEAM_MEMBERS)
        maybeFilterTerm <- sessionCacheService.get[String](TEAM_MEMBER_SEARCH_INPUT)
        teamMembersForArn <- teamMemberService.getAllTeamMembers(group.arn)
      } yield (filteredTeamMembers, teamMembersForArn, maybeFilterTerm)
      result.map {
        result =>
          val filteredTeamMembers = result._1
          val teamMembersSearchTerm = result._3
          val backUrl = Some(routes.ManageGroupClientsController.showExistingGroupClients(groupId).url)
          if (filteredTeamMembers.isDefined)
            Ok(
              team_members_list(
                filteredTeamMembers.getOrElse(Seq.empty),
                group.groupName,
                AddTeamMembersToGroupForm.form().fill(AddTeamMembersToGroup(
                  search = teamMembersSearchTerm,
                  members = None
                )),
                msgKey = "update",
                formAction = controller.submitManageGroupTeamMembers(groupId),
                backUrl = backUrl
              )
            )
          else
            Ok(
              team_members_list(
                result._2,
                group.groupName,
                AddTeamMembersToGroupForm.form().fill(AddTeamMembersToGroup(
                  search = teamMembersSearchTerm,
                  members = None
                )),
                msgKey = "update",
                formAction = controller.submitManageGroupTeamMembers(groupId),
                backUrl = backUrl
              ))
      }
    }
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
                      backUrl = Some(controller.showExistingGroupTeamMembers(groupId).url)
                    ))
                }
            },
            formData => {
              teamMemberService.saveSelectedOrFilteredTeamMembers(formData.submit)(group.arn)(formData).flatMap(_ =>
                if (formData.submit == CONTINUE_BUTTON) {
                  // checks selected from session AFTER saving (removed de-selections)
                  val hasSelected = for {
                    selected <- sessionCacheService.get(SELECTED_TEAM_MEMBERS)
                    // if "empty" returns Some(Vector()) so .nonEmpty on it's own returns true
                  } yield selected.getOrElse(Seq.empty).nonEmpty

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
                            backUrl = Some(controller.showExistingGroupTeamMembers(groupId).url),
                           )
                        )
                    }
                  })
                }
                else Redirect(controller.showManageGroupTeamMembers(groupId)).toFuture
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
          .fold(Redirect(controller.showManageGroupTeamMembers(groupId)).toFuture
          )( members => Ok(review_update_team_members(members, group, YesNoForm.form())).toFuture)
      }
    }
  }

  def submitReviewSelectedTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { maybeSelectedTeamMembers =>
        maybeSelectedTeamMembers
          .fold(
            Redirect(controller.showExistingGroupTeamMembers(groupId)).toFuture
          ) { members => {
            YesNoForm
              .form("group.teamMembers.review.error")
              .bindFromRequest
              .fold(
                formWithErrors => {
                  Ok(review_update_team_members(members, group, formWithErrors)).toFuture
                }, (yes: Boolean) => {
                  if (yes)
                    Redirect(controller.showManageGroupTeamMembers(group._id.toString)).toFuture
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
          else Redirect(controller.showManageGroupTeamMembers(groupId))
        )
      }
    }
  }

  def agentUsersInGroupAsTeamMembers(group: AccessGroup) = {
    group.teamMembers.map { maybeUsers: Set[AgentUser] =>
      maybeUsers.toSeq
        .map(UserDetails.fromAgentUser)
        .map(TeamMember.fromUserDetails)
    }.getOrElse(Seq.empty[TeamMember])
  }

}
