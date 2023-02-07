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
import models.{DisplayGroup, SearchFilter}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.ClientService
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentmtdidentifiers.utils.PaginatedListBuilder
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.FilterUtils
import views.html.assistant_read_only._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AssistantViewOnlyController @Inject()(
         authAction: AuthAction,
         groupAction: GroupAction,
         mcc: MessagesControllerComponents,
         optInStatusAction: OptInStatusAction,
         clientService: ClientService,
         unassigned_client_list: unassigned_client_list,
         existing_group_client_list: existing_group_client_list
       )(implicit val appConfig: AppConfig, ec: ExecutionContext,
        implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

    with I18nSupport
    with Logging {

  import authAction._
  import groupAction._
  import optInStatusAction._

  private val UNASSIGNED_CLIENTS_PAGE_SIZE = 20

  def showUnassignedClientsViewOnly(page: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAssistant { arn =>
      isOptedIn(arn) { _ =>
        for {
          search <- sessionCacheService.get(CLIENT_SEARCH_INPUT)
          filter <- sessionCacheService.get(CLIENT_FILTER_INPUT)
          unassignedClients <- clientService.getUnassignedClients(arn)(page.getOrElse(1), UNASSIGNED_CLIENTS_PAGE_SIZE, search, filter)
        } yield {
          Ok(
            unassigned_client_list(
              unassignedClients.pageContent,
              SearchAndFilterForm.form().fill(SearchFilter(search, filter, None)),
              Some(unassignedClients.paginationMetaData))
          )
        }
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


  def showExistingGroupClientsViewOnly(groupId: String, page: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAssistant(groupId) { group: CustomGroup =>
      for {
        search <- sessionCacheService.get(CLIENT_SEARCH_INPUT)
        filter <- sessionCacheService.get(CLIENT_FILTER_INPUT)
        displayGroup = DisplayGroup.fromAccessGroup(group)
        filteredClients = FilterUtils.filterClients(displayGroup.clients, search, filter)
        paginated = PaginatedListBuilder.build(page = page.getOrElse(1), pageSize = UNASSIGNED_CLIENTS_PAGE_SIZE, fullList = filteredClients)
      } yield {
        Ok(
          existing_group_client_list(
            group = displayGroup.copy(clients = paginated.pageContent),
            filterForm = SearchAndFilterForm.form().fill(SearchFilter(search, filter, None)),
            paginationMetaData = Some(paginated.paginationMetaData)
          )
        )
      }
    }
  }

  // This endpoint exists in order to POST search/filter terms (GET form submit is disallowed by organisation policy)
  def submitExistingGroupClientsViewOnly(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAssistant { arn =>
      isOptedIn(arn) { _ =>
        updateSearchFilter(redirectTo = routes.AssistantViewOnlyController.showExistingGroupClientsViewOnly(groupId))
      }
    }
  }

  private def updateSearchFilter(redirectTo: Call)(implicit request: Request[_]): Future[Result] = {
    val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
    searchFilter.submit match {
      case Some(CLEAR_BUTTON) =>
        sessionCacheService.deleteAll(Seq(CLIENT_SEARCH_INPUT, CLIENT_FILTER_INPUT)).map { _ =>
          Redirect(redirectTo)
        }
      case Some(FILTER_BUTTON) => for {
        _ <- sessionCacheService.put(CLIENT_SEARCH_INPUT, searchFilter.search.getOrElse(""))
        _ <- sessionCacheService.put(CLIENT_FILTER_INPUT, searchFilter.filter.getOrElse(""))
      } yield {
        Redirect(redirectTo)
      }
      case _ => Future.successful(BadRequest)
    }
  }
}
