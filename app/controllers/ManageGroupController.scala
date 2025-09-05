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
import models.{GroupId, SearchFilter}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{GroupService, TaxGroupService}
import models.Arn
import models.accessgroups.GroupSummary
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.manage.delete._
import views.html.groups.manage.manage_existing_groups
import views.html.groups.manage.rename._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageGroupController @Inject() (
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
)(
  implicit val appConfig: AppConfig,
  ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc) with I18nSupport with Logging {

  import authAction._
  import groupAction._
  import optInStatusAction._

  val controller: ReverseManageGroupController = controllers.routes.ManageGroupController

  def showManageGroups(page: Option[Int] = None, pageSize: Option[Int] = None): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorisedAgent { arn =>
        isOptedIn(arn) { _ =>
          sessionCacheService
            .deleteAll(managingGroupKeys)
            .flatMap(_ =>
              sessionCacheService
                .get(GROUP_SEARCH_INPUT)
                .flatMap { maybeSearchTerm =>
                  groupService
                    .getPaginatedGroupSummaries(arn, maybeSearchTerm.getOrElse(""))(
                      page.getOrElse(1),
                      pageSize.getOrElse(5)
                    )
                    .map { pagination =>
                      Ok(
                        manage_existing_groups(
                          pagination.pageContent,
                          SearchAndFilterForm.form().fill(SearchFilter(maybeSearchTerm, None, None)),
                          Some(pagination.paginationMetaData)
                        )
                      )
                    }
                }
            )
        }
      }
  }

  def submitManageGroups: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
        searchFilter.submit.fold(
          Redirect(controller.showManageGroups(None, None)).toFuture
        ) {
          case CLEAR_BUTTON =>
            sessionCacheService
              .delete(GROUP_SEARCH_INPUT)
              .map(_ => Redirect(controller.showManageGroups(None, None)))
          case PAGINATION_REGEX(_, pageToShow) =>
            sessionCacheService
              .get(GROUP_SEARCH_INPUT)
              .flatMap { search =>
                if (searchFilter.search == search) {
                  Redirect(controller.showManageGroups(Some(pageToShow.toInt), None)).toFuture
                } else {
                  sessionCacheService
                    .put(GROUP_SEARCH_INPUT, searchFilter.search.getOrElse(""))
                    .map(_ => Redirect(controller.showManageGroups(None, None)))
                }
              }
          case FILTER_BUTTON =>
            sessionCacheService
              .put(GROUP_SEARCH_INPUT, searchFilter.search.getOrElse(""))
              .map(_ => Redirect(controller.showManageGroups(None, None)))
        }
      }
    }
  }

  def showRenameGroup(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary: GroupSummary, _: Arn) =>
      Ok(rename_group(GroupNameForm.form().fill(summary.groupName), summary, groupId, isCustom = true)).toFuture
    }
  }

  def showRenameTaxGroup(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId, isCustom = false) { (summary: GroupSummary, _: Arn) =>
      Ok(rename_group(GroupNameForm.form().fill(summary.groupName), summary, groupId, isCustom = false)).toFuture
    }
  }

  def submitRenameGroup(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary: GroupSummary, _: Arn) =>
      GroupNameForm
        .form()
        .bindFromRequest()
        .fold(
          formWithErrors => Ok(rename_group(formWithErrors, summary, groupId, isCustom = true)).toFuture,
          (newName: String) =>
            for {
              _ <- sessionCacheService.put[String](GROUP_RENAMED_FROM, summary.groupName)
              patchRequestBody = UpdateAccessGroupRequest(groupName = Some(newName))
              _ <- groupService.updateGroup(groupId, patchRequestBody)
            } yield Redirect(routes.ManageGroupController.showGroupRenamed(groupId))
        )
    }
  }

  def submitRenameTaxGroup(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId, isCustom = false) { (summary: GroupSummary, _: Arn) =>
      GroupNameForm
        .form()
        .bindFromRequest()
        .fold(
          formWithErrors => Ok(rename_group(formWithErrors, summary, groupId, isCustom = false)).toFuture,
          (newName: String) =>
            for {
              _ <- sessionCacheService.put[String](GROUP_RENAMED_FROM, summary.groupName)
              patchRequestBody = UpdateTaxServiceGroupRequest(groupName = Some(newName))
              _ <- taxGroupService.updateGroup(groupId, patchRequestBody)
            } yield Redirect(routes.ManageGroupController.showTaxGroupRenamed(groupId))
        )
    }
  }

  def showGroupRenamed(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary: GroupSummary, _: Arn) =>
      sessionCacheService
        .get(GROUP_RENAMED_FROM)
        .map(oldName => Ok(rename_group_complete(oldName.get, summary.groupName)))
    }
  }

  def showTaxGroupRenamed(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId, isCustom = false) { (summary: GroupSummary, _: Arn) =>
      sessionCacheService
        .get(GROUP_RENAMED_FROM)
        .map(oldName => Ok(rename_group_complete(oldName.get, summary.groupName)))
    }
  }

  def showDeleteGroup(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary: GroupSummary, arn: Arn) =>
      Ok(confirm_delete_group(YesNoForm.form("group.delete.select.error"), summary)).toFuture
    }
  }

  def showDeleteTaxGroup(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId, isCustom = false) { (summary: GroupSummary, _: Arn) =>
      Ok(confirm_delete_group(YesNoForm.form("group.delete.select.error"), summary, isCustom = false)).toFuture
    }
  }

  def submitDeleteGroup(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary: GroupSummary, _: Arn) =>
      YesNoForm
        .form("group.delete.select.error")
        .bindFromRequest()
        .fold(
          formWithErrors => Ok(confirm_delete_group(formWithErrors, summary)).toFuture,
          (answer: Boolean) =>
            if (answer) {
              for {
                _ <- sessionCacheService.put[String](GROUP_DELETED_NAME, summary.groupName)
                _ <- groupService.deleteGroup(groupId)
              } yield Redirect(routes.ManageGroupController.showGroupDeleted().url)
            } else
              Redirect(routes.ManageGroupController.showManageGroups(None, None).url).toFuture
        )
    }
  }

  def submitDeleteTaxGroup(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId, isCustom = false) { (summary: GroupSummary, _: Arn) =>
      YesNoForm
        .form("group.delete.select.error")
        .bindFromRequest()
        .fold(
          formWithErrors => Ok(confirm_delete_group(formWithErrors, summary)).toFuture,
          (answer: Boolean) =>
            if (answer) {
              for {
                _ <- sessionCacheService.put[String](GROUP_DELETED_NAME, summary.groupName)
                _ <- taxGroupService.deleteGroup(groupId)
              } yield Redirect(routes.ManageGroupController.showGroupDeleted().url)
            } else
              Redirect(routes.ManageGroupController.showManageGroups(None, None).url).toFuture
        )
    }
  }

  def showGroupDeleted: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        sessionCacheService
          .get(GROUP_DELETED_NAME)
          .map(groupName => Ok(delete_group_complete(groupName.getOrElse(""))))
      }
    }
  }

}
