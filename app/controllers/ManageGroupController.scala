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
import models.SearchFilter
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
import services.{GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.manage._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageGroupController @Inject()(
                                       authAction: AuthAction,
                                       groupAction: GroupAction,
                                       mcc: MessagesControllerComponents,
                                       val agentPermissionsConnector: AgentPermissionsConnector,
                                       val sessionCacheRepository: SessionCacheRepository,
                                       val sessionCacheService: SessionCacheService,
                                       groupService: GroupService,
                                       dashboard: dashboard,
                                       rename_group: rename_group,
                                       rename_group_complete: rename_group_complete,
                                       confirm_delete_group: confirm_delete_group,
                                       delete_group_complete: delete_group_complete
                                     )
                                     (implicit val appConfig: AppConfig, ec: ExecutionContext,
                                      implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

  with GroupsControllerCommon
  with I18nSupport
  with SessionBehaviour
  with Logging {

  import authAction._
  import groupAction._

  def showManageGroups: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        agentPermissionsConnector.groupsOnly(arn).map { groupSummaries =>
          val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
          searchFilter.submit.fold( //i.e. regular page load with no params
            Ok(dashboard(groupSummaries, SearchAndFilterForm.form()))
          )(submitButton =>
            //either the 'filter' button or the 'clear' filter button was clicked
            submitButton match {
              case "clear" =>
                Redirect(routes.ManageGroupController.showManageGroups)
              case "filter" =>
                val filteredGroupSummaries = groupSummaries
                  .filter(_.groupName.toLowerCase.contains(searchFilter.search.getOrElse("").toLowerCase))
                Ok(dashboard(filteredGroupSummaries, SearchAndFilterForm.form().fill(searchFilter)))
            }
          )
        }
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
            } yield Redirect(routes.ManageGroupController.showGroupRenamed(groupId))
          }
        )
    }
  }

  def showGroupRenamed(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        for {
          group <- agentPermissionsConnector.getGroup(groupId)
          oldName <- sessionCacheRepository.getFromSession(GROUP_RENAMED_FROM)
        } yield Ok(rename_group_complete(oldName.get, group.get.groupName))
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
              } yield Redirect(routes.ManageGroupController.showGroupDeleted.url)
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

}
