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
import connectors.UpdateAccessGroupRequest
import controllers.action.SessionAction
import forms._
import models.DisplayClient.format
import models.{AddClientsToGroup, DisplayClient, DisplayGroup, SearchFilter}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{ClientService, GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.manage._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageGroupClientsController @Inject()(
                                              groupAction: GroupAction,
                                              sessionAction: SessionAction,
                                              mcc: MessagesControllerComponents,
                                              clientService: ClientService,
                                              groupService: GroupService,
                                              val sessionCacheService: SessionCacheService,
                                              review_update_clients: review_update_clients,
                                              update_client_group_list: update_client_group_list,
                                              existing_clients: existing_clients,
                                              clients_update_complete: clients_update_complete
                                            )(implicit val appConfig: AppConfig, ec: ExecutionContext,
                                              implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

  with I18nSupport
  with Logging {

  import groupAction._
  import sessionAction.withSessionItem

  private val controller: ReverseManageGroupClientsController = routes.ManageGroupClientsController

  def showExistingGroupClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      val displayGroup = DisplayGroup.fromAccessGroup(group)
      val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
      searchFilter.submit.fold( // fresh page load
        Ok(existing_clients(group = displayGroup, form = SearchAndFilterForm.form()))
      )(submitButton => //either the 'filter' button or the 'clear' filter button was clicked
        submitButton match {
          case FILTER_BUTTON =>
            val searchTerm = searchFilter.search.getOrElse("")
            val filteredClients = displayGroup.clients
              .filter(_.name.toLowerCase.contains(searchTerm.toLowerCase))
              .filter(dc =>
                if (!searchFilter.filter.isDefined) true
                else {
                  val filter = searchFilter.filter.get
                  dc.taxService.equalsIgnoreCase(filter) || (filter == "TRUST" && dc.taxService.startsWith("HMRC-TERS"))
                })
            Ok(
              existing_clients(
                group = displayGroup.copy(clients = filteredClients),
                form = SearchAndFilterForm.form().fill(searchFilter)
              )
            )
          case CLEAR_BUTTON =>
            Redirect(controller.showExistingGroupClients(groupId))
        }
      ).toFuture
    }
  }

  def showManageGroupClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group =>
      withSessionItem[String](CLIENT_FILTER_INPUT) { clientFilterTerm =>
        withSessionItem[String](CLIENT_SEARCH_INPUT) { clientSearchTerm =>
          for {
            _ <- sessionCacheService.get(SELECTED_CLIENTS).map(maybeSelectedClients =>
              if (maybeSelectedClients.isEmpty) {
                val clientsInGroupAlready = group.clients.toSeq.flatten.map(DisplayClient.fromClient(_)).map(_.copy(selected = true))
                sessionCacheService.put[Seq[DisplayClient]](SELECTED_CLIENTS, clientsInGroupAlready)
              }
            )
            clients <- clientService.getFilteredClientsElseAll(group.arn)
          } yield {
            val form = AddClientsToGroupForm.form().fill(AddClientsToGroup(clientSearchTerm, clientFilterTerm, None))
            Ok(
              update_client_group_list(clients, group.groupName, form,
                formAction = controller.submitManageGroupClients(groupId),
                backUrl = Some(controller.showExistingGroupClients(groupId).url)
              )
            )
          }
        }
      }
    }
  }

  def submitManageGroupClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { maybeClients =>
        // allows form to bind if preselected clients so we can `.saveSelectedOrFilteredClients`
        val hasPreSelected = maybeClients.getOrElse(Seq.empty).nonEmpty
      AddClientsToGroupForm
        .form(hasPreSelected)
        .bindFromRequest()
        .fold(
          formWithErrors => {
            clientService.getFilteredClientsElseAll(group.arn).map { maybeFilteredClients =>
              Ok(update_client_group_list(
                maybeFilteredClients,
                group.groupName,
                formWithErrors,
                formAction = controller.submitManageGroupClients(groupId),
                backUrl = Some(controller.showExistingGroupClients(groupId).url)
              ))
            }
          },
          formData => {
            clientService
              .saveSelectedOrFilteredClients(group.arn)(formData)(clientService.getAllClients)
              .flatMap(_ =>
                if (formData.submit == CONTINUE_BUTTON) {
                  // check selected clients from session cache AFTER saving (removed de-selections)
                  val hasSelectedClients = for {
                    selectedClients <- sessionCacheService.get(SELECTED_CLIENTS)
                    // if "empty" returns Some(Vector()) so .nonEmpty on it's own returns true
                  } yield selectedClients.getOrElse(Seq.empty).nonEmpty

                  hasSelectedClients.flatMap(selectedNotEmpty => {
                    if(selectedNotEmpty) {
                      sessionCacheService.deleteAll(clientFilteringKeys).map(_ =>
                        Redirect(controller.showReviewSelectedClients(groupId))
                      )
                    } else {
                      for {
                        clients <- clientService.getFilteredClientsElseAll(group.arn)
                      } yield {
                        val form = AddClientsToGroupForm.form().withError("clients", "error.select-clients.empty")
                        Ok(
                          update_client_group_list(clients, group.groupName, form,
                            formAction = controller.submitManageGroupClients(groupId),
                            backUrl = Some(controller.showExistingGroupClients(groupId).url)
                          )
                        )
                      }
                    }
                  })
                } else Redirect(controller.showManageGroupClients(groupId)).toFuture
              )
          }
        )
      }
    }
  }

  def showReviewSelectedClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        selectedClients
          .fold {
            Redirect(controller.showManageGroupClients(groupId))
          } { clients =>
            Ok(review_update_clients(clients, group, YesNoForm.form()))
          }.toFuture
      }
    }
  }

  def submitReviewSelectedClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        selectedClients
          .fold(
            Redirect(controller.showExistingGroupClients(groupId)).toFuture
          ) { clients =>
            YesNoForm
              .form("group.clients.review.error")
              .bindFromRequest
              .fold(
                formWithErrors => {
                  Ok(review_update_clients(clients, group, formWithErrors)).toFuture
                }, (yes: Boolean) => {
                  if (yes)
                    Redirect(controller.showManageGroupClients(group._id.toString)).toFuture
                  else {
                    val toSave = clients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet
                    val updateGroupRequest = UpdateAccessGroupRequest(clients = Some(toSave))
                    groupService.updateGroup(groupId, updateGroupRequest).map(_ =>
                      Redirect(controller.showGroupClientsUpdatedConfirmation(groupId))
                    )
                  }
                }
              )
          }
      }
    }
  }

  def showGroupClientsUpdatedConfirmation(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        if (selectedClients.isDefined) {
            sessionCacheService.delete(SELECTED_CLIENTS)
              .map(_ => Ok(clients_update_complete(group.groupName)))
          }
          else Redirect(controller.showManageGroupClients(groupId)).toFuture
      }
    }
  }

}
