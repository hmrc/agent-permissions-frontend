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
import connectors.{AddMembersToAccessGroupRequest, AgentPermissionsConnector, GroupSummary, UpdateAccessGroupRequest}
import forms._
import models.ButtonSelect.{Clear, Filter}
import models.DisplayClient.toEnrolment
import models.TeamMember.toAgentUser
import models.{ButtonSelect, DisplayClient, DisplayGroup, TeamMember}
import play.api.Logging
import play.api.data.FormError
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
import services.{GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model._
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
     val agentPermissionsConnector: AgentPermissionsConnector,
     val sessionCacheRepository: SessionCacheRepository,
     val sessionCacheService: SessionCacheService,
     groupService: GroupService,
     dashboard: dashboard,
     rename_group: rename_group,
     rename_group_complete: rename_group_complete,
     group_not_found: group_not_found,
     confirm_delete_group: confirm_delete_group,
     delete_group_complete: delete_group_complete,
     review_clients_to_add: review_clients_to_add,
     clients_update_complete: clients_update_complete,
     existing_team_members: existing_team_members,
     team_members_list: team_members_list,
     review_team_members_to_add: review_team_members_to_add,
     team_members_update_complete: team_members_update_complete,
     select_groups_for_clients: select_groups_for_clients,
     clients_added_to_groups_complete: clients_added_to_groups_complete
    )
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
        withSessionItem[String](FILTERED_GROUPS_INPUT) { searchTerm =>
          withSessionItem[Boolean](HIDDEN_CLIENTS_EXIST) { maybeHiddenClients =>
            groupService.groupSummaries(arn).map(gs =>
              Ok(
                dashboard(
                  gs,
                  AddClientsToGroupForm.form(),
                  FilterByGroupNameForm.form.fill(searchTerm.getOrElse("")),
                  maybeHiddenClients))
            )
          }
        }
      }
    }
  }

  def submitFilterByGroupName: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        withSessionItem[Boolean](HIDDEN_CLIENTS_EXIST) { maybeHiddenClients =>
          val encoded = request.body.asFormUrlEncoded
          val buttonSelection: ButtonSelect = buttonClickedByUserOnFilterFormPage(encoded)

          buttonSelection match {
            case Clear =>
              val u = for {
                _ <- sessionCacheRepository.deleteFromSession(FILTERED_GROUP_SUMMARIES)
                _ <- sessionCacheRepository.deleteFromSession(FILTERED_GROUPS_INPUT)
              } yield ()
              u.flatMap(_ => Redirect(routes.ManageGroupController.showManageGroups).toFuture)
            case Filter =>
              FilterByGroupNameForm.form
                .bindFromRequest()
                .fold(
                  hasErrors =>
                    groupService.groupSummaries(arn).map(
                      gs => Ok(dashboard(gs, AddClientsToGroupForm.form(), hasErrors, maybeHiddenClients)))
                  ,
                  formData => {
                    groupService.filterByGroupName(formData)(arn)
                      .flatMap(_ => Redirect(routes.ManageGroupController.showManageGroups).toFuture)
                  }
                )
          }
        }
      }
    }
  }

  def showSelectedUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
          selectedClients
            .fold {
              Redirect(routes.ManageGroupController.showManageGroups)
            } { clients => Ok(
                review_clients_to_add(
                  clients = clients,
                  groupName = "",
                  backUrl = Some(s"${routes.ManageGroupController.showManageGroups.url}#unassigned-clients"),
                  continueCall = routes.ManageGroupController.showSelectGroupsForSelectedUnassignedClients
                )
              )
            }
            .toFuture
        }
      }
    }
  }

  def showSelectGroupsForSelectedUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        getGroupSummaries(arn).map(tuple =>
        Ok(select_groups_for_clients(SelectGroupsForm
          .form()
          .fill(SelectGroups(None, None)), tuple._1)))
      }
    }
  }

  def submitSelectGroupsForSelectedUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        SelectGroupsForm.form().bindFromRequest().fold(
          formWithErrors => {
            getGroupSummaries(arn).map( tuple => {
              val clonedForm = formWithErrors.copy(
                errors = Seq(FormError("field-wrapper", formWithErrors.errors(0).message))
              )
              Ok(select_groups_for_clients(clonedForm, tuple._1))
            }
            )
          }, validForm => {
            if(validForm.createNew.isDefined) Redirect(routes.GroupController.showGroupName).toFuture
            else {
              for {
                summaries <- getGroupSummaries(arn)
                groupsToAddTo = summaries._1
                  .filter(groupSummary => validForm.groups.get.contains(groupSummary.groupId))
                _ <- sessionCacheRepository.putSession(GROUPS_FOR_UNASSIGNED_CLIENTS, groupsToAddTo.map(_.groupName))
                selectedClients <- sessionCacheRepository.getFromSession(SELECTED_CLIENTS)
                result <- selectedClients.fold(
                  Redirect(routes.ManageGroupController.showManageGroups).toFuture
                )
                { displayClients =>
                  val enrolments: Set[Enrolment] = displayClients.map(DisplayClient.toEnrolment(_)).toSet
                  Future.sequence( groupsToAddTo.map{ grp =>
                    //TODO: what do we do if 3 out of 4 fail to save?
                    agentPermissionsConnector.addUnassignedMembers (
                      grp.groupId, AddMembersToAccessGroupRequest(clients = Some(enrolments))
                    )
                  }).map{ _ =>
                    Redirect(routes.ManageGroupController.showConfirmClientsAddedToGroups)
                  }
                }
              } yield result

            }
          }
        )
      }
    }
  }

  def showConfirmClientsAddedToGroups: Action[AnyContent] = Action.async {implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        sessionCacheRepository.getFromSession(GROUPS_FOR_UNASSIGNED_CLIENTS).map(maybeGroupNames =>
          maybeGroupNames.fold(
            Redirect(routes.ManageGroupController.showSelectGroupsForSelectedUnassignedClients.url )
          )(groups => {
              sessionCacheRepository.deleteFromSession(SELECTED_CLIENTS)
              Ok(clients_added_to_groups_complete(groups))
            }
          )
        )
      }
    }
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
                    groupSummaries <- agentPermissionsConnector.groupsSummaries(arn)
                    _ <- (if (buttonSelection == ButtonSelect.Continue)
                      sessionCacheService.clearSelectedClients()
                    else ()).toFuture
                    result = if (maybeFilteredClients.isDefined)
                      Ok(dashboard(groupSummaries.getOrElse(Seq.empty[GroupSummary], Seq.empty[DisplayClient]), formWithErrors, FilterByGroupNameForm.form,maybeHiddenClients, true))
                    else
                      Ok(dashboard(groupSummaries.getOrElse(Seq.empty[GroupSummary], Seq.empty[DisplayClient]), formWithErrors, FilterByGroupNameForm.form, maybeHiddenClients, true))
                  } yield result
                },
                formData => {
                  groupService.saveSelectedOrFilteredClients(buttonSelection)(arn)(formData).map(_ =>
                    if(buttonSelection == ButtonSelect.Continue)
                  Redirect(routes.ManageGroupController.showSelectedUnassignedClients)
                    else Redirect(s"${routes.ManageGroupController.showManageGroups}#unassigned-clients")
                  )
                }
              )
          }
        }
      }
    }
  }

  def showGroupClientsUpdatedConfirmation(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) {group: AccessGroup =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        if(selectedClients.isDefined) Ok(clients_update_complete(group.groupName)).toFuture
        else Redirect(routes.ManageGroupClientsController.showManageGroupClients(groupId)).toFuture
      }
    }
  }

  def showExistingGroupTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
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
    }
  }

  def showManageGroupTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId){ group: AccessGroup =>
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
          val backUrl = Some(routes.ManageGroupClientsController.showExistingGroupClients(groupId).url)
          if (filteredTeamMembers.isDefined)
            Ok(
              team_members_list(
                filteredTeamMembers,
                group.groupName,
                maybeHiddenTeamMembers,
                AddTeamMembersToGroupForm.form(),
                formAction = routes.ManageGroupController.submitManageGroupTeamMembers(groupId),
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
                formAction = routes.ManageGroupController.submitManageGroupTeamMembers(groupId),
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
                      formAction = routes.ManageGroupController.submitManageGroupTeamMembers(groupId),
                      backUrl = Some(routes.ManageGroupController.showExistingGroupTeamMembers(groupId).url)
                    )).toFuture
                  else
                    groupService.getTeamMembers(group.arn)().flatMap {
                      maybeTeamMembers =>
                        Ok(team_members_list(
                          maybeTeamMembers,
                          group.groupName,
                          maybeHiddenTeamMembers,
                          formWithErrors,
                          formAction = routes.ManageGroupController.submitManageGroupTeamMembers(groupId),
                          backUrl = Some(routes.ManageGroupController.showExistingGroupTeamMembers(groupId).url)
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
                    Redirect(routes.ManageGroupController.showReviewSelectedTeamMembers(groupId))
                  }
                  else Redirect(routes.ManageGroupController.showManageGroupTeamMembers(groupId))
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
            Redirect(routes.ManageGroupController.showManageGroupTeamMembers(groupId)).toFuture
          ){ members =>
            Ok(
              review_team_members_to_add(
                teamMembers = members,
                groupName = group.groupName,
                continueCall = routes.ManageGroupController.showGroupTeamMembersUpdatedConfirmation(groupId),
                backUrl = Some(routes.ManageGroupController.showManageGroupTeamMembers(groupId).url)
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
        else Redirect(routes.ManageGroupController.showManageGroupTeamMembers(groupId)).toFuture
      }
    }
  }

  def showRenameGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group =>
      Ok(rename_group(GroupNameForm.form().fill(group.groupName), group, groupId)).toFuture
  }
  }

  def submitRenameGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
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
            Redirect(routes.ManageGroupController.showGroupRenamed(groupId)).toFuture
          }
        )
    }
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
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      Ok(confirm_delete_group(YesNoForm.form("group.delete.select.error"), group)).toFuture
    }
  }

  def submitDeleteGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
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
              Redirect(routes.ManageGroupController.showGroupDeleted.url).toFuture
            } else
              Redirect(routes.ManageGroupController.showManageGroups.url).toFuture
          }
        )
    }
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

  private def withGroupForAuthorisedOptedAgent(groupId: String)(body: AccessGroup => Future[Result])
                                              (implicit ec: ExecutionContext, request: MessagesRequest[AnyContent], appConfig: AppConfig): Future[Result] = {
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        agentPermissionsConnector.getGroup(groupId).flatMap(
          _.fold(groupNotFound)(body(_)))
      }
    }
  }

  private def groupNotFound(implicit request: MessagesRequest[AnyContent]): Future[Result] = {
    NotFound(group_not_found()).toFuture
  }

  private def getGroupSummaries(arn: Arn)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[(Seq[GroupSummary], Seq[DisplayClient])] = {
    agentPermissionsConnector.groupsSummaries(arn).map { maybeSummaries =>
      maybeSummaries.getOrElse((Seq.empty[GroupSummary], Seq.empty[DisplayClient]))
    }
  }
}
