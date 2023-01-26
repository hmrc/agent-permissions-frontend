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
        val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
        def render(searchFilter: SearchFilter): Future[Result] =
          clientService.getUnassignedClients(arn)(page.getOrElse(1), UNASSIGNED_CLIENTS_PAGE_SIZE, searchFilter.search, searchFilter.filter).map { clients =>
            Ok(unassigned_client_list(clients.pageContent, SearchAndFilterForm.form().fill(searchFilter), Some(clients.paginationMetaData)))
          }
        searchFilter.submit match {
          case None => //no filter/clear was applied
            render(SearchFilter(None, None, None))
          //either the 'filter' button or the 'clear' filter button was clicked
          case Some(CLEAR_BUTTON) =>
            Redirect(routes.AssistantViewOnlyController.showUnassignedClientsViewOnly(page)).toFuture
          case Some(FILTER_BUTTON) =>
            render(searchFilter)
          case _ => Future.successful(BadRequest)
        }
      }
    }
  }

  def showExistingGroupClientsViewOnly(groupId: String, page: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAssistant(groupId) { group: CustomGroup =>
      val displayGroup = DisplayGroup.fromAccessGroup(group)
      val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
      def render(searchFilter: SearchFilter): Future[Result] = {
        val filteredClients = PaginationUtil.filterClients(displayGroup.clients, searchFilter.search, searchFilter.filter)
        val paginated = PaginatedListBuilder.build(page = page.getOrElse(1), pageSize = UNASSIGNED_CLIENTS_PAGE_SIZE, fullList = filteredClients)
        Future.successful(Ok(
          existing_group_client_list(
            group = displayGroup.copy(clients = paginated.pageContent),
            filterForm = SearchAndFilterForm.form().fill(searchFilter),
            paginationMetaData = Some(paginated.paginationMetaData)
          )
        ))
      }
      searchFilter.submit match {
        case None => //no filter/clear was applied
          render(SearchFilter(None, None, None))
      //either the 'filter' button or the 'clear' filter button was clicked
        case Some(CLEAR_BUTTON) =>
          Future.successful(Redirect(routes.AssistantViewOnlyController.showExistingGroupClientsViewOnly(groupId)))
        case Some(FILTER_BUTTON) =>
          render(searchFilter)
        case _ => Future.successful(BadRequest)
      }
    }
  }

}
