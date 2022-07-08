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
import connectors.{
  AgentPermissionsConnector,
  GroupSummary,
  UpdateAccessGroupRequest
}
import forms.{AddClientsToGroupForm, GroupNameForm, YesNoForm}
import models.DisplayClient.toEnrolment
import models.{ButtonSelect, DisplayClient}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
import services.{GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, Client, Enrolment}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.client_group_list
import views.html.groups.manage._

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
    val client_group_list: client_group_list,
    val groupService: GroupService,
    val agentPermissionsConnector: AgentPermissionsConnector,
    val sessionCacheRepository: SessionCacheRepository,
    val sessionCacheService: SessionCacheService
)(
    implicit val appConfig: AppConfig,
    ec: ExecutionContext,
    implicit override val messagesApi: MessagesApi,
) extends FrontendController(mcc)
    with GroupsControllerCommon
    with I18nSupport
    with SessionBehaviour
    with Logging {

  import authAction._

  def showManageGroups: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        val eventuallySummaries = for {
          response <- agentPermissionsConnector.groupsSummaries(arn)
        } yield response

        eventuallySummaries.map {
          summaries: Option[(Seq[GroupSummary], Seq[DisplayClient])] =>
            Ok(
              dashboard(summaries.getOrElse(
                (Seq.empty[GroupSummary], Seq.empty[DisplayClient]))))
        }
      }
    }
  }

  def showManageGroupClients(groupId: String): Action[AnyContent] =
    Action.async { implicit request =>
      withGroupForAuthorisedOptedAgent(
        groupId,
        (group: AccessGroup) => {
          val result = for {
            _ <- sessionCacheRepository
              .putSession[Seq[DisplayClient]](
                GROUP_CLIENTS_SELECTED,
                group.clients
                  .map { maybeEnrolments: Set[Enrolment] =>
                    maybeEnrolments.toSeq
                      .map(x => Client.fromEnrolment(x))
                      .map(DisplayClient.fromClient(_, true))
                  }
                  .getOrElse(Seq.empty[DisplayClient])
              )
            filteredClients <- sessionCacheRepository
              .getFromSession[Seq[DisplayClient]](FILTERED_CLIENTS)
            maybeHiddenClients <- sessionCacheRepository
              .getFromSession[Boolean](HIDDEN_CLIENTS_EXIST)
            clientsForArn <- groupService.getClients(group.arn)
          } yield (filteredClients, maybeHiddenClients, clientsForArn)
          result.map {
            result =>
              val filteredClients = result._1
              val maybeHiddenClients = result._2
              if (filteredClients.isDefined)
                Ok(
                  client_group_list(
                    filteredClients,
                    group.groupName,
                    maybeHiddenClients,
                    AddClientsToGroupForm.form(),
                    formAction = routes.ManageGroupController
                      .submitManageGroupClients(groupId),
                    backUrl =
                      Some(routes.ManageGroupController.showManageGroups.url)
                  )
                )
              else
                Ok(
                  client_group_list(
                    result._3,
                    group.groupName,
                    maybeHiddenClients,
                    AddClientsToGroupForm.form(),
                    formAction = routes.ManageGroupController
                      .submitManageGroupClients(groupId),
                    backUrl =
                      Some(routes.ManageGroupController.showManageGroups.url)
                  ))
          }
        }
      )
    }

  def submitManageGroupClients(groupId: String): Action[AnyContent] =
    Action.async { implicit request =>
      val buttonSelection: ButtonSelect =
        buttonClickedByUserOnFilterFormPage(request.body.asFormUrlEncoded)

      withGroupForAuthorisedOptedAgent(
        groupId,
        (group: AccessGroup) => {
          sessionCacheRepository
            .getFromSession[Seq[DisplayClient]](FILTERED_CLIENTS)
            .flatMap {
              maybeFilteredResult =>
                sessionCacheRepository
                  .getFromSession[Boolean](HIDDEN_CLIENTS_EXIST)
                  .flatMap {
                    maybeHiddenClients =>
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
                                  formAction = routes.ManageGroupController
                                    .submitManageGroupClients(groupId),
                                  backUrl = Some(
                                    routes.ManageGroupController.showManageGroups.url)
                                )).toFuture
                              else
                                groupService.getClients(group.arn).flatMap {
                                  maybeClients =>
                                    Ok(client_group_list(
                                      maybeClients,
                                      group.groupName,
                                      maybeHiddenClients,
                                      formWithErrors,
                                      formAction = routes.ManageGroupController
                                        .submitManageGroupClients(groupId),
                                      backUrl = Some(
                                        routes.ManageGroupController.showManageGroups.url)
                                    )).toFuture
                                }
                            } yield result
                          },
                          formData => {
                            groupService
                              .processFormDataForClients(buttonSelection)(
                                group.arn)(formData)
                              .map(
                                _ =>
                                  if (buttonSelection == ButtonSelect.Continue) {
                                    for {
                                      enrolments <- sessionCacheRepository
                                        .getFromSession[Seq[DisplayClient]](
                                          GROUP_CLIENTS_SELECTED)
                                        .flatMap {
                                          maybeClients: Option[
                                            Seq[DisplayClient]] =>
                                            Future(
                                              maybeClients
                                                .map(dcs =>
                                                  dcs.map(toEnrolment(_)))
                                                .map(_.toSet)
                                            )
                                        }
                                      groupRequest: UpdateAccessGroupRequest = UpdateAccessGroupRequest(
                                        clients = enrolments)
                                      updated <- agentPermissionsConnector
                                        .updateGroup(groupId, groupRequest)
                                    } yield updated
                                    sessionCacheRepository.deleteFromSession(
                                      FILTERED_CLIENTS)
                                    sessionCacheRepository.deleteFromSession(
                                      HIDDEN_CLIENTS_EXIST)
                                    Redirect(routes.ManageGroupController.showManageGroups)
                                  } else {
                                    Redirect(routes.ManageGroupController
                                      .showManageGroupClients(groupId))
                                }
                              )
                          }
                        )
                  }
            }
        }
      )
    }

  def showManageGroupTeamMembers(groupId: String): Action[AnyContent] =
    Action.async { implicit request =>
      isAuthorisedAgent { arn =>
        isOptedIn(arn) { _ =>
          Ok(s"showManageGroupTeamMembers not yet implemented ${groupId}").toFuture
        }
      }
    }

  def showRenameGroup(groupId: String): Action[AnyContent] = Action.async {
    implicit request =>
      withGroupForAuthorisedOptedAgent(
        groupId,
        group =>
          Ok(
            rename_group(GroupNameForm.form.fill(group.groupName),
                         group,
                         groupId)).toFuture)
  }

  def submitRenameGroup(groupId: String): Action[AnyContent] = Action.async {
    implicit request =>
      withGroupForAuthorisedOptedAgent(
        groupId,
        (group: AccessGroup) =>
          GroupNameForm
            .form()
            .bindFromRequest
            .fold(
              formWithErrors => {
                Ok(rename_group(formWithErrors, group, groupId)).toFuture
              },
              (newName: String) => {
                for {
                  _ <- sessionCacheRepository
                    .putSession[String](GROUP_RENAMED_FROM, group.groupName)
                  patchRequestBody = UpdateAccessGroupRequest(
                    groupName = Some(newName))
                  _ <- agentPermissionsConnector.updateGroup(groupId,
                                                             patchRequestBody)
                } yield ()
                Redirect(routes.ManageGroupController.showGroupRenamed(groupId)).toFuture
              }
          )
      )
  }

  def showGroupRenamed(groupId: String): Action[AnyContent] = Action.async {
    implicit request =>
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

  def showDeleteGroup(groupId: String): Action[AnyContent] = Action.async {
    implicit request =>
      withGroupForAuthorisedOptedAgent(
        groupId,
        (group: AccessGroup) =>
          Ok(
            confirm_delete_group(YesNoForm.form("group.delete.select.error"),
                                 group)).toFuture)
  }

  def submitDeleteGroup(groupId: String): Action[AnyContent] = Action.async {
    implicit request =>
      withGroupForAuthorisedOptedAgent(
        groupId,
        (group: AccessGroup) =>
          YesNoForm
            .form("group.delete.select.error")
            .bindFromRequest
            .fold(
              formWithErrors =>
                Ok(confirm_delete_group(formWithErrors, group)).toFuture,
              (answer: Boolean) => {
                if (answer) {
                  for {
                    _ <- sessionCacheRepository
                      .putSession[String](GROUP_DELETED_NAME, group.groupName)
                    _ <- agentPermissionsConnector.deleteGroup(groupId)
                  } yield ()
                  Redirect(routes.ManageGroupController.showGroupDeleted.url).toFuture
                } else
                  Redirect(routes.ManageGroupController.showManageGroups.url).toFuture
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

  private def withGroupForAuthorisedOptedAgent(
      groupId: String,
      body: AccessGroup => Future[Result])(
      implicit ec: ExecutionContext,
      request: MessagesRequest[AnyContent],
      appConfig: AppConfig): Future[Result] = {
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        agentPermissionsConnector
          .getGroup(groupId)
          .flatMap(maybeGroup =>
            maybeGroup.fold(groupNotFound) { group =>
              body(group)
          })
      }
    }
  }

  private def groupNotFound(
      implicit request: MessagesRequest[AnyContent]): Future[Result] = {
    NotFound(group_not_found()).toFuture
  }

}
