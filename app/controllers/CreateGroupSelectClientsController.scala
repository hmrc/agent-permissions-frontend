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
import services.{ClientService, GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.PaginationMetaData
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups._
import views.html.groups.create.clients._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class CreateGroupSelectClientsController @Inject()
(
  groupAction: GroupAction,
  sessionAction: SessionAction,
  mcc: MessagesControllerComponents,
  search_clients: search_clients,
  select_paginated_clients: select_paginated_clients,
  review_clients_paginated: review_clients_paginated,
  review_clients_to_add: review_clients_to_add,
  val sessionCacheService: SessionCacheService,
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


  def showSearchClients: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, _, _) =>
      withSessionItem[String](CLIENT_FILTER_INPUT) { clientFilterTerm =>
        withSessionItem[String](CLIENT_SEARCH_INPUT) { clientSearchTerm =>
          Ok(
            search_clients(
              form = SearchAndFilterForm.form().fill(SearchFilter(clientSearchTerm, clientFilterTerm, None)),
              groupName = groupName,
              backUrl = Some(controllers.routes.CreateGroupSelectNameController.showConfirmGroupName.url)
            )
          ).toFuture
        }
      }
    }
  }

  def submitSearchClients: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, _, arn) =>
      SearchAndFilterForm
        .form()
        .bindFromRequest
        .fold(
          formWithErrors => {
            Ok(search_clients(formWithErrors, groupName, Some(controllers.routes.CreateGroupSelectNameController.showConfirmGroupName.url))).toFuture
          }, formData => {
            clientService.saveSearch(formData.search, formData.filter).flatMap(_ => {
              Redirect(controller.showSelectClients(Some(1), Some(20))).toFuture
            })
          })
    }
  }

  def showSelectClients(page: Option[Int] = None, pageSize: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, _, arn) =>
      withSessionItem[String](CLIENT_FILTER_INPUT) { clientFilterTerm =>
        withSessionItem[String](CLIENT_SEARCH_INPUT) { clientSearchTerm =>
          clientService.getPaginatedClients(arn)(page.getOrElse(1), pageSize.getOrElse(20)).map { paginatedClients =>
            Ok(
              select_paginated_clients(
                paginatedClients.pageContent,
                groupName,
                backUrl = Some(controller.showSearchClients.url),
                form = AddClientsToGroupForm.form().fill(AddClientsToGroup(clientSearchTerm, clientFilterTerm)),
                paginationMetaData = Some(paginatedClients.paginationMetaData)
              )
            )
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
                paginatedClients <- clientService.getPaginatedClients(arn)(1, 20)
              } yield
                Ok(
                  select_paginated_clients(
                    clients = paginatedClients.pageContent,
                    groupName = groupName,
                    form = formWithErrors,
                    paginationMetaData = Some(paginatedClients.paginationMetaData))
                )
            },
            formData => {
              // don't savePageOfClients if "Select all button" eg forData.submit == "SELECT_ALL"
              clientService
                .savePageOfClients(formData)
                .flatMap(nowSelectedClients => {
                  if (formData.submit == CONTINUE_BUTTON) {
                    // check selected clients from session cache AFTER saving (removed de-selections)
                    if (nowSelectedClients.nonEmpty) {
                      Redirect(controller.showReviewSelectedClients(None, None)).toFuture
                    } else { // render page with empty client error
                      for {
                        paginatedClients <- clientService.getPaginatedClients(arn)(1, 20)
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
                    Redirect(controller.showSelectClients(Some(pageToShow), Some(20))).toFuture
                  } else { //bad submit
                    Redirect(controller.showSearchClients).toFuture
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
          Redirect(controller.showSearchClients).toFuture
        )(clients => {
          val pageSize = maybePageSize.getOrElse(10)
          val page = maybePage.getOrElse(1)
          val firstMemberInPage = (page - 1) * pageSize
          val lastMemberInPage = page * pageSize
          val currentPageOfClients = clients.slice(firstMemberInPage, lastMemberInPage)
          val numPages = Math.ceil(clients.length.toDouble / maybePageSize.getOrElse(10).toDouble).toInt
          val meta = PaginationMetaData(
            page == numPages,
            page == 1,
            clients.length,
            numPages,
            pageSize,
            page,
            currentPageOfClients.length
          )
          Ok(
            review_clients_paginated(
              currentPageOfClients,
              groupName,
              YesNoForm.form(),
              backUrl = Some(controller.showSelectClients(None, None).url),
              formAction = controller.submitReviewSelectedClients,
              paginationMetaData = Some(meta)
            )
          ).toFuture
        }
        )
      }
    }
  }

  def submitReviewSelectedClients(): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName,_,_) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) {
        maybeClients =>
          maybeClients.fold(Redirect(controller.showSearchClients).toFuture)(
            clients =>
              YesNoForm
                .form("group.clients.review.error")
                .bindFromRequest
                .fold(
                  formWithErrors => {
                    Ok(review_clients_to_add(clients, groupName, formWithErrors)).toFuture
                  }, (yes: Boolean) => {
                    if (yes) {
                      sessionCacheService
                        .deleteAll(clientFilteringKeys)
                        .map(_ => Redirect(controller.showSearchClients))
                    } else {
                      Redirect(routes.CreateGroupSelectTeamMembersController.showSelectTeamMembers(None, None)).toFuture
                    }
                  }
                )
          )
      }
    }
  }

}
