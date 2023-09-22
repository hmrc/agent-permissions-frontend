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
import controllers.actions.{GroupAction, SessionAction}
import forms.{AddClientsToGroupForm, SearchAndFilterForm, YesNoForm}
import models.{AddClientsToGroup, DisplayClient, SearchFilter}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{ClientService, GroupService, SessionCacheOperationsService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.utils.PaginatedListBuilder
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.create.clients._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateGroupSelectClientsController @Inject()
(
  groupAction: GroupAction,
  sessionAction: SessionAction,
  mcc: MessagesControllerComponents,
  search_clients: search_clients,
  select_paginated_clients: select_paginated_clients,
  review_selected: review_selected,
  confirm_remove_client: confirm_remove_client,
  val sessionCacheService: SessionCacheService,
  val sessionCacheOps: SessionCacheOperationsService,
  val groupService: GroupService,
  clientService: ClientService
)(
  implicit val appConfig: AppConfig,
  ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc)
  with I18nSupport with Logging {

  import groupAction._
  import sessionAction.withSessionItem

  private val controller: ReverseCreateGroupSelectClientsController = routes.CreateGroupSelectClientsController
  private val CLIENT_PAGE_SIZE = 20
  private val REVIEW_SELECTED_PAGE_SIZE = 10

  def showSearchClients: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, _, arn) =>
      withSessionItem[String](CLIENT_FILTER_INPUT) { clientFilterTerm =>
        withSessionItem[String](CLIENT_SEARCH_INPUT) { clientSearchTerm =>
            Ok(
              search_clients(
                form = SearchAndFilterForm.form().fill(SearchFilter(clientSearchTerm, clientFilterTerm, None)),
                groupName = groupName
              )
          ).toFuture
        }
      }
    }
  }

  def submitSearchClients: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, _, _) =>
      SearchAndFilterForm
        .form()
        .bindFromRequest()
        .fold(
          formWithErrors => {
              Ok(
                search_clients(
                  formWithErrors,
                  groupName
                ),
              ).toFuture
          }, formData => {
            sessionCacheOps.saveSearch(formData.search, formData.filter).flatMap(_ => {
              Redirect(controller.showSelectClients(Some(1), Some(CLIENT_PAGE_SIZE))).toFuture
            })
          })
    }
  }

  def showSelectClients(page: Option[Int] = None, pageSize: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, _, arn) =>
      withSessionItem[String](CLIENT_FILTER_INPUT) { clientFilterTerm =>
        withSessionItem[String](CLIENT_SEARCH_INPUT) { clientSearchTerm =>
          clientService.getPaginatedClients(arn)(page.getOrElse(1), pageSize.getOrElse(CLIENT_PAGE_SIZE)).flatMap { paginatedClients =>
            if (paginatedClients.pageContent.isEmpty) {// if the search failed (no results) (APB-7378)
              withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
                Future.successful(Ok(search_clients(
                  form = SearchAndFilterForm.form().fill(SearchFilter(clientSearchTerm, clientFilterTerm, None)),
                  groupName = groupName,
                  isFailedSearch = true,
                  continueAction = if (selectedClients.exists(_.nonEmpty)) Some(routes.CreateGroupSelectClientsController.submitSelectedClients) else None
                )))
              }
            } else {
              Future.successful(Ok(
                select_paginated_clients(
                  paginatedClients.pageContent,
                  groupName,
                  form = AddClientsToGroupForm.form().fill(AddClientsToGroup(clientSearchTerm, clientFilterTerm)),
                  paginationMetaData = Some(paginatedClients.paginationMetaData))
              ))
            }
          }
        }
      }
    }
  }

  def submitSelectedClients: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, _, arn) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { maybeSelected =>
        // allows form to bind if preselected clients so we can `.saveSelectedOrFilteredClients`
        val hasPreSelected = maybeSelected.getOrElse(Seq.empty).nonEmpty
        AddClientsToGroupForm
          // if pre-selected exist will not empty error
          .form(hasPreSelected)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              for {
                paginatedClients <- clientService.getPaginatedClients(arn)(1, CLIENT_PAGE_SIZE)
              } yield
                Ok(
                  select_paginated_clients(
                    clients = paginatedClients.pageContent,
                    groupName = groupName,
                    form = formWithErrors,
                    paginationMetaData = Some(paginatedClients.paginationMetaData)
                  )
                )
            },
            formData => {
              // don't savePageOfClients if "Select all button" eg forData.submit == "SELECT_ALL"
              sessionCacheOps
                .savePageOfClientsForCreateGroup(formData)
                .flatMap(nowSelectedClients => {
                  if (formData.submit == CONTINUE_BUTTON) {
                    // check selected clients from session cache AFTER saving (removed de-selections)
                    if (nowSelectedClients.nonEmpty) {
                      Redirect(controller.showReviewSelectedClients(None, None)).toFuture
                    } else { // render page with empty client error
                      for {
                        paginatedClients <- clientService.getPaginatedClients(arn)(1, CLIENT_PAGE_SIZE)
                      } yield
                        Ok(
                          select_paginated_clients(
                            paginatedClients.pageContent,
                            groupName,
                            form = AddClientsToGroupForm.form().withError("clients", "error.select-clients.empty"),
                            paginationMetaData = Some(paginatedClients.paginationMetaData)
                          )
                        )
                    }
                  } else if (formData.submit.startsWith(PAGINATION_BUTTON)) {
                    val pageToShow = formData.submit.replace(s"${PAGINATION_BUTTON}_", "").toInt
                    Redirect(controller.showSelectClients(Some(pageToShow), Some(CLIENT_PAGE_SIZE))).toFuture
                  } else { //bad submit
                    Redirect(controller.showSearchClients()).toFuture
                  }
                }
                )
            }
          )
      }
    }
  }

  def showReviewSelectedClients(maybePage: Option[Int], maybePageSize: Option[Int]): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, _, _) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { maybeClients =>
        maybeClients.fold(
          Redirect(controller.showSearchClients()).toFuture
        )(clients => {
          val paginatedList = PaginatedListBuilder.build[DisplayClient](
            maybePage.getOrElse(1),
            maybePageSize.getOrElse(REVIEW_SELECTED_PAGE_SIZE),
            clients
          )
          sessionCacheService.get(CONFIRM_CLIENTS_SELECTED).map(mData =>
          Ok(
            review_selected(
              paginatedList.pageContent,
              groupName,
              formWithFilledValue(YesNoForm.form(), mData),
              paginationMetaData = Some(paginatedList.paginationMetaData)
            )
          ))
        }
        )
      }
    }
  }

  def showConfirmRemoveClient(clientId: Option[String]): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, _, _) =>
      withSessionItem(SELECTED_CLIENTS) { selectedClients =>
        for {
          // if clientId is not provided as a query parameter, check the CLIENT_TO_REMOVE session value.
          // This is to enable the welsh language switch to work correctly.
          maybeClientId: Option[String] <- clientId match {
            case None => sessionCacheService.get(CLIENT_TO_REMOVE).map(_.map(_.id))
            case Some(cid) => Future.successful(Some(cid))
          }
          result <- maybeClientId.flatMap(id => selectedClients.getOrElse(Seq.empty).find(_.id == id)) match {
            case None => Future.successful(Redirect(controller.showSelectClients(None, None)))
            case Some(client) =>
              sessionCacheService
                .put(CLIENT_TO_REMOVE, client)
                .flatMap(_ =>
                  Ok(confirm_remove_client(YesNoForm.form(), groupName, client)).toFuture
                )
          }
        } yield result
      }
    }
  }

  def submitConfirmRemoveClient: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, _, _) =>
      withSessionItem[DisplayClient](CLIENT_TO_REMOVE) { maybeClient =>
        withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { maybeSelectedClients =>
          if (maybeClient.isEmpty || maybeSelectedClients.isEmpty) {
            Redirect(controller.showSelectClients(None, None)).toFuture
          }
          else {
            YesNoForm
              .form("group.client.remove.error")
              .bindFromRequest()
              .fold(
                formWithErrors => {
                  Ok(confirm_remove_client(formWithErrors, groupName, maybeClient.get)).toFuture
                }, (yes: Boolean) => {
                  if (yes) {
                    val clientsMinusRemoved = maybeSelectedClients.get.filterNot(_ == maybeClient.get)
                    sessionCacheService
                      .put(SELECTED_CLIENTS, clientsMinusRemoved)
                      .map(_ => Redirect(controller.showReviewSelectedClients(None, None)))
                  }
                  else Redirect(controller.showReviewSelectedClients(None, None)).toFuture
                }
              )
          }
        }
      }
    }
  }

  def submitReviewSelectedClients(): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, _, _) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) {
        maybeClients =>
          maybeClients.fold(Redirect(controller.showSearchClients()).toFuture)(
            clients => {
              val paginatedList = PaginatedListBuilder.build[DisplayClient](1, REVIEW_SELECTED_PAGE_SIZE, clients)
              YesNoForm
                .form("group.clients.review.error")
                .bindFromRequest()
                .fold(
                  formWithErrors => {
                    Ok(
                      review_selected(
                        paginatedList.pageContent,
                        groupName,
                        formWithErrors,
                        paginationMetaData = Some(paginatedList.paginationMetaData)
                      )
                    ).toFuture
                  }, (yes: Boolean) => {
                    sessionCacheService.put(CONFIRM_CLIENTS_SELECTED, yes)
                      .flatMap { _ =>
                        if (yes) {
                          sessionCacheService
                            .deleteAll(clientFilteringKeys)
                            .map(_ => Redirect(controller.showSearchClients()))
                        } else {
                          if (clients.nonEmpty) {
                            Redirect(routes.CreateGroupSelectTeamMembersController.showSelectTeamMembers(None, None)).toFuture
                          } else { // throw empty client error (would prefer redirect to showSearchClients)
                            Ok(
                              review_selected(
                                paginatedList.pageContent,
                                groupName,
                                YesNoForm.form("group.clients.review.error").withError("answer", "group.clients.review.error.no-clients"),
                                paginationMetaData = Some(paginatedList.paginationMetaData)
                              )
                            ).toFuture
                          }
                        }
                      }
                  }
                )
            }
          )
      }
    }
  }

}
