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
import connectors.AgentPermissionsConnector
import forms._
import models.{DisplayClient, DisplayGroup, SearchFilter}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
import services.{ClientService, GroupService, SessionCacheService}
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
     groupService: GroupService,
     clientService: ClientService,
     val agentPermissionsConnector: AgentPermissionsConnector,
     val sessionCacheRepository: SessionCacheRepository,
     val sessionCacheService: SessionCacheService,
     unassigned_client_list: unassigned_client_list,
     existing_group_client_list: existing_group_client_list
    )
                                           (implicit val appConfig: AppConfig, ec: ExecutionContext,
    implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

  with GroupsControllerCommon
  with I18nSupport
  with SessionBehaviour
  with Logging {

  import authAction._
  import groupAction._

  def showUnassignedClientsViewOnly: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAssistant { arn =>
      isOptedIn(arn) { _ =>
        val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
        searchFilter.submit.fold(
          //no filter/clear was applied
          clientService.getUnassignedClients(arn).map(maybeClients =>
            Ok(unassigned_client_list(
              unassignedClients = maybeClients.getOrElse(Seq.empty[DisplayClient]),
              filterForm = SearchAndFilterForm.form()
            ))
          )
        ) { //either the 'filter' button or the 'clear' filter button was clicked
          case "clear" =>
            Redirect(routes.AssistantViewOnlyController.showUnassignedClientsViewOnly).toFuture
          case "filter" =>
            for {
              maybeClients <- clientService.getUnassignedClients(arn)
              filteredClients = maybeClients.getOrElse(Seq.empty[DisplayClient])
                .filter(_.name.toLowerCase.contains(searchFilter.search.getOrElse("").toLowerCase))
                .filter(dc =>
                  if (!searchFilter.filter.isDefined) true
                  else {
                    val filter = searchFilter.filter.get
                    dc.taxService.equalsIgnoreCase(filter) || (filter == "TRUST" && dc.taxService.startsWith("HMRC-TERS"))
                  }
                )
            } yield Ok(
              unassigned_client_list(
                unassignedClients = filteredClients,
                filterForm = SearchAndFilterForm.form().fill(searchFilter)
              )
            )
        }
      }
    }
  }

  def showExistingGroupClientsViewOnly(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAssistant(groupId) { group: AccessGroup =>
      val displayGroup = DisplayGroup.fromAccessGroup(group)
      val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
      searchFilter.submit.fold(
        //no filter/clear was applied
        Ok(existing_group_client_list(group = displayGroup, filterForm = SearchAndFilterForm.form()))
      )(submitButton =>
        //either the 'filter' button or the 'clear' filter button was clicked
        submitButton match {
          case "clear" =>
            Redirect(routes.AssistantViewOnlyController.showExistingGroupClientsViewOnly(groupId))
          case "filter" =>
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