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
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.assistant_read_only._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

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

  def showUnassignedClientsViewOnly: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAssistant { arn =>
      isOptedIn(arn) { _ =>
        val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
        searchFilter.submit.fold(
          //no filter/clear was applied
          clientService.getUnassignedClients(arn).map(clients =>
            Ok(unassigned_client_list(clients, SearchAndFilterForm.form())))
        ) { //either the 'filter' button or the 'clear' filter button was clicked
          case CLEAR_BUTTON =>
            Redirect(routes.AssistantViewOnlyController.showUnassignedClientsViewOnly).toFuture
          case FILTER_BUTTON =>
            clientService.getUnassignedClients(arn).map { clients =>
              val filteredClients = clients.filter(_.name.toLowerCase.contains(searchFilter.search.getOrElse("")
                .toLowerCase))
                .filter(dc =>
                  if (!searchFilter.filter.isDefined) true
                  else {
                    val filter = searchFilter.filter.get
                    dc.taxService.equalsIgnoreCase(filter) || (filter == "TRUST" && dc.taxService.startsWith("HMRC-TERS"))
                  }
                )
              Ok(unassigned_client_list(filteredClients, SearchAndFilterForm.form().fill(searchFilter)))
            }

        }
      }
    }
  }

  def showExistingGroupClientsViewOnly(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAssistant(groupId) { group: CustomGroup =>
      val displayGroup = DisplayGroup.fromAccessGroup(group)
      val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
      searchFilter.submit.fold(
        //no filter/clear was applied
        Ok(existing_group_client_list(group = displayGroup, filterForm = SearchAndFilterForm.form()))
      )(submitButton =>
        //either the 'filter' button or the 'clear' filter button was clicked
        submitButton match {
          case CLEAR_BUTTON =>
            Redirect(routes.AssistantViewOnlyController.showExistingGroupClientsViewOnly(groupId))
          case FILTER_BUTTON =>
            val filteredClients = displayGroup.clients
              .filter(_.name.toLowerCase.contains(searchFilter.search.getOrElse("").toLowerCase))
              .filter(dc =>
                if (!searchFilter.filter.isDefined) true
                else {
                  val filter = searchFilter.filter.get
                  dc.taxService.equalsIgnoreCase(filter) || (filter == "TRUST" && dc.taxService.startsWith("HMRC-TERS"))
                })
            Ok(
              existing_group_client_list(
                group = displayGroup.copy(clients = filteredClients),
                filterForm = SearchAndFilterForm.form().fill(searchFilter)
              )
            )
        }
      ).toFuture
    }
  }

}
