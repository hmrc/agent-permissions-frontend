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
import controllers.actions.{AuthAction, GroupAction, OptInStatusAction}
import forms._
import models.SearchFilter
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{GroupService, TaxGroupService}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, GroupSummary, TaxGroup}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.manage.delete._
import views.html.groups.manage.manage_existing_groups
import views.html.groups.manage.rename._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageGroupController @Inject()
(
  authAction: AuthAction,
  groupAction: GroupAction,
  mcc: MessagesControllerComponents,
  groupService: GroupService,
  taxGroupService: TaxGroupService,
  optInStatusAction: OptInStatusAction,
  manage_existing_groups: manage_existing_groups,
  rename_group: rename_group,
  rename_group_complete: rename_group_complete,
  confirm_delete_group: confirm_delete_group,
  delete_group_complete: delete_group_complete
)
(
  implicit val appConfig: AppConfig,
  ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc)


    with I18nSupport
    with Logging {

  import authAction._
  import groupAction._
  import optInStatusAction._

  def showManageGroups(page:Option[Int] = None, pageSize: Option[Int] = None) : Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
          val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
          searchFilter.submit.fold({ //i.e. regular page load with no params
            groupService.getPaginatedGroupSummaries(arn)(page.getOrElse(1), pageSize.getOrElse(5)).map { pagination =>
            sessionCacheService.deleteAll(teamMemberFilteringKeys ++ clientFilteringKeys)
            Ok(manage_existing_groups(
              pagination.pageContent,
              SearchAndFilterForm.form(),
              Some(pagination.paginationMetaData)
            ))
            }
          }
          ) { //either the 'filter' button or the 'clear' filter button was clicked
            case FILTER_BUTTON =>
              groupService.getPaginatedGroupSummaries(
                arn,
                searchFilter.search.getOrElse("")
              )(page.getOrElse(1), pageSize.getOrElse(5)).map { pagination =>
                Ok(manage_existing_groups(
                  pagination.pageContent,
                  SearchAndFilterForm.form().fill(searchFilter),
                  Some(pagination.paginationMetaData)
                ))
              }
            case CLEAR_BUTTON =>
              Redirect(routes.ManageGroupController.showManageGroups(None, None)).toFuture
          }
      }
    }
  }

  def showRenameGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary, arn) =>
      Ok(rename_group(GroupNameForm.form().fill(summary.groupName), summary, groupId, isCustom = true)).toFuture
    }
  }

  def showRenameTaxGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withTaxGroupForAuthorisedOptedAgent(groupId) { group: TaxGroup =>
      Ok(rename_group(GroupNameForm.form().fill(group.groupName), GroupSummary.fromAccessGroup(group), groupId, isCustom = false)).toFuture
    }
  }

  def submitRenameGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary: GroupSummary, arn: Arn) =>
      GroupNameForm
        .form()
        .bindFromRequest
        .fold(
          formWithErrors => Ok(rename_group(formWithErrors, summary, groupId, isCustom = true)).toFuture,
          (newName: String) => {
            for {
              _ <- sessionCacheService.put[String](GROUP_RENAMED_FROM, summary.groupName)
              patchRequestBody = UpdateAccessGroupRequest(groupName = Some(newName))
              _ <- groupService.updateGroup(groupId, patchRequestBody)
            } yield Redirect(routes.ManageGroupController.showGroupRenamed(groupId))
          }
        )
    }
  }

  def submitRenameTaxGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withTaxGroupForAuthorisedOptedAgent(groupId) { group: TaxGroup =>
      GroupNameForm
        .form()
        .bindFromRequest
        .fold(
          formWithErrors => Ok(rename_group(formWithErrors, GroupSummary.fromAccessGroup(group), groupId, isCustom = false)).toFuture,
          (newName: String) => {
            for {
              _ <- sessionCacheService.put[String](GROUP_RENAMED_FROM, group.groupName)
              patchRequestBody = UpdateTaxServiceGroupRequest(groupName = Some(newName))
              _ <- taxGroupService.updateGroup(groupId, patchRequestBody)
            } yield Redirect(routes.ManageGroupController.showTaxGroupRenamed(groupId))
          }
        )
    }
  }

  def showGroupRenamed(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        for {
          group <- groupService.getCustomSummary(groupId)
          oldName <- sessionCacheService.get(GROUP_RENAMED_FROM)
        } yield Ok(rename_group_complete(oldName.get, group.get.groupName))
      }
    }
  }

  def showTaxGroupRenamed(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withTaxGroupForAuthorisedOptedAgent(groupId) { group: TaxGroup =>
        sessionCacheService
          .get(GROUP_RENAMED_FROM)
          .map(oldName =>
            Ok(rename_group_complete(oldName.get, group.groupName))
          )
      }
  }

  def showDeleteGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary: GroupSummary, arn: Arn) =>
      Ok(confirm_delete_group(YesNoForm.form("group.delete.select.error"), summary)).toFuture
    }
  }

  def showDeleteTaxGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withTaxGroupForAuthorisedOptedAgent(groupId) { group: TaxGroup =>
      Ok(confirm_delete_group(YesNoForm.form("group.delete.select.error"), GroupSummary.fromAccessGroup(group), isCustom = false)).toFuture
    }
  }

  def submitDeleteGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary: GroupSummary, arn: Arn) =>
      YesNoForm
        .form("group.delete.select.error")
        .bindFromRequest
        .fold(
          formWithErrors =>
            Ok(confirm_delete_group(formWithErrors, summary)).toFuture,
          (answer: Boolean) => {
            if (answer) {
              for {
                _ <- sessionCacheService.put[String](GROUP_DELETED_NAME, summary.groupName)
                _ <- groupService.deleteGroup(groupId)
              } yield Redirect(routes.ManageGroupController.showGroupDeleted.url)
            } else
              Redirect(routes.ManageGroupController.showManageGroups(None,None).url).toFuture
          }
        )
    }
  }

  def submitDeleteTaxGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withTaxGroupForAuthorisedOptedAgent(groupId) { group: TaxGroup =>
      YesNoForm
        .form("group.delete.select.error")
        .bindFromRequest
        .fold(
          formWithErrors =>
            Ok(confirm_delete_group(formWithErrors, GroupSummary.fromAccessGroup(group))).toFuture,
          (answer: Boolean) => {
            if (answer) {
              for {
                _ <- sessionCacheService.put[String](GROUP_DELETED_NAME, group.groupName)
                _ <- taxGroupService.deleteGroup(groupId)
              } yield Redirect(routes.ManageGroupController.showGroupDeleted.url)
            } else
              Redirect(routes.ManageGroupController.showManageGroups(None, None).url).toFuture
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
