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
import controllers.actions.GroupAction
import forms._
import models.SearchFilter
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{ClientService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.{GroupSummary, TaxGroup}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.manage.clients._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageTaxGroupClientsController @Inject()
(
  groupAction: GroupAction,
  mcc: MessagesControllerComponents,
  clientService: ClientService,
  val sessionCacheService: SessionCacheService,
  existing_tax_group_clients: existing_tax_group_clients,
)(
  implicit val appConfig: AppConfig,
  ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc)
  with I18nSupport
  with Logging {

  import groupAction._

  private val controller: ReverseManageTaxGroupClientsController = routes.ManageTaxGroupClientsController

  def showExistingGroupClients(groupId: String, page: Option[Int] = None, pageSize: Option[Int] = None)
  : Action[AnyContent] = Action.async { implicit request =>
    withTaxGroupForAuthorisedOptedAgent(groupId) { group: TaxGroup =>
      val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
      searchFilter.submit.fold( // fresh page load or pagination reload
        for {
          _ <- sessionCacheService.put(CLIENT_FILTER_INPUT, if (group.service == "HMRC-TERS") {
            "TRUST"
          } else {
            group.service
          })
          paginatedClients <- clientService.getPaginatedClients(group.arn)(page.getOrElse(1), pageSize.getOrElse(20))
        } yield Ok(existing_tax_group_clients(
          group = GroupSummary.fromAccessGroup(group),
          groupClients = paginatedClients.pageContent,
          form = SearchAndFilterForm.form(), // TODO fill form when reloading page via pagination
          paginationMetaData = Some(paginatedClients.paginationMetaData)
        ))
      ) { // a button was clicked
        case FILTER_BUTTON =>
          for {
            _ <- sessionCacheService.put(CLIENT_SEARCH_INPUT, searchFilter.search.getOrElse(""))
            _ <- sessionCacheService.put(CLIENT_FILTER_INPUT, if (group.service == "HMRC-TERS") {
              "TRUST"
            } else {
              group.service
            })
            paginatedClients <- clientService.getPaginatedClients(group.arn)(page.getOrElse(1), pageSize.getOrElse(20))
          } yield Ok(existing_tax_group_clients(
            group = GroupSummary.fromAccessGroup(group),
            groupClients = paginatedClients.pageContent,
            form = SearchAndFilterForm.form().fill(searchFilter),
            paginationMetaData = Some(paginatedClients.paginationMetaData)
          ))
        case CLEAR_BUTTON =>
          sessionCacheService.delete(CLIENT_SEARCH_INPUT).map(_ =>
            Redirect(controller.showExistingGroupClients(groupId, Some(1), Some(20)))
          )
        case button =>
          if (button.startsWith(PAGINATION_BUTTON)) {
            val pageToShow = button.replace(s"${PAGINATION_BUTTON}_", "").toInt
            Redirect(controller.showExistingGroupClients(groupId, Some(pageToShow), Some(20))).toFuture
          } else { // bad submit
            Redirect(controller.showExistingGroupClients(groupId, Some(1), Some(20))).toFuture
          }
      }
    }
  }


}
