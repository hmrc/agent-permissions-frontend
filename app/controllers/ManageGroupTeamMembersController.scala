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

  import sessionAction.withSessionItem
  import groupAction._

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
    withGroupForAuthorisedOptedAgent(groupId){ group: AccessGroup =>
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
    withGroupForAuthorisedOptedAgent(groupId){group: AccessGroup =>
      withSessionItem[Seq[TeamMember]](FILTERED_TEAM_MEMBERS) { maybeFilteredResult =>
          AddTeamMembersToGroupForm
            .form()
            .bindFromRequest()
            .fold(
              formWithErrors => {
                for {
                  _ <- if (CONTINUE_BUTTON == formWithErrors.data.get("submit"))
                    sessionCacheService.clearSelectedTeamMembers()
                  else ().toFuture
                  result <- if (maybeFilteredResult.isDefined)
                    Ok(team_members_list(
                      maybeFilteredResult.getOrElse(Seq.empty),
                      group.groupName,
                      formWithErrors,
                      formAction = controller.submitManageGroupTeamMembers(groupId),
                      backUrl = Some(controller.showExistingGroupTeamMembers(groupId).url)
                    )).toFuture
                  else {
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
                  }
                } yield result
              },
              formData => {
                teamMemberService.saveSelectedOrFilteredTeamMembers(formData.submit)(group.arn)(formData).map(_ =>
                  if (formData.submit == CONTINUE_BUTTON) {
                    for {
                      members <- sessionCacheService.get[Seq[TeamMember]](SELECTED_TEAM_MEMBERS)
                        .map { maybeTeamMembers: Option[Seq[TeamMember]] =>
                          maybeTeamMembers
                            .map(tm => tm.map(toAgentUser))
                            .map(_.toSet)
                        }
                      groupRequest = UpdateAccessGroupRequest(teamMembers = members)
                      updated <- groupService.updateGroup(groupId, groupRequest)
                    } yield updated
                    sessionCacheService.delete(FILTERED_TEAM_MEMBERS)
                    Redirect(controller.showReviewSelectedTeamMembers(groupId))
                  }
                  else Redirect(controller.showManageGroupTeamMembers(groupId))
                )
              }
            )
        }
    }
  }

  def showReviewSelectedTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId){ group: AccessGroup =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { selectedMembers =>
        selectedMembers
          .fold(
            Redirect(controller.showManageGroupTeamMembers(groupId)).toFuture
          ){ members =>
            Ok(
              review_update_team_members(members, group, YesNoForm.form())).toFuture
          }
      }
    }
  }

  def submitReviewSelectedTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId){ group: AccessGroup =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { selectedMembers =>
        selectedMembers
          .fold(
            Redirect(controller.showExistingGroupTeamMembers(groupId)).toFuture
          ){ members =>
            YesNoForm
              .form("group.teamMembers.review.error")
              .bindFromRequest
              .fold(
                formWithErrors =>{
                  Ok(review_update_team_members(members, group, formWithErrors)).toFuture
                }, (yes: Boolean) => {
                  if (yes)
                      Redirect(controller.showManageGroupTeamMembers(group._id.toString)).toFuture
                  else
                    Redirect(controller.showGroupTeamMembersUpdatedConfirmation(groupId)).toFuture
                }
              )
          }
      }
    }
  }

  def showGroupTeamMembersUpdatedConfirmation(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { selectedTeamMembers =>
        sessionCacheService.clearSelectedTeamMembers().map(_ =>
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
