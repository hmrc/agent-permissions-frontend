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
import controllers.actions.{AuthAction, GroupAction, OptInStatusAction}
import forms._
import models.{GroupId, SearchFilter}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{ClientService, SessionCacheOperationsService}
import models.Arn
import models.accessgroups.{AccessGroup, GroupSummary}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.assistant_read_only._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AssistantViewOnlyController @Inject() (
  authAction: AuthAction,
  groupAction: GroupAction,
  mcc: MessagesControllerComponents,
  optInStatusAction: OptInStatusAction,
  clientService: ClientService,
  sessionCacheOps: SessionCacheOperationsService,
  unassigned_client_list: unassigned_client_list,
  existing_group_client_list: existing_group_client_list
)(implicit val appConfig: AppConfig, ec: ExecutionContext, implicit override val messagesApi: MessagesApi)
    extends FrontendController(mcc) with I18nSupport with Logging {

  import authAction._
  import groupAction._
  import optInStatusAction._

  private val CLIENTS_PAGE_SIZE = 20

  def showUnassignedClientsViewOnly(page: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAssistant { arn =>
      isOptedIn(arn) { _ =>
        for {
          search <- sessionCacheService.get(CLIENT_SEARCH_INPUT)
          filter <- sessionCacheService.get(CLIENT_FILTER_INPUT)
          unassignedClients <-
            clientService.getUnassignedClients(arn)(page.getOrElse(1), CLIENTS_PAGE_SIZE, search, filter)
        } yield Ok(
          unassigned_client_list(
            unassignedClients.pageContent,
            SearchAndFilterForm.form().fill(SearchFilter(search, filter, None)),
            Some(unassignedClients.paginationMetaData)
          )
        )
      }
    }
  }

  // This endpoint exists in order to POST search/filter terms (GET form submit is disallowed by organisation policy)
  def submitUnassignedClientsViewOnly: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAssistant { arn =>
      isOptedIn(arn) { _ =>
        updateSearchFilter(redirectTo = routes.AssistantViewOnlyController.showUnassignedClientsViewOnly())
      }
    }
  }

  def showExistingGroupClientsViewOnly(groupId: GroupId, page: Option[Int] = None): Action[AnyContent] = Action.async {
    implicit request =>
      withGroupForAuthorisedAssistant(groupId) { (group: AccessGroup, _: Arn) =>
        val summary = GroupSummary.of(group)
        for {
          search <- sessionCacheService.get(CLIENT_SEARCH_INPUT)
          filter <- sessionCacheService.get(CLIENT_FILTER_INPUT)
          list <-
            groupService.getPaginatedClientsForCustomGroup(groupId)(page.getOrElse(1), pageSize = CLIENTS_PAGE_SIZE)
        } yield Ok(
          existing_group_client_list(
            clients = list._1,
            summary = summary,
            filterForm = SearchAndFilterForm.form().fill(SearchFilter(search, filter, None)),
            formAction = routes.AssistantViewOnlyController.submitExistingGroupClientsViewOnly(summary.groupId),
            paginationMetaData = Some(list._2)
          )
        )
      }
  }

  // This endpoint exists in order to POST search/filter terms (GET form submit is disallowed by organisation policy)
  def submitExistingGroupClientsViewOnly(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAssistant { arn =>
      isOptedIn(arn) { _ =>
        updateSearchFilter(redirectTo = routes.AssistantViewOnlyController.showExistingGroupClientsViewOnly(groupId))
      }
    }
  }

  def showExistingTaxClientsViewOnly(groupId: GroupId, page: Option[Int] = None): Action[AnyContent] = Action.async {
    implicit request =>
      withGroupForAuthorisedAssistant(groupId, isCustom = false) { (group: AccessGroup, arn: Arn) =>
        val summary = GroupSummary.of(group)
        for {
          // needs Tax service saved to session on page load
          search <- sessionCacheService.get(CLIENT_SEARCH_INPUT)
          filter = if (summary.taxService.getOrElse("") == "HMRC-TERS") {
                     Some("TRUST")
                   } else {
                     summary.taxService
                   }
          _    <- sessionCacheOps.saveSearch(search, filter)
          list <- clientService.getPaginatedClients(arn)(page.getOrElse(1), CLIENTS_PAGE_SIZE)
        } yield Ok(
          existing_group_client_list(
            clients = list.pageContent,
            summary = summary,
            filterForm = SearchAndFilterForm.form().fill(SearchFilter(search, filter, None)),
            formAction = routes.AssistantViewOnlyController.submitExistingTaxClientsViewOnly(summary.groupId),
            paginationMetaData = Some(list.paginationMetaData)
          )
        )
      }
  }

  // This endpoint exists in order to POST search/filter terms (GET form submit is disallowed by organisation policy)
  def submitExistingTaxClientsViewOnly(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAssistant { arn =>
      isOptedIn(arn) { _ =>
        updateSearchFilter(
          redirectTo = routes.AssistantViewOnlyController.showExistingTaxClientsViewOnly(groupId),
          ignoreTaxService = true
        )
      }
    }
  }

  private def updateSearchFilter(redirectTo: Call, ignoreTaxService: Boolean = false)(implicit
    request: Request[_]
  ): Future[Result] = {
    val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
    searchFilter.submit match {
      case Some(CLEAR_BUTTON) =>
        if (ignoreTaxService) {
          sessionCacheService.delete(CLIENT_SEARCH_INPUT).map { _ =>
            Redirect(redirectTo)
          }
        } else {
          sessionCacheService.deleteAll(Seq(CLIENT_SEARCH_INPUT, CLIENT_FILTER_INPUT)).map { _ =>
            Redirect(redirectTo)
          }
        }
      case Some(FILTER_BUTTON) =>
        if (ignoreTaxService) {
          sessionCacheService.put(CLIENT_SEARCH_INPUT, searchFilter.search.getOrElse("")).map { _ =>
            Redirect(redirectTo)
          }
        } else {
          for {
            _ <- sessionCacheService.put(CLIENT_SEARCH_INPUT, searchFilter.search.getOrElse(""))
            _ <- sessionCacheService.put(CLIENT_FILTER_INPUT, searchFilter.filter.getOrElse(""))
          } yield Redirect(redirectTo)
        }
      case _ => Future.successful(BadRequest)
    }
  }
}
