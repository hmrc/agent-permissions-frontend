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
import connectors.AddMembersToAccessGroupRequest
import controllers.actions.{GroupAction, SessionAction}
import forms._
import models.DisplayClient.format
import models.{AddClientsToGroup, DisplayClient, GroupId, SearchFilter}
import play.api.Logging
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{ClientService, GroupService, SessionCacheOperationsService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, PaginatedList, PaginationMetaData}
import uk.gov.hmrc.agentmtdidentifiers.utils.PaginatedListBuilder
import uk.gov.hmrc.agents.accessgroups.{Client, GroupSummary}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.create.clients.{confirm_remove_client, search_clients}
import views.html.groups.manage.clients._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

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
  update_clients_paginated: update_clients_paginated,
  confirm_remove_client: confirm_remove_client,
  existing_clients: existing_clients,
  search_clients: search_clients
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
  def showExistingGroupClients(groupId: GroupId, page: Option[Int] = None, pageSize: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (groupSummary: GroupSummary, arn: Arn) =>
      val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
      searchFilter.submit.fold( // fresh page load or pagination reload
        groupService
          .getPaginatedClientsForCustomGroup(groupId)(page.getOrElse(1), pageSize.getOrElse(20)).map({ paginatedList: (Seq[DisplayClient], PaginationMetaData) =>
          Ok(
            existing_clients(
              group = groupSummary,
              groupClients = paginatedList._1,
              form = SearchAndFilterForm.form(),
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
            group = groupSummary,
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

  def showConfirmRemoveClient(groupId: GroupId, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (groupSummary: GroupSummary, arn: Arn) => {
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
                    groupSummary.groupName,
                    client,
                    formAction = controller.submitConfirmRemoveClient(groupId),
                    legendKey = "common.group.remove.client"
                  )
                )
              )
          )
        )
    }
    }
  }

  def submitConfirmRemoveClient(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (group: GroupSummary, _: Arn) => {
      withSessionItem[DisplayClient](CLIENT_TO_REMOVE) { maybeClient =>
        val redirectLink = controller.showExistingGroupClients(groupId, None, None)
        maybeClient.fold(
          Redirect(controller.showExistingGroupClients(group.groupId, None, None)).toFuture
        )(clientToRemove =>
          YesNoForm
            .form("group.client.remove.error")
            .bindFromRequest()
            .fold(
              formWithErrors => {
                Ok(
                  confirm_remove_client(
                    formWithErrors,
                    group.groupName,
                    clientToRemove,
                    formAction = controller.submitConfirmRemoveClient(groupId)
                  )
                ).toFuture
              }, (yes: Boolean) => {
                if (yes) {
                  groupService
                    .removeClientFromGroup(groupId, clientToRemove.enrolmentKey) // can currently remove the last client in a group!
                    .map(_ =>
                      Redirect(redirectLink)
                        .flashing("success" -> request.messages("client.removed.confirm", clientToRemove.name))
                    )
                }
                else Redirect(redirectLink).toFuture
              }
            )
        )
      }
    }
    }
  }

  def showSearchClientsToAdd(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (groupSummary: GroupSummary, _: Arn) =>
      withSessionItem[String](CLIENT_FILTER_INPUT) { clientFilterTerm =>
        withSessionItem[String](CLIENT_SEARCH_INPUT) { clientSearchTerm =>
          Ok(
            search_clients(
              form = SearchAndFilterForm.form().fill(SearchFilter(clientSearchTerm, clientFilterTerm, None)),
              groupName = groupSummary.groupName,
              searchAction = controller.submitSearchClientsToAdd(groupId)
            )
          ).toFuture
        }
      }
    }
  }

  def submitSearchClientsToAdd(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (groupSummary: GroupSummary, _: Arn) =>
      SearchAndFilterForm
        .form()
        .bindFromRequest()
        .fold(
          formWithErrors => {
            Ok(
              search_clients(
                formWithErrors,
                groupSummary.groupName,
                searchAction = controller.submitSearchClientsToAdd(groupId)
              )
            ).toFuture
          }, formData => {
            sessionCacheOps.saveSearch(formData.search, formData.filter).flatMap(_ => {
              Redirect(controller.showAddClients(groupId, None, None)).toFuture
            })
          })
    }
  }

  def showAddClients(groupId: GroupId, page: Option[Int] = None, pageSize: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (_: GroupSummary, _: Arn) =>
      withSessionItem[String](CLIENT_FILTER_INPUT) { filter =>
        withSessionItem[String](CLIENT_SEARCH_INPUT) { search =>
          clientService
            .getPaginatedClientsToAddToGroup(groupId)(page.getOrElse(1), pageSize.getOrElse(20), search, filter)
            .flatMap { case (groupSummary, paginatedClients) =>
              if (paginatedClients.pageContent.isEmpty) {// if the search failed (no results) (APB-7378)
                withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
                  val canContinue = selectedClients.exists(_.nonEmpty)
                  Future.successful(Ok(search_clients(
                    form = SearchAndFilterForm.form().fill(SearchFilter(search, filter, None)),
                    groupName = groupSummary.groupName,
                    isFailedSearch = true,
                    searchAction = controller.submitSearchClientsToAdd(groupId),
                    continueAction = if (canContinue) Some(controller.submitAddClients(groupId)) else None
                  )))
                }
              } else {
                val form = AddClientsToGroupForm.form().fill(AddClientsToGroup(search, filter, None))
                Future.successful(renderUpdateClientsPaginated(groupSummary, form, paginatedClients))
              }
            }
        }
      }
    }
  }

  def submitAddClients(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (groupSummary: GroupSummary, _: Arn) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { maybeSelected =>
        withSessionItem[String](CLIENT_FILTER_INPUT) { filter =>
          withSessionItem[String](CLIENT_SEARCH_INPUT) { search =>
            // allows form to bind if preselected clients so we can `.saveSelectedOrFilteredClients`
            val hasPreSelected = maybeSelected.getOrElse(Seq.empty).nonEmpty
            AddClientsToGroupForm
              .form(hasPreSelected)
              .bindFromRequest()
              .fold(
                formWithErrors => {
                  clientService
                    .getPaginatedClientsToAddToGroup(groupId)(1, 20, search, filter)
                    .map { tuple =>
                      renderUpdateClientsPaginated(groupSummary, formWithErrors, tuple._2)
                    }
                },
                formData => {
                  // don't savePageOfClients if "Select all button" eg forData.submit == "SELECT_ALL"
                  sessionCacheOps
                    .saveClientsToAddToExistingGroup(formData)
                    .flatMap(nowSelectedClients =>
                      if (formData.submit == CONTINUE_BUTTON) {
                        // checks selected clients from session cache AFTER saving (removed de-selections)
                        if (nowSelectedClients.nonEmpty) {
                          Redirect(controller.showReviewSelectedClients(groupId, None, None)).toFuture
                        } else { // display empty error
                          for {
                            paginatedClients <- clientService
                              .getPaginatedClientsToAddToGroup(groupId)(1, 20, search, filter)
                          } yield {
                            renderUpdateClientsPaginated(
                              groupSummary,
                              AddClientsToGroupForm.form().withError("clients", "error.select-clients.empty"),
                              paginatedClients._2
                            )
                          }
                        }
                      } else if (formData.submit.startsWith(PAGINATION_BUTTON)) {
                        val pageToShow = formData.submit.replace(s"${PAGINATION_BUTTON}_", "").toInt
                        Redirect(controller.showAddClients(groupId, Some(pageToShow), Some(20))).toFuture
                      } else { //bad submit
                        Redirect(controller.showSearchClientsToAdd(groupId)).toFuture
                      }
                    )
                }
              )
          }
        }
      }
    }
  }

  def showConfirmRemoveFromSelectedClients(groupId: GroupId, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (groupSummary: GroupSummary, arn: Arn) => {
      withSessionItem(SELECTED_CLIENTS) { selectedClients =>
        selectedClients.getOrElse(Seq.empty).find(_.id == clientId) match {
          // if the user tries to go back after removing the selected client, take them to search clients instead
          case None => Future.successful(Redirect(controller.showSearchClientsToAdd(groupId)))
          case Some(client) =>
            sessionCacheService
              .put(CLIENT_TO_REMOVE, client)
              .map(_ =>
                Ok(
                  confirm_remove_client(
                    YesNoForm.form(),
                    groupSummary.groupName,
                    client,
                    formAction = controller.submitConfirmRemoveFromSelectedClients(groupId, client.id)
                  )
                )
              )
          }
      }
    }
    }
  }

  def submitConfirmRemoveFromSelectedClients(groupId: GroupId, clientId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (group: GroupSummary, _: Arn) => {
      withSessionItem[DisplayClient](CLIENT_TO_REMOVE) { maybeClient =>
        withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { maybeSelectedClients =>
          val redirectLink: Call = controller.showReviewSelectedClients(groupId, None, None)
          maybeClient.fold(
            Redirect(controller.showSearchClientsToAdd(group.groupId)).toFuture
          )(clientToRemove =>
            YesNoForm
              .form("group.client.review.remove.error")
              .bindFromRequest()
              .fold(
                formWithErrors => {
                  Ok(
                    confirm_remove_client(
                      formWithErrors,
                      group.groupName,
                      clientToRemove,
                      formAction = controller.submitConfirmRemoveFromSelectedClients(groupId, clientToRemove.id)
                    )
                  ).toFuture
                }, (yes: Boolean) => {
                  if (yes) {
                    val remainingClients = maybeSelectedClients.getOrElse(Nil).filterNot(dc => clientToRemove.id == dc.id)
                    for {
                      _ <- sessionCacheService.put(SELECTED_CLIENTS, remainingClients)
                      _ <- sessionCacheService.delete(CLIENT_TO_REMOVE)
                    } yield remainingClients.size match {
                          case 0 => Redirect(controller.showSearchClientsToAdd(group.groupId))
                          case _ => Redirect(redirectLink)
                    }
                  }
                  else Redirect(redirectLink).toFuture
                }
              )
          )
        }
      }
    }
    }
  }

  private def renderUpdateClientsPaginated(groupSummary: GroupSummary,
                                           form: Form[AddClientsToGroup],
                                           paginatedClients: PaginatedList[DisplayClient])
                                          (implicit request: Request[_]): Result = {
    Ok(
      update_clients_paginated(
        clients = paginatedClients.pageContent,
        group = groupSummary,
        form = form,
        paginationMetaData = Some(paginatedClients.paginationMetaData)
      )
    )
  }

  def showReviewSelectedClients(groupId: GroupId, page: Option[Int], pageSize: Option[Int]): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (groupSummary: GroupSummary, _: Arn) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        selectedClients
          .fold {
            Redirect(controller.showSearchClientsToAdd(groupId)).toFuture
          } { clients =>
            val paginatedList = PaginatedListBuilder.build[DisplayClient](page.getOrElse(1), pageSize.getOrElse(20), clients)
            sessionCacheService.get(CONFIRM_CLIENTS_SELECTED)
              .map( mData =>
            renderReviewUpdateClients(groupSummary, paginatedList, formWithFilledValue(YesNoForm.form(), mData)))
          }
      }
    }
  }


  private def renderReviewUpdateClients(groupSummary: GroupSummary,
                                        paginatedList: PaginatedList[DisplayClient],
                                        form: Form[Boolean])
                                       (implicit request: Request[_]): Result = {
    Ok(
      review_update_clients(
        paginatedList.pageContent,
        groupSummary,
        form,
        paginatedList.paginationMetaData
      )
    )
  }

  def submitReviewSelectedClients(groupId: GroupId): Action[AnyContent] = Action.async { implicit request =>
    withGroupSummaryForAuthorisedOptedAgent(groupId) { (groupSummary: GroupSummary, _: Arn) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        selectedClients
          .fold(
            Redirect(controller.showExistingGroupClients(groupId, None, None)).toFuture
          ) { clients =>
            val paginatedList = PaginatedListBuilder.build[DisplayClient](1, 20, clients)
            YesNoForm
              .form("group.clients.review.error")
              .bindFromRequest()
              .fold(
                formWithErrors => {
                  renderReviewUpdateClients(groupSummary, paginatedList, formWithErrors).toFuture
                }, (selectMoreClients: Boolean) => {
                  if (selectMoreClients) sessionCacheService.deleteAll(clientFilteringKeys)
                    .map(_ => Redirect(controller.showSearchClientsToAdd(groupId)))
                  else {
                    val toSave = clients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet
                    val x = for {
                      _ <- sessionCacheService.deleteAll(managingGroupKeys)
                      _ <- groupService.addMembersToGroup(groupId, AddMembersToAccessGroupRequest(None, Some(toSave)))
                    } yield ()
                    x.map(_ => Redirect(controller.showExistingGroupClients(groupId, None, None))
                      .flashing("success" -> request.messages("common.clients.added", toSave.size))
                    )
                  }
                }
              )
          }
      }
    }
  }

}
