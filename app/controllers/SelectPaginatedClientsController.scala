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
import controllers.actions.{AuthAction, OptInStatusAction, SessionAction}
import forms.{AddClientsToGroupForm, YesNoForm}
import models.{AddClientsToGroup, DisplayClient}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{ClientService, GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups._
import views.html.groups.create._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SelectPaginatedClientsController @Inject()(
       authAction: AuthAction,
       sessionAction: SessionAction,
       mcc: MessagesControllerComponents,
       select_paginated_clients: select_paginated_clients,
       review_clients_to_add: review_clients_to_add,
       val sessionCacheService: SessionCacheService,
       val groupService: GroupService,
       clientService: ClientService,
       optInStatusAction: OptInStatusAction,
     )(
       implicit val appConfig: AppConfig,
       ec: ExecutionContext,
       implicit override val messagesApi: MessagesApi
     ) extends FrontendController(mcc)
  with I18nSupport with Logging {

  import authAction._
  import optInStatusAction._
  import sessionAction.withSessionItem

  private val controller: ReverseSelectPaginatedClientsController = routes.SelectPaginatedClientsController

   def showSelectClients(page: Option[Int] = None , pageSize: Option[Int] = None ): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[String](CLIENT_FILTER_INPUT) { clientFilterTerm =>
        withSessionItem[String](CLIENT_SEARCH_INPUT) { clientSearchTerm =>
          withSessionItem[String](RETURN_URL) { returnUrl =>
            clientService.getPaginatedClients(arn)(page.getOrElse(1), pageSize.getOrElse(20)).map { paginatedClients =>
              Ok(
                select_paginated_clients(
                  paginatedClients.pageContent,
                  groupName,
                  backUrl = Some(returnUrl.getOrElse("/")),
                  form = AddClientsToGroupForm.form().fill(AddClientsToGroup(clientSearchTerm, clientFilterTerm)),
                  paginationMetaData = Some(paginatedClients.paginationMetaData)
                )
              )
            }
          }
        }
      }
    }
  }

  def submitSelectedClients: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[Seq[String]](SELECTED_CLIENT_IDS) { maybeSelected =>
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
              println("********form data**********")
              println (formData)
              println("**********************")
              // don't savePageOfClients if "Select all button" eg forData.submit == "SELECT_ALL"
              clientService
                .savePageOfClients(formData)
                .flatMap(_ => {
                  if (formData.submit == CONTINUE_BUTTON) {
                    // check selected clients from session cache AFTER saving (removed de-selections)
                    val hasSelectedClients = for {
                      selectedClientIds <- sessionCacheService.get(SELECTED_CLIENT_IDS)
                      // if "empty" returns Some(Vector()) so .nonEmpty on it's own returns true
                    } yield selectedClientIds.getOrElse(Seq.empty).nonEmpty
                    hasSelectedClients.flatMap(selectedNotEmpty => {
                      if (selectedNotEmpty) {
                        Redirect(controller.showReviewSelectedClients).toFuture
                      } else { // render page with empty client error
                          for {
                            paginatedClients <- clientService.getPaginatedClients(arn)(1, 20)
                            returnUrl <- sessionCacheService.get(RETURN_URL)
                          } yield
                            Ok(
                              select_paginated_clients(
                                paginatedClients.pageContent,
                                groupName,
                                backUrl = Some(returnUrl.getOrElse("/")),
                                form = AddClientsToGroupForm.form().withError("clients", "error.select-clients.empty"),
                                paginationMetaData = Some(paginatedClients.paginationMetaData)
                              )
                            )
                      }
                    })
                  } else if (formData.submit.startsWith(PAGINATION_BUTTON)) {
                    val pageToShow = formData.submit.replace(s"${PAGINATION_BUTTON}_", "").toInt
                    Redirect(controller.showSelectClients(Some(pageToShow), Some(20))).toFuture
                  } else {
                    Redirect(controller.showSelectClients(None, None)).toFuture
                  }
                }
                )
            }
          )
      }
    }
  }

  def showReviewSelectedClients: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[Seq[String]](SELECTED_CLIENT_IDS) { maybeClients =>
          maybeClients.fold(Redirect(controller.showSelectClients(None, None)).toFuture)(_ =>
            // need to call BE for '1st page' display clients using the selected ids
            clientService.getPaginatedClients(arn)(1, 20).flatMap { paginatedClients =>
              // include pagination
              Ok(review_clients_to_add(Seq.empty, groupName, YesNoForm.form())).toFuture
        })
      }
    }
  }

  def submitReviewSelectedClients(): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, _) =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) {
        maybeClients =>
          maybeClients.fold(Redirect(controller.showSelectClients(None, None)).toFuture)(
            clients =>
              YesNoForm
                .form("group.clients.review.error")
                .bindFromRequest
                .fold(
                  formWithErrors => {
                    Ok(review_clients_to_add(clients, groupName, formWithErrors)).toFuture
                  }, (yes: Boolean) => {
                    if (yes)
                      sessionCacheService.deleteAll(clientFilteringKeys).map(_ =>
                        Redirect(controller.showSelectClients(None, None))
                      )
                    else {
                      sessionCacheService.get(RETURN_URL)
                        .map(returnUrl =>
                          returnUrl.fold(Redirect("/"))(url => {
                            sessionCacheService.delete(RETURN_URL)
                            Redirect(url)
                          })
                        )
                    }
                  }
                )
          )
      }
    }
  }

  private def withGroupNameForAuthorisedOptedAgent(body: (String, Arn) => Future[Result])
                                                  (implicit ec: ExecutionContext, request: MessagesRequest[AnyContent], appConfig: AppConfig): Future[Result] = {
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        println("******************")
        val groupName = maybeGroupName.getOrElse("Carrots")
        println(groupName)
        println("******************")
        body(groupName, arn)
      }
    }
  }

}
