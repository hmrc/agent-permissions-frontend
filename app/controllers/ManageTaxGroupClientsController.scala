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
import connectors.UpdateTaxServiceGroupRequest
import controllers.actions.{GroupAction, SessionAction}
import forms._
import models.DisplayClient.{fromClient, toClient}
import models.{AddClientsToGroup, DisplayClient, GroupId, SearchFilter}
import play.api.Logging
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{ClientService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, PaginatedList}
import uk.gov.hmrc.agentmtdidentifiers.utils.PaginatedListBuilder
import uk.gov.hmrc.agents.accessgroups.{Client, GroupSummary, TaxGroup}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.create.clients.confirm_remove_client
import views.html.groups.manage.clients._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ManageTaxGroupClientsController @Inject() (
  groupAction: GroupAction,
  sessionAction: SessionAction,
  mcc: MessagesControllerComponents,
  clientService: ClientService,
  val sessionCacheService: SessionCacheService,
  existing_tax_group_clients: existing_tax_group_clients,
  removed_tax_group_clients: removed_tax_group_clients,
  confirm_remove_client: confirm_remove_client,
  excluded_clients_not_found: excluded_clients_not_found
)(
  implicit val appConfig: AppConfig,
  ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc) with I18nSupport with Logging {

  import groupAction._
  import sessionAction.withSessionItem

  private val controller: ReverseManageTaxGroupClientsController = routes.ManageTaxGroupClientsController

  def showExistingGroupClients(
    groupId: GroupId,
    page: Option[Int] = None,
    pageSize: Option[Int] = None
  ): Action[AnyContent] = Action.async { implicit request =>
    withTaxGroupForAuthorisedOptedAgent(groupId) { (group: TaxGroup, _: Arn) =>
      val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
      val taxServiceName = if (group.service == "HMRC-TERS") "TRUST" else group.service
      searchFilter.submit.fold( // fresh page load or pagination reload
        for {
          _                <- sessionCacheService.put(CLIENT_FILTER_INPUT, taxServiceName)
          paginatedClients <- clientService.getPaginatedClients(group.arn)(page.getOrElse(1), pageSize.getOrElse(20))
        } yield {
          val form = SearchAndFilterForm.form()
          renderGroupClientsWithExcluded(group, paginatedClients, form)
        }
      ) { // a button was clicked
        case FILTER_BUTTON =>
          for {
            _                <- sessionCacheService.put(CLIENT_SEARCH_INPUT, searchFilter.search.getOrElse(""))
            _                <- sessionCacheService.put(CLIENT_FILTER_INPUT, taxServiceName)
            paginatedClients <- clientService.getPaginatedClients(group.arn)(page.getOrElse(1), pageSize.getOrElse(20))
          } yield {
            val form = SearchAndFilterForm.form().fill(searchFilter)
            renderGroupClientsWithExcluded(group, paginatedClients, form)
          }
        case CLEAR_BUTTON =>
          sessionCacheService
            .delete(CLIENT_SEARCH_INPUT)
            .map(_ => Redirect(controller.showExistingGroupClients(groupId, Some(1), Some(20))))
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

  private def renderGroupClientsWithExcluded(
    group: TaxGroup,
    paginatedClients: PaginatedList[DisplayClient],
    form: Form[SearchFilter]
  )(implicit request: Request[_]): Result = {
    val excludedClients = group.excludedClients.map(fromClient(_)).toSeq
    Ok(
      existing_tax_group_clients(
        group = GroupSummary.of(group),
        groupClients = paginatedClients.pageContent,
        form = form,
        paginationMetaData = Some(paginatedClients.paginationMetaData),
        excludedClients = excludedClients
      )
    )
  }

  def showConfirmRemoveClient(groupId: GroupId, clientId: String): Action[AnyContent] = Action.async {
    implicit request =>
      withTaxGroupForAuthorisedOptedAgent(groupId) { (group: TaxGroup, arn: Arn) =>
        clientService
          .lookupClient(arn)(clientId)
          .flatMap(maybeClient =>
            maybeClient.fold(Redirect(controller.showExistingGroupClients(groupId, None, None)).toFuture)(client =>
              sessionCacheService
                .put(CLIENT_TO_REMOVE, client)
                .map(_ => renderConfirmRemoveClient(group, groupId, client, YesNoForm.form()))
            )
          )
      }
  }

  def submitConfirmRemoveClient(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withTaxGroupForAuthorisedOptedAgent(groupId) { (group: TaxGroup, _: Arn) =>
      withSessionItem[DisplayClient](CLIENT_TO_REMOVE) { maybeClient =>
        val groupId = group.id
        maybeClient.fold(
          Redirect(controller.showExistingGroupClients(groupId, None, None)).toFuture
        )(clientToRemove =>
          YesNoForm
            .form("group.client.remove.error")
            .bindFromRequest()
            .fold(
              formWithErrors => renderConfirmRemoveClient(group, groupId, clientToRemove, formWithErrors).toFuture,
              (yes: Boolean) =>
                if (yes) {
                  val updateRequest = UpdateTaxServiceGroupRequest(
                    excludedClients = Option(group.excludedClients + toClient(clientToRemove))
                  )
                  taxGroupService
                    .updateGroup(groupId, updateRequest)
                    .map { _ =>
                      val clientLink = routes.ManageClientController.showClientDetails(clientToRemove.id)
                      Redirect(controller.showExistingGroupClients(groupId, None, None))
                        .flashing(
                          "success" -> request
                            .messages("tax-group.client.removed.confirm", clientToRemove.name, clientLink)
                        )
                    }
                } else
                  Redirect(controller.showExistingGroupClients(groupId, None, None)).toFuture
            )
        )
      }
    }
  }

  private def renderConfirmRemoveClient(
    group: TaxGroup,
    groupId: GroupId,
    clientToRemove: DisplayClient,
    formWithErrors: Form[Boolean]
  )(implicit request: MessagesRequest[_]): Result =
    Ok(
      confirm_remove_client(
        formWithErrors,
        group.groupName,
        clientToRemove,
        formAction = controller.submitConfirmRemoveClient(groupId),
        hintKey = Some(request.messages("tax-group.clients.confirm.p1", clientToRemove.name)),
        legendKey = "common.group.remove.client"
      )
    )

  def showExcludedClients(
    groupId: GroupId,
    page: Option[Int] = None,
    pageSize: Option[Int] = None
  ): Action[AnyContent] = Action.async { implicit request =>
    withTaxGroupForAuthorisedOptedAgent(groupId) { (group: TaxGroup, _: Arn) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { maybeSelected =>
        withSessionItem[String](CLIENT_SEARCH_INPUT) { maybeSearch =>
          if (group.excludedClients.isEmpty)
            Ok(excluded_clients_not_found(group)).toFuture
          else {
            val paginatedList =
              paginationOfExcludedClients(group.excludedClients, page, pageSize, maybeSearch, maybeSelected)
            sessionCacheService
              .put(CURRENT_PAGE_CLIENTS, paginatedList.pageContent)
              .map { _ =>
                val form = AddClientsToGroupForm.form().fill(AddClientsToGroup(maybeSearch, None, None))
                renderRemovedTaxGroupClients(group, paginatedList, form)
              }
          }
        }
      }
    }
  }

  private def renderRemovedTaxGroupClients(
    group: TaxGroup,
    paginatedList: PaginatedList[DisplayClient],
    form: Form[AddClientsToGroup]
  )(implicit request: Request[_]): Result =
    Ok(
      removed_tax_group_clients(
        group = group,
        clients = paginatedList.pageContent,
        form = form,
        paginationMetaData = Option(paginatedList.paginationMetaData)
      )
    )

  def submitUnexcludeClients(
    groupId: GroupId,
    page: Option[Int] = None,
    pageSize: Option[Int] = None
  ): Action[AnyContent] = Action.async { implicit request =>
    withTaxGroupForAuthorisedOptedAgent(groupId) { (group: TaxGroup, _: Arn) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { maybeSelected =>
        withSessionItem[Seq[DisplayClient]](CURRENT_PAGE_CLIENTS) { currentPageClients =>
          withSessionItem[String](CLIENT_SEARCH_INPUT) { maybeSearch =>
            if (group.excludedClients.isEmpty)
              Ok(excluded_clients_not_found(group)).toFuture
            else {
              AddClientsToGroupForm
                .form()
                .bindFromRequest()
                .fold(
                  formWithErrors =>
                    // if there are already selected clients you shouldn't have to select on current page.
                    if (
                      formWithErrors.data.get("submit").contains(CONTINUE_BUTTON) && maybeSelected
                        .getOrElse(Nil)
                        .nonEmpty
                    ) {
                      updateExcludedClients(
                        groupId,
                        maybeSelected,
                        currentPageClients,
                        AddClientsToGroup(submit = CONTINUE_BUTTON),
                        group.excludedClients
                      )
                        .map(numberRemoved =>
                          Redirect(controller.showExistingGroupClients(groupId, None, None))
                            .flashing("success" -> request.messages("common.clients.added", numberRemoved))
                        )

                    } else {
                      val paginatedList =
                        paginationOfExcludedClients(group.excludedClients, page, pageSize, maybeSearch, maybeSelected)
                      renderRemovedTaxGroupClients(group, paginatedList, formWithErrors).toFuture
                    },
                  (formData: AddClientsToGroup) =>
                    if (group.excludedClients.isEmpty)
                      Ok(excluded_clients_not_found(group)).toFuture
                    else {
                      formData.submit match {
                        // a button was clicked
                        case CONTINUE_BUTTON =>
                          updateExcludedClients(
                            groupId,
                            maybeSelected,
                            currentPageClients,
                            formData,
                            group.excludedClients
                          )
                            .map(numberRemoved =>
                              Redirect(controller.showExistingGroupClients(groupId, None, None))
                                .flashing("success" -> request.messages("common.clients.added", numberRemoved))
                            )
                        case FILTER_BUTTON =>
                          sessionCacheService
                            .put(CLIENT_SEARCH_INPUT, formData.search.getOrElse(""))
                            .map(_ => Redirect(controller.showExcludedClients(groupId, page, pageSize)))
                        case CLEAR_BUTTON =>
                          sessionCacheService
                            .delete(CLIENT_SEARCH_INPUT)
                            .map(_ => Redirect(controller.showExcludedClients(groupId, Some(1), Some(10))))
                        case button =>
                          if (button.startsWith(PAGINATION_BUTTON)) {
                            val totalSelectedClients =
                              clientsSelectedIncludingCurrentPage(maybeSelected, currentPageClients, formData)
                            sessionCacheService
                              .put(SELECTED_CLIENTS, totalSelectedClients)
                              .map { _ =>
                                val pageToShow = button.replace(s"${PAGINATION_BUTTON}_", "").toInt
                                Redirect(controller.showExcludedClients(groupId, Some(pageToShow), Some(10)))
                              }
                          } else { // bad submit
                            Redirect(controller.showExcludedClients(groupId, Some(1), Some(10))).toFuture
                          }
                      }
                    }
                )
            }
          }
        }
      }
    }
  }

  private def updateExcludedClients(
    groupId: GroupId,
    maybeSelected: Option[Seq[DisplayClient]],
    currentPageClients: Option[Seq[DisplayClient]],
    formData: AddClientsToGroup,
    excludedClients: Set[Client]
  )(implicit request: Request[_], hc: HeaderCarrier): Future[Int] = {
    val clientsToUnexclude = clientsSelectedIncludingCurrentPage(maybeSelected, currentPageClients, formData)
    val clients: Set[Client] = excludedClients -- clientsToUnexclude.map(toClient)
    val updateRequest = UpdateTaxServiceGroupRequest(excludedClients = Option(clients))
    for {
      _ <- taxGroupService.updateGroup(groupId, updateRequest)
      _ <- sessionCacheService.delete(SELECTED_CLIENTS)
    } yield clientsToUnexclude.size
  }

  private def paginationOfExcludedClients(
    excludedClients: Set[Client],
    page: Option[Int],
    pageSize: Option[Int],
    maybeSearch: Option[String],
    maybeSelected: Option[Seq[DisplayClient]]
  ): PaginatedList[DisplayClient] = {
    var sortedExcludedClients = excludedClients.map(fromClient(_)).toSeq.sortBy(_.name)
    val pge = page.getOrElse(1)
    val pgSize = pageSize.getOrElse(10)
    if (maybeSearch.isDefined) {
      val lowerCaseSearch = maybeSearch.get.toLowerCase
      sortedExcludedClients = sortedExcludedClients
        .filter(dc => dc.name.toLowerCase.contains(lowerCaseSearch) || dc.hmrcRef.toLowerCase.contains(lowerCaseSearch))
    }
    if (maybeSelected.getOrElse(Seq.empty[DisplayClient]).nonEmpty) {
      sortedExcludedClients =
        sortedExcludedClients.map(dc => dc.copy(selected = maybeSelected.get.map(_.id).contains(dc.id)))
    }
    val pagination = PaginatedListBuilder.build(pge, pgSize, sortedExcludedClients)
    pagination
  }

  private def clientsSelectedIncludingCurrentPage(
    maybeSelected: Option[Seq[DisplayClient]],
    currentPageClients: Option[Seq[DisplayClient]],
    formData: AddClientsToGroup
  ) = {
    val currentPage = currentPageClients.get
    val alreadySelectedClients = maybeSelected.getOrElse(Seq.empty)
    val clientsSelectedInPage = currentPage.filter(dc => formData.clients.getOrElse(Seq.empty).contains(dc.id))
    val selectedClientsMinusThisPage = alreadySelectedClients.filterNot(dc => currentPage.map(_.id).contains(dc.id))
    val totalSelectedClients = selectedClientsMinusThisPage.diff(clientsSelectedInPage) ++ clientsSelectedInPage
    totalSelectedClients
  }
}
