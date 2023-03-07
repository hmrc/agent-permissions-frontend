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
import connectors.{AddMembersToAccessGroupRequest, UpdateAccessGroupRequest}
import controllers.actions.{GroupAction, SessionAction}
import forms._
import models.DisplayClient.format
import models.{AddClientsToGroup, DisplayClient, SearchFilter}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{ClientService, GroupService, SessionCacheOperationsService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.{GroupSummary, _}
import uk.gov.hmrc.agentmtdidentifiers.utils.PaginatedListBuilder
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.create.clients.{confirm_remove_client, search_clients}
import views.html.groups.manage.clients._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageGroupClientsController @Inject()
(
  groupAction: GroupAction,
  sessionAction: SessionAction,
  mcc: MessagesControllerComponents,
  clientService: ClientService,
  groupService: GroupService,
  val sessionCacheService: SessionCacheService,
  val sessionCacheOps: SessionCacheOperationsService,
  review_update_clients: review_update_clients,
  update_clients: update_clients_paginated,
  confirm_remove_client: confirm_remove_client,
  existing_clients: existing_clients,
  search_clients: search_clients,
  clients_update_complete: clients_update_complete
)(
  implicit val appConfig: AppConfig,
  ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc)

  with I18nSupport
  with Logging {

  import groupAction._
  import sessionAction.withSessionItem

  private val controller: ReverseManageGroupClientsController = routes.ManageGroupClientsController

  // custom clients
  def showExistingGroupClients(groupId: String, page: Option[Int] = None, pageSize: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary: GroupSummary, arn: Arn) =>
      val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
      searchFilter.submit.fold( // fresh page load or pagination reload
        groupService.getPaginatedClientsForCustomGroup(groupId)(page.getOrElse(1), pageSize.getOrElse(20)).map({ paginatedList: (Seq[DisplayClient], PaginationMetaData) =>
          Ok(
            existing_clients(
              group = summary,
              groupClients = paginatedList._1,
              form = SearchAndFilterForm.form(), // TODO fill form when reloading page via pagination
              paginationMetaData = Some(paginatedList._2)
            )
          )
        })
      ) { // a button was clicked
        case FILTER_BUTTON =>
          for {
            _ <- sessionCacheService.put(CLIENT_SEARCH_INPUT, searchFilter.search.getOrElse(""))
            _ <- sessionCacheService.put(CLIENT_FILTER_INPUT, searchFilter.filter.getOrElse(""))
            paginatedList <- groupService.getPaginatedClientsForCustomGroup(groupId)(1, pageSize.getOrElse(20))
          } yield Ok(existing_clients(
            group = summary,
            groupClients = paginatedList._1,
            form = SearchAndFilterForm.form().fill(searchFilter),
            paginationMetaData = Some(paginatedList._2)
          ))
        case CLEAR_BUTTON =>
          sessionCacheService.deleteAll(clientFilteringKeys).map(_ =>
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

  def showConfirmRemoveClient(groupId: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary: GroupSummary, arn: Arn) => {
      clientService
        .lookupClient(arn)(clientId)
        .flatMap(maybeClient =>
          maybeClient.fold(Redirect(controller.showExistingGroupClients(groupId, None, None)).toFuture)(client =>
            sessionCacheService
              .put(CLIENT_TO_REMOVE, client)
              .map(_ =>
                Ok(
                  confirm_remove_client(
                    YesNoForm.form(),
                    summary.groupName,
                    client,
                    backLink = controller.showExistingGroupClients(groupId, None, None),
                    formAction = controller.submitConfirmRemoveClient(groupId, client.id)
                  )
                )
              )
          )
        )
    }
    }
  }

  def submitConfirmRemoveClient(groupId: String, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (group: GroupSummary, _: Arn) => {
      withSessionItem[DisplayClient](CLIENT_TO_REMOVE) { maybeClient =>
        maybeClient.fold(
          Redirect(controller.showExistingGroupClients(group.groupId, None, None)).toFuture
        )(clientToRemove =>
          YesNoForm
            .form("group.client.remove.error")
            .bindFromRequest
            .fold(
              formWithErrors => {
                Ok(
                  confirm_remove_client(
                    formWithErrors,
                    group.groupName,
                    clientToRemove,
                    backLink = controller.showExistingGroupClients(groupId, None, None),
                    formAction = controller.submitConfirmRemoveClient(groupId, clientToRemove.id)
                  )
                ).toFuture
              }, (yes: Boolean) => {
                if (yes) {
                  groupService
                    .removeClientFromGroup(groupId, clientToRemove.enrolmentKey) // can currently remove the last client in a group!
                    .map(_ =>
                      Redirect(controller.showExistingGroupClients(groupId, None, None))
                        .flashing("success" -> request.messages("person.removed.confirm", clientToRemove.name))
                    )
                }
                else Redirect(controller.showExistingGroupClients(groupId, None, None)).toFuture
              }
            )
        )
      }
    }
    }
  }

  // add or remove for now...?
  def showSearchClientsToAdd(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary: GroupSummary, arn: Arn) =>
      withSessionItem[String](CLIENT_FILTER_INPUT) { clientFilterTerm =>
        withSessionItem[String](CLIENT_SEARCH_INPUT) { clientSearchTerm =>
          Ok(
            search_clients(
              form = SearchAndFilterForm.form().fill(SearchFilter(clientSearchTerm, clientFilterTerm, None)),
              groupName = summary.groupName,
              backUrl = Some(controller.showExistingGroupClients(groupId, None, None).url),
              formAction = controller.submitSearchClientsToAdd(groupId)
            )
          ).toFuture
        }
      }
    }
  }

  def submitSearchClientsToAdd(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary: GroupSummary, arn: Arn) =>
      SearchAndFilterForm
        .form()
        .bindFromRequest
        .fold(
          formWithErrors => {
            Ok(
              search_clients(
                formWithErrors,
                summary.groupName,
                backUrl = Some(controller.showExistingGroupClients(groupId, None, None).url),
                formAction = controller.submitSearchClientsToAdd(groupId)
              )
            ).toFuture
          }, formData => {
            sessionCacheOps.saveSearch(formData.search, formData.filter).flatMap(_ => {
              Redirect(controller.showManageGroupClients(groupId, None, None)).toFuture
            })
          })
    }
  }

  def showManageGroupClients(groupId: String, page: Option[Int] = None, pageSize: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group =>
      withSessionItem[String](CLIENT_FILTER_INPUT) { clientFilterTerm =>
        withSessionItem[String](CLIENT_SEARCH_INPUT) { clientSearchTerm =>
          val clientsInGroupAlready = group.clients.toSeq.flatten.map(DisplayClient.fromClient(_)).map(_.copy(selected = true))
          for {
            _ <- sessionCacheService.put[Seq[DisplayClient]](EXISTING_CLIENTS, clientsInGroupAlready)
            paginatedClients <- clientService
              .getPaginatedClientsForArn(group.arn, clientsInGroupAlready)(page.getOrElse(1), pageSize.getOrElse(20))
          } yield {
            val form = AddClientsToGroupForm.form().fill(AddClientsToGroup(clientSearchTerm, clientFilterTerm, None))
            Ok(
              update_clients(
                paginatedClients.pageContent,
                groupName = group.groupName,
                groupId = groupId,
                form,
                Some(paginatedClients.paginationMetaData)
              )
            )
          }
        }
      }
    }
  }

  def submitManageGroupClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: CustomGroup =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { maybeSelected =>
        // allows form to bind if preselected clients so we can `.saveSelectedOrFilteredClients`
        val hasPreSelected = maybeSelected.getOrElse(Seq.empty).nonEmpty
        AddClientsToGroupForm
          .form(hasPreSelected)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              clientService.getPaginatedClients(group.arn)(1, 20).map { paginatedClients =>
                Ok(
                  update_clients(
                    paginatedClients.pageContent,
                    group.groupName,
                    groupId,
                    formWithErrors,
                    Some(paginatedClients.paginationMetaData)
                  )
                )
              }
            },
            formData => {
              // don't savePageOfClients if "Select all button" eg forData.submit == "SELECT_ALL"
              sessionCacheOps
                .savePageOfClients(formData)
                .flatMap(nowSelectedClients =>
                  if (formData.submit == CONTINUE_BUTTON) {
                    // checks selected clients from session cache AFTER saving (removed de-selections)
                    if (nowSelectedClients.nonEmpty) {
                      sessionCacheService.deleteAll(clientFilteringKeys).map(_ =>
                        Redirect(controller.showReviewSelectedClients(groupId, None, None))
                      )
                    } else { // display empty error
                      for {
                        paginatedClients <- clientService.getPaginatedClients(group.arn)(1, 20)
                      } yield {
                        Ok(
                          update_clients(
                            paginatedClients.pageContent,
                            group.groupName,
                            groupId,
                            AddClientsToGroupForm.form().withError("clients", "error.select-clients.empty"),
                            Some(paginatedClients.paginationMetaData)
                          )
                        )
                      }
                    }
                  } else if (formData.submit.startsWith(PAGINATION_BUTTON)) {
                    val pageToShow = formData.submit.replace(s"${PAGINATION_BUTTON}_", "").toInt
                    Redirect(controller.showManageGroupClients(groupId, Some(pageToShow), Some(20))).toFuture
                  } else { //bad submit
                    Redirect(controller.showSearchClientsToAdd(groupId)).toFuture
                  }
                )
            }
          )
      }
    }
  }

  def showReviewSelectedClients(groupId: String, page: Option[Int], pageSize: Option[Int]): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary: GroupSummary, arn: Arn) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        selectedClients
          .fold {
            Redirect(controller.showSearchClientsToAdd(groupId))
          } { clients =>
            val paginatedList = PaginatedListBuilder.build[DisplayClient](page.getOrElse(1), pageSize.getOrElse(20), clients)
            Ok(review_update_clients(
              paginatedList.pageContent,
              summary,
              YesNoForm.form(),
              paginatedList.paginationMetaData
            ))
          }.toFuture
      }
    }
  }

  def submitReviewSelectedClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary: GroupSummary, arn: Arn) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        selectedClients
          .fold(
            Redirect(controller.showExistingGroupClients(groupId, None, None)).toFuture
          ) { clients =>
            val paginatedList = PaginatedListBuilder.build[DisplayClient](1, 20, clients)
            YesNoForm
              .form("group.clients.review.error")
              .bindFromRequest
              .fold(
                formWithErrors => {
                  Ok(review_update_clients(
                    paginatedList.pageContent,
                    summary,
                    formWithErrors,
                    paginatedList.paginationMetaData
                  )).toFuture
                }, (yes: Boolean) => {
                  if (yes)
                    Redirect(controller.showSearchClientsToAdd(groupId)).toFuture
                  else {
                    val toSave = clients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet
                    // TODO replace with AddMembersToAccessGroupRequest after APB-6886
                    val addClientsRequest = AddMembersToAccessGroupRequest(None, Some(toSave))
                    groupService.addMembersToGroup(groupId, addClientsRequest).map(_ =>
                      Redirect(controller.showGroupClientsUpdatedConfirmation(groupId)) // update to controller.showExistingGroupClients(summary.groupId, None, None)
                    )
                  }
                }
              )
          }
      }
    }
  }

  def showGroupClientsUpdatedConfirmation(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (summary: GroupSummary, arn: Arn) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        if (selectedClients.isDefined) {
          sessionCacheService.delete(SELECTED_CLIENTS)
            .map(_ => Ok(clients_update_complete(summary)))
        }
        else Redirect(controller.showSearchClientsToAdd(groupId)).toFuture
      }
    }
  }

}
