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
import controllers.actions.{AuthAction, GroupAction, OptInStatusAction}
import forms._
import models.SearchFilter
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.GroupService
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
       groupService: GroupService,
       optInStatusAction: OptInStatusAction,
       dashboard: dashboard,
       rename_group: rename_group,
       rename_group_complete: rename_group_complete,
       confirm_delete_group: confirm_delete_group,
       delete_group_complete: delete_group_complete
     )
     (implicit val appConfig: AppConfig, ec: ExecutionContext,
      implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

    with I18nSupport
    with Logging {

  import authAction._
  import groupAction._
  import optInStatusAction._

  def showManageGroups: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        groupService.getGroupSummaries(arn).map { groupSummaries =>
          val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
          searchFilter.submit.fold({ //i.e. regular page load with no params
            sessionCacheService.deleteAll(teamMemberFilteringKeys ++ clientFilteringKeys)
            Ok(dashboard(groupSummaries, SearchAndFilterForm.form()))
          }
          ) { //either the 'filter' button or the 'clear' filter button was clicked
            case FILTER_BUTTON =>
              val searchTerm = searchFilter.search.getOrElse("")
              val filteredGroupSummaries = groupSummaries
                .filter(_.groupName.toLowerCase.contains(searchTerm.toLowerCase))
              Ok(dashboard(filteredGroupSummaries, SearchAndFilterForm.form().fill(searchFilter)))
            case CLEAR_BUTTON =>
              Redirect(routes.ManageGroupController.showManageGroups)
          }
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
              _ <- sessionCacheService.put[String](GROUP_RENAMED_FROM, group.groupName)
              patchRequestBody = UpdateAccessGroupRequest(groupName = Some(newName))
              _ <- groupService.updateGroup(groupId, patchRequestBody)
            } yield Redirect(routes.ManageGroupController.showGroupRenamed(groupId))
          }
        )
    }
  }

  def showGroupRenamed(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        for {
          group <- groupService.getGroup(groupId)
          oldName <- sessionCacheService.get(GROUP_RENAMED_FROM)
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
                _ <- sessionCacheService.put[String](GROUP_DELETED_NAME, group.groupName)
                _ <- groupService.deleteGroup(groupId)
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
        sessionCacheService
          .get(GROUP_DELETED_NAME)
          .map(groupName =>
            Ok(delete_group_complete(groupName.getOrElse("")))
          )
      }
    }
  }

}
