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
import connectors.{AgentPermissionsConnector, GroupSummary, UpdateAccessGroupRequest}
import controllers.routes.ManageGroupController
import forms.{AddClientsToGroupForm, AddTeamMembersToGroupForm, GroupNameForm, YesNoForm}
import models.DisplayClient.toEnrolment
import models.TeamMember.toAgentUser
import models.{ButtonSelect, DisplayClient, DisplayGroup, TeamMember}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
import services.{GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, AgentUser, Arn, UserDetails}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups._
import views.html.groups.manage._
import views.html.groups.unassigned_clients.select_groups_for_clients

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ManageGroupController @Inject()(
     authAction: AuthAction,
     mcc: MessagesControllerComponents,
     dashboard: dashboard,
     rename_group: rename_group,
     rename_group_complete: rename_group_complete,
     group_not_found: group_not_found,
     confirm_delete_group: confirm_delete_group,
     delete_group_complete: delete_group_complete,
     review_clients_to_add: review_clients_to_add,
     client_group_list: client_group_list,
     clients_update_complete: clients_update_complete,
     existing_clients: existing_clients,
     existing_team_members: existing_team_members,
     groupService: GroupService,
     team_members_list: team_members_list,
     review_team_members_to_add: review_team_members_to_add,
     team_members_update_complete: team_members_update_complete,
     select_groups_for_clients: select_groups_for_clients,
     val agentPermissionsConnector: AgentPermissionsConnector,
     val sessionCacheRepository: SessionCacheRepository,
     val sessionCacheService: SessionCacheService)
   (implicit val appConfig: AppConfig, ec: ExecutionContext,
    implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

  with GroupsControllerCommon
  with I18nSupport
  with SessionBehaviour
  with Logging {

  import authAction._

  def showManageGroups: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        getGroupSummaries(arn).map(data =>
          Ok(dashboard(data, AddClientsToGroupForm.form(),Some(false)))
        )
      }
    }
  }

  private def getGroupSummaries(arn: Arn)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[(Seq[GroupSummary], Seq[DisplayClient])] = {

    val eventuallySummaries = for {
      response <- agentPermissionsConnector.groupsSummaries(arn)
    } yield response

    eventuallySummaries.map { maybeSummaries =>
      maybeSummaries.getOrElse((Seq.empty[GroupSummary], Seq.empty[DisplayClient]))
    }
  }

  def showExistingGroupClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId, (group: AccessGroup) =>
      Ok(existing_clients(DisplayGroup.fromAccessGroup(group))).toFuture
    )
  }

  def showManageGroupClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>

    withGroupForAuthorisedOptedAgent(groupId, (group: AccessGroup) => {

      val displayClients = DisplayClient.fromEnrolments(group.clients)

      val result = for {
        _ <- sessionCacheRepository.putSession[Seq[DisplayClient]](SELECTED_CLIENTS, displayClients)
        filteredClients <- sessionCacheRepository.getFromSession[Seq[DisplayClient]](FILTERED_CLIENTS)
        maybeHiddenClients <- sessionCacheRepository.getFromSession[Boolean](HIDDEN_CLIENTS_EXIST)
        clientsForArn <- groupService.getClients(group.arn)
      } yield (filteredClients, maybeHiddenClients, clientsForArn)
      result.map {
        result =>
          val filteredClients = result._1
          val maybeHiddenClients = result._2
          val backUrl = Some(ManageGroupController.showExistingGroupClients(groupId).url)
          if (filteredClients.isDefined)
            Ok(
              client_group_list(
                filteredClients,
                group.groupName,
                maybeHiddenClients,
                AddClientsToGroupForm.form(),
                formAction = ManageGroupController.submitManageGroupClients(groupId),
                backUrl = backUrl
              )
            )
          else
            Ok(
              client_group_list(
                result._3,
                group.groupName,
                maybeHiddenClients,
                AddClientsToGroupForm.form(),
                formAction = ManageGroupController.submitManageGroupClients(groupId),
                backUrl = backUrl
              ))
      }
    }
    )
  }

  def submitManageGroupClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>

    val encoded = request.body.asFormUrlEncoded
    val buttonSelection: ButtonSelect = buttonClickedByUserOnFilterFormPage(encoded)

    withGroupForAuthorisedOptedAgent(groupId, (group: AccessGroup) => {
      withSessionItem[Seq[DisplayClient]](FILTERED_CLIENTS) { maybeFilteredResult =>
        withSessionItem[Boolean](HIDDEN_CLIENTS_EXIST) { maybeHiddenClients =>
          AddClientsToGroupForm
            .form(buttonSelection)
            .bindFromRequest()
            .fold(
              formWithErrors => {
                for {
                  _ <- if (buttonSelection == ButtonSelect.Continue)
                    sessionCacheService.clearSelectedClients()
                  else ().toFuture
                  result <- if (maybeFilteredResult.isDefined)
                    Ok(client_group_list(
                      maybeFilteredResult,
                      group.groupName,
                      maybeHiddenClients,
                      formWithErrors,
                      formAction = ManageGroupController.showManageGroupClients(groupId),
                      backUrl = Some(ManageGroupController.showManageGroups.url)
                    )).toFuture
                  else
                    groupService.getClients(group.arn).flatMap { maybeClients =>
                        Ok(client_group_list(
                          maybeClients,
                          group.groupName,
                          maybeHiddenClients,
                          formWithErrors,
                          formAction = ManageGroupController.showManageGroupClients(groupId),
                          backUrl = Some(ManageGroupController.showManageGroups.url)
                        )).toFuture
                    }
                } yield result
              },
              formData => {
                groupService.saveSelectedOrFilteredClients(buttonSelection)(group.arn)(formData).map(_ =>
                  if (buttonSelection == ButtonSelect.Continue) {
                    for {
                      enrolments <- sessionCacheRepository
                        .getFromSession[Seq[DisplayClient]](SELECTED_CLIENTS)
                        .flatMap { maybeClients: Option[Seq[DisplayClient]] =>
                          maybeClients
                            .map(dcs => dcs.map(toEnrolment(_)))
                            .map(_.toSet)
                            .toFuture
                        }
                      groupRequest = UpdateAccessGroupRequest(clients = enrolments)
                      updated <- agentPermissionsConnector.updateGroup(groupId, groupRequest)
                    } yield updated
                    sessionCacheRepository.deleteFromSession(FILTERED_CLIENTS)
                    sessionCacheRepository.deleteFromSession(HIDDEN_CLIENTS_EXIST)
                    Redirect(ManageGroupController.showReviewSelectedClients(groupId))
                  }
                  else Redirect(ManageGroupController.showManageGroupClients(groupId))
                )
              }
            )
        }
      }
    }
    )
  }

  def showReviewSelectedClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId, (group: AccessGroup) => {
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        selectedClients
          .fold {
            Redirect(ManageGroupController.showManageGroupClients(groupId))
          } { clients =>
            Ok(
              review_clients_to_add(
                clients = clients,
                groupName = group.groupName,
                backUrl = Some(ManageGroupController.showManageGroupClients(groupId).url),
                continueCall = ManageGroupController.showGroupClientsUpdatedConfirmation(groupId)
              )
            )
          }
          .toFuture
      }
    }
    )
  }

  def showSelectedUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
          selectedClients
            .fold {
              Redirect(ManageGroupController.showManageGroups)
            } { clients => Ok(
                review_clients_to_add(
                  clients = clients,
                  groupName = "",
                  backUrl = Some(ManageGroupController.showManageGroups.url + "#unassigned-clients"),
                  continueCall = ManageGroupController.showSelectGroupsForSelectedUnassignedClients
                )
              )
            }
            .toFuture
        }
      }
    }
  }

  def showSelectGroupsForSelectedUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    Ok(select_groups_for_clients(YesNoForm.form())).toFuture
  }

  def submitSelectGroupsForSelectedUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    Ok("whatever").toFuture
  }

  def submitAddUnassignedClients: Action[AnyContent] = Action.async { implicit request =>

    val encoded = request.body.asFormUrlEncoded
    val buttonSelection: ButtonSelect = buttonClickedByUserOnFilterFormPage(encoded)

    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        withSessionItem[Seq[DisplayClient]](FILTERED_CLIENTS) { maybeFilteredClients =>
          withSessionItem[Boolean](HIDDEN_CLIENTS_EXIST) { maybeHiddenClients =>
            AddClientsToGroupForm
              .form(buttonSelection)
              .bindFromRequest()
              .fold(
                formWithErrors => {
                  for {
                    groupSummaries <- getGroupSummaries(arn)
                    _ <- (if (buttonSelection == ButtonSelect.Continue)
                      sessionCacheService.clearSelectedClients()
                    else ()).toFuture
                    result <- if (maybeFilteredClients.isDefined)
                      Ok(dashboard(groupSummaries, formWithErrors, maybeHiddenClients, true)).toFuture
                    else
                      Ok(dashboard(groupSummaries, formWithErrors, maybeHiddenClients, true)).toFuture
                  } yield result
                },
                formData => {
                  Redirect(ManageGroupController.showSelectedUnassignedClients).toFuture
                }
              )
          }
        }
      }
    }
  }

  def showGroupClientsUpdatedConfirmation(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId, (group: AccessGroup) => {
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        selectedClients.fold {
          Redirect(ManageGroupController.showManageGroupClients(groupId))
        } { _ => Ok(clients_update_complete(group.groupName)) }
          .toFuture
      }
    }
    )
  }

  def showExistingGroupTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId, (group: AccessGroup) => {
      val teamMembers = group.teamMembers.map { maybeUsers: Set[AgentUser] =>
        maybeUsers.toSeq
          .map(UserDetails.fromAgentUser)
          .map(TeamMember.fromUserDetails)
      }.getOrElse(Seq.empty[TeamMember])
      groupService.getTeamMembersFromGroup(group.arn)(Some(teamMembers)).flatMap(groupMembers =>
        Ok(
          existing_team_members(
            groupMembers.getOrElse(Seq.empty[TeamMember]),
            group.groupName,
            routes.ManageGroupController.showManageGroupTeamMembers(groupId).url
          )).toFuture
      )
    })
  }

  def showManageGroupTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId, (group: AccessGroup) => {
      val teamMembers = group.teamMembers.map { maybeUsers: Set[AgentUser] =>
        maybeUsers.toSeq
          .map(UserDetails.fromAgentUser)
          .map(TeamMember.fromUserDetails)
      }.getOrElse(Seq.empty[TeamMember])

      val result = for {
        selectedTeamMembers <- groupService.getTeamMembersFromGroup(group.arn)(Some(teamMembers))
        _ <- sessionCacheRepository.putSession[Seq[TeamMember]](SELECTED_TEAM_MEMBERS, selectedTeamMembers.get)
        filteredTeamMembers <- sessionCacheRepository.getFromSession[Seq[TeamMember]](FILTERED_TEAM_MEMBERS)
        maybeHiddenTeamMembers <- sessionCacheRepository.getFromSession[Boolean](HIDDEN_TEAM_MEMBERS_EXIST)
        teamMembersForArn <- groupService.getTeamMembers(group.arn)(selectedTeamMembers)
      } yield (filteredTeamMembers, maybeHiddenTeamMembers, teamMembersForArn)
      result.map {
        result =>
          val filteredTeamMembers = result._1
          val maybeHiddenTeamMembers = result._2
          val backUrl = Some(ManageGroupController.showExistingGroupClients(groupId).url)
          if (filteredTeamMembers.isDefined)
            Ok(
              team_members_list(
                filteredTeamMembers,
                group.groupName,
                maybeHiddenTeamMembers,
                AddTeamMembersToGroupForm.form(),
                formAction = ManageGroupController.submitManageGroupTeamMembers(groupId),
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
                formAction = ManageGroupController.submitManageGroupTeamMembers(groupId),
                backUrl = backUrl
              ))
      }
    }
    )
  }

  def submitManageGroupTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>

    val encoded = request.body.asFormUrlEncoded
    val buttonSelection: ButtonSelect = buttonClickedByUserOnFilterFormPage(encoded)

    withGroupForAuthorisedOptedAgent(groupId, (group: AccessGroup) => {
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
                      formAction = ManageGroupController.submitManageGroupTeamMembers(groupId),
                      backUrl = Some(ManageGroupController.showExistingGroupTeamMembers(groupId).url)
                    )).toFuture
                  else
                    groupService.getTeamMembers(group.arn)().flatMap {
                      maybeTeamMembers =>
                        Ok(team_members_list(
                          maybeTeamMembers,
                          group.groupName,
                          maybeHiddenTeamMembers,
                          formWithErrors,
                          formAction = ManageGroupController.submitManageGroupTeamMembers(groupId),
                          backUrl = Some(ManageGroupController.showExistingGroupTeamMembers(groupId).url)
                        )).toFuture
                    }
                } yield result
              },
              formData => {
                groupService.saveSelectedOrFilteredTeamMembers(buttonSelection)(group.arn)(formData).map(_ =>
                  if (buttonSelection == ButtonSelect.Continue) {
                    for {
                      members <- sessionCacheRepository
                        .getFromSession[Seq[TeamMember]](SELECTED_TEAM_MEMBERS)
                        .flatMap { maybeTeamMembers: Option[Seq[TeamMember]] =>
                          maybeTeamMembers
                            .map(tm => tm.map(toAgentUser))
                            .map(_.toSet)
                            .toFuture
                        }
                      groupRequest = UpdateAccessGroupRequest(teamMembers = members)
                      updated <- agentPermissionsConnector.updateGroup(groupId, groupRequest)
                    } yield updated
                    sessionCacheRepository.deleteFromSession(FILTERED_TEAM_MEMBERS)
                    sessionCacheRepository.deleteFromSession(HIDDEN_TEAM_MEMBERS_EXIST)
                    Redirect(ManageGroupController.showReviewSelectedTeamMembers(groupId))
                  }
                  else Redirect(ManageGroupController.showManageGroupTeamMembers(groupId))
                )
              }
            )
        }
      }
    }
    )
  }

  def showReviewSelectedTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId, (group: AccessGroup) => {
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { selectedMembers =>
        selectedMembers
          .fold {
            Redirect(ManageGroupController.showManageGroupTeamMembers(groupId))
          } { members =>
            Ok(
              review_team_members_to_add(
                teamMembers = members,
                groupName = group.groupName,
                continueCall = ManageGroupController.showGroupTeamMembersUpdatedConfirmation(groupId),
                backUrl = Some(ManageGroupController.showManageGroupTeamMembers(groupId).url)
              )
            )
          }
          .toFuture
      }
    }
    )
  }

  def showGroupTeamMembersUpdatedConfirmation(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId, (group: AccessGroup) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { selectedTeamMembers =>
        selectedTeamMembers.fold {
          Redirect(ManageGroupController.showManageGroupTeamMembers(groupId))
        } { _ => Ok(team_members_update_complete(group.groupName)) }
          .toFuture
      })
  }

  def showRenameGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId, group =>
      Ok(rename_group(GroupNameForm.form().fill(group.groupName), group, groupId)).toFuture)
  }

  def submitRenameGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId, (group: AccessGroup) =>
      GroupNameForm
        .form()
        .bindFromRequest
        .fold(
          formWithErrors => Ok(rename_group(formWithErrors, group, groupId)).toFuture,
          (newName: String) => {
            for {
              _ <- sessionCacheRepository.putSession[String](GROUP_RENAMED_FROM, group.groupName)
              patchRequestBody = UpdateAccessGroupRequest(groupName = Some(newName))
              _ <- agentPermissionsConnector.updateGroup(groupId, patchRequestBody)
            } yield ()
            Redirect(ManageGroupController.showGroupRenamed(groupId)).toFuture
          }
        )
    )
  }

  def showGroupRenamed(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        val result = for {
          group <- agentPermissionsConnector.getGroup(groupId)
          oldName <- sessionCacheRepository.getFromSession(GROUP_RENAMED_FROM)
        } yield (group, oldName)
        result.map(tuple =>
          Ok(rename_group_complete(tuple._2.get, tuple._1.get.groupName)))
      }
    }
  }

  def showDeleteGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId, (group: AccessGroup) =>
      Ok(confirm_delete_group(YesNoForm.form("group.delete.select.error"), group)).toFuture
    )
  }

  def submitDeleteGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId, (group: AccessGroup) =>
      YesNoForm
        .form("group.delete.select.error")
        .bindFromRequest
        .fold(
          formWithErrors =>
            Ok(confirm_delete_group(formWithErrors, group)).toFuture,
          (answer: Boolean) => {
            if (answer) {
              for {
                _ <- sessionCacheRepository.putSession[String](GROUP_DELETED_NAME, group.groupName)
                _ <- agentPermissionsConnector.deleteGroup(groupId)
              } yield ()
              Redirect(ManageGroupController.showGroupDeleted.url).toFuture
            } else
              Redirect(ManageGroupController.showManageGroups.url).toFuture
          }
        )
    )
  }

  def showGroupDeleted: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        sessionCacheRepository
          .getFromSession(GROUP_DELETED_NAME)
          .map(groupName => Ok(delete_group_complete(groupName.getOrElse(""))))
      }
    }
  }

  private def withGroupForAuthorisedOptedAgent(groupId: String, body: AccessGroup => Future[Result])
                                              (implicit ec: ExecutionContext, request: MessagesRequest[AnyContent], appConfig: AppConfig): Future[Result] = {
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        agentPermissionsConnector.getGroup(groupId).flatMap(
          maybeGroup => maybeGroup.fold(groupNotFound)(group => body(group)))
      }
    }
  }

  private def groupNotFound(implicit request: MessagesRequest[AnyContent]): Future[Result] = {
    NotFound(group_not_found()).toFuture
  }

}
