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
import connectors.{AgentPermissionsConnector, UpdateAccessGroupRequest}
import forms._
import models.TeamMember.toAgentUser
import models.{ButtonSelect, SearchFilter, TeamMember}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
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
     mcc: MessagesControllerComponents,
     val agentPermissionsConnector: AgentPermissionsConnector,
     val sessionCacheRepository: SessionCacheRepository,
     val sessionCacheService: SessionCacheService,
     groupService: GroupService,
     teamMemberService: TeamMemberService,
     existing_team_members: existing_team_members,
     team_members_list: team_members_list,
     review_team_members_to_add: review_team_members_to_add,
     team_members_update_complete: team_members_update_complete,
    )
                                                (implicit val appConfig: AppConfig, ec: ExecutionContext,
    implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

  with GroupsControllerCommon
  with I18nSupport
  with SessionBehaviour
  with Logging {

  import groupAction._

  private val controller: ReverseManageGroupTeamMembersController = routes.ManageGroupTeamMembersController

  def showExistingGroupTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      val teamMembers = teamMembersInGroup(group)
      groupService.getTeamMembersFromGroup(group.arn)(Some(teamMembers)).map { members =>
        val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
        searchFilter.submit.fold(
          //no filter/clear was applied
          Ok(
            existing_team_members(
              teamMembers = members.getOrElse(Seq.empty[TeamMember]),
              form = SearchAndFilterForm.form(),
              group = group,
            )
          )
        )(button => button match {
            case "clear" =>
              Redirect(controller.showExistingGroupTeamMembers(groupId))
            case "filter" =>
              val lowerCaseSearchTerm = searchFilter.search.getOrElse("").toLowerCase
              val filteredMembers = members.getOrElse(Seq.empty)
                .filter(tm =>
                  tm.name.toLowerCase.contains(lowerCaseSearchTerm) ||
                  tm.email.toLowerCase.contains(lowerCaseSearchTerm)
                )
              Ok(
                existing_team_members(
                  teamMembers = filteredMembers,
                  form = SearchAndFilterForm.form().fill(searchFilter),
                  group = group
                )
              )
          }
        )
      }
    }
  }

  def showManageGroupTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId){ group: AccessGroup =>
      val teamMembers = teamMembersInGroup(group)
      val result = for {
        selectedTeamMembers <- groupService.getTeamMembersFromGroup(group.arn)(Some(teamMembers))
        _ <- sessionCacheRepository.putSession[Seq[TeamMember]](SELECTED_TEAM_MEMBERS, selectedTeamMembers.get)
        filteredTeamMembers <- sessionCacheRepository.getFromSession[Seq[TeamMember]](FILTERED_TEAM_MEMBERS)
        maybeHiddenTeamMembers <- sessionCacheRepository.getFromSession[Boolean](HIDDEN_TEAM_MEMBERS_EXIST)
        teamMembersForArn <- teamMemberService.getTeamMembers(group.arn)
      } yield (filteredTeamMembers, maybeHiddenTeamMembers, teamMembersForArn)
      result.map {
        result =>
          val filteredTeamMembers = result._1
          val maybeHiddenTeamMembers = result._2
          val backUrl = Some(routes.ManageGroupClientsController.showExistingGroupClients(groupId).url)
          if (filteredTeamMembers.isDefined)
            Ok(
              team_members_list(
                filteredTeamMembers,
                group.groupName,
                maybeHiddenTeamMembers,
                AddTeamMembersToGroupForm.form(),
                formAction = controller.submitManageGroupTeamMembers(groupId),
                backUrl = backUrl
              )
            )
          else
            Ok(
              team_members_list(
                result._3,
                group.groupName,
                maybeHiddenTeamMembers,
                AddTeamMembersToGroupForm.form(),
                formAction = controller.submitManageGroupTeamMembers(groupId),
                backUrl = backUrl
              ))
      }
    }
  }

  def submitManageGroupTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>

    val encoded = request.body.asFormUrlEncoded
    val buttonSelection: ButtonSelect = buttonClickedByUserOnFilterFormPage(encoded)

    withGroupForAuthorisedOptedAgent(groupId){group: AccessGroup =>
      withSessionItem[Seq[TeamMember]](FILTERED_TEAM_MEMBERS) { maybeFilteredResult =>
        withSessionItem[Boolean](HIDDEN_TEAM_MEMBERS_EXIST) { maybeHiddenTeamMembers =>
          AddTeamMembersToGroupForm
            .form(buttonSelection)
            .bindFromRequest()
            .fold(
              formWithErrors => {
                for {
                  _ <- if (buttonSelection == ButtonSelect.Continue)
                    sessionCacheService.clearSelectedTeamMembers()
                  else ().toFuture
                  result <- if (maybeFilteredResult.isDefined)
                    Ok(team_members_list(
                      maybeFilteredResult,
                      group.groupName,
                      maybeHiddenTeamMembers,
                      formWithErrors,
                      formAction = controller.submitManageGroupTeamMembers(groupId),
                      backUrl = Some(controller.showExistingGroupTeamMembers(groupId).url)
                    )).toFuture
                  else
                    teamMemberService.getTeamMembers(group.arn).map {
                      maybeTeamMembers =>
                        Ok(team_members_list(
                          maybeTeamMembers,
                          group.groupName,
                          maybeHiddenTeamMembers,
                          formWithErrors,
                          formAction = controller.submitManageGroupTeamMembers(groupId),
                          backUrl = Some(controller.showExistingGroupTeamMembers(groupId).url)
                        ))
                    }
                } yield result
              },
              formData => {
                teamMemberService.saveSelectedOrFilteredTeamMembers(buttonSelection)(group.arn)(formData).map(_ =>
                  if (buttonSelection == ButtonSelect.Continue) {
                    for {
                      members <- sessionCacheRepository
                        .getFromSession[Seq[TeamMember]](SELECTED_TEAM_MEMBERS)
                        .map { maybeTeamMembers: Option[Seq[TeamMember]] =>
                          maybeTeamMembers
                            .map(tm => tm.map(toAgentUser))
                            .map(_.toSet)
                        }
                      groupRequest = UpdateAccessGroupRequest(teamMembers = members)
                      updated <- agentPermissionsConnector.updateGroup(groupId, groupRequest)
                    } yield updated
                    sessionCacheRepository.deleteFromSession(FILTERED_TEAM_MEMBERS)
                    sessionCacheRepository.deleteFromSession(HIDDEN_TEAM_MEMBERS_EXIST)
                    Redirect(controller.showReviewSelectedTeamMembers(groupId))
                  }
                  else Redirect(controller.showManageGroupTeamMembers(groupId))
                )
              }
            )
        }
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
              review_team_members_to_add(
                teamMembers = members,
                groupName = group.groupName,
                continueCall = controller.showGroupTeamMembersUpdatedConfirmation(groupId),
                backUrl = Some(controller.showManageGroupTeamMembers(groupId).url)
              )
            ).toFuture
          }
      }
    }
  }

  def showGroupTeamMembersUpdatedConfirmation(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { selectedTeamMembers =>
        if (selectedTeamMembers.isDefined) Ok(team_members_update_complete(group.groupName)).toFuture
        else Redirect(controller.showManageGroupTeamMembers(groupId)).toFuture
      }
    }
  }

  private def teamMembersInGroup(group: AccessGroup) = {
    group.teamMembers.map { maybeUsers: Set[AgentUser] =>
      maybeUsers.toSeq
        .map(UserDetails.fromAgentUser)
        .map(TeamMember.fromUserDetails)
    }.getOrElse(Seq.empty[TeamMember])
  }

}
