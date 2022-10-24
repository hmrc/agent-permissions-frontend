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
import connectors.{AgentPermissionsConnector, UpdateAccessGroupRequest}
import forms._
import models.DisplayClient.format
import models.{AddClientsToGroup, DisplayClient, DisplayGroup, SearchFilter}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
import services.{ClientService, GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.manage._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageGroupClientsController @Inject()(
     groupAction: GroupAction,
     mcc: MessagesControllerComponents,
     clientService: ClientService,
     val agentPermissionsConnector: AgentPermissionsConnector,
     groupService: GroupService,
     val sessionCacheRepository: SessionCacheRepository,
     val sessionCacheService: SessionCacheService,
     review_update_clients: review_update_clients,
     update_client_group_list: update_client_group_list,
     existing_clients: existing_clients,
     clients_update_complete: clients_update_complete
    )(implicit val appConfig: AppConfig, ec: ExecutionContext,
      implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

    with I18nSupport
  with SessionBehaviour
  with Logging {

  import groupAction._

  private val controller: ReverseManageGroupClientsController = routes.ManageGroupClientsController

  def showExistingGroupClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      val displayGroup = DisplayGroup.fromAccessGroup(group)
      val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
      searchFilter.submit.fold(
        //no filter/clear was applied
        Ok(existing_clients(group = displayGroup, form = SearchAndFilterForm.form()))
      )(submitButton =>
        //either the 'filter' button or the 'clear' filter button was clicked
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
    withGroupForAuthorisedOptedAgent(groupId){ group =>
      withSessionItem[String](CLIENT_FILTER_INPUT) { clientFilterTerm =>
        withSessionItem[String](CLIENT_SEARCH_INPUT) { clientSearchTerm =>
          for {
            _ <- sessionCacheRepository.putSession[Seq[DisplayClient]](SELECTED_CLIENTS,
              group.clients.toSeq.flatten.map(DisplayClient.fromClient(_)).map(_.copy(selected = true)))
            clients <- clientService.getFilteredClientsElseAll(group.arn)
          } yield Ok(
            update_client_group_list(
              clients,
              group.groupName,
              AddClientsToGroupForm.form().fill(
                AddClientsToGroup(
                  search = clientSearchTerm,
                  filter = clientFilterTerm,
                  clients = None)),
              formAction = controller.submitManageGroupClients(groupId),
              backUrl = Some(controller.showExistingGroupClients(groupId).url)
            )
          )
        }
      }
    }
  }

  def submitManageGroupClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId){ group: AccessGroup =>
          AddClientsToGroupForm
            .form()
            .bindFromRequest()
            .fold(
              formWithErrors => {
                clientService.getFilteredClientsElseAll(group.arn).map{maybeFilteredClients =>
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
                    for {
                      maybeSelectedClients <- sessionCacheRepository.getFromSession[Seq[DisplayClient]](SELECTED_CLIENTS)
                      clientsToSaveToGroup = maybeSelectedClients
                            .map(_.map(dc => Client(dc.enrolmentKey, dc.name)))
                            .map(_.toSet)

                      groupRequest = UpdateAccessGroupRequest(clients = clientsToSaveToGroup)
                      _ <- groupService.updateGroup(groupId, groupRequest)
                      _ <- sessionCacheRepository.deleteFromSession(FILTERED_CLIENTS)
                      _ <- sessionCacheRepository.deleteFromSession(CLIENT_FILTER_INPUT)
                      _ <- sessionCacheRepository.deleteFromSession(CLIENT_SEARCH_INPUT)
                    } yield
                      Redirect(controller.showReviewSelectedClients(groupId))
                  }
                  else Redirect(controller.showManageGroupClients(groupId)).toFuture
                )
              }
            )
    }
  }

  def showReviewSelectedClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId){group: AccessGroup =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        selectedClients
          .fold {
            Redirect(controller.showManageGroupClients(groupId))
          } { clients =>
            Ok(
              review_update_clients(
                clients = clients,
                group = group,
                form = YesNoForm.form()
              )
            )
          }
          .toFuture
      }
    }
  }

  def submitReviewSelectedClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId){ group: AccessGroup =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        selectedClients
          .fold(
            Redirect(controller.showExistingGroupClients(groupId)).toFuture
          ){ clients =>
            YesNoForm
              .form("group.clients.review.error")
              .bindFromRequest
              .fold(
                formWithErrors =>{
                  Ok(review_update_clients(clients, group, formWithErrors)).toFuture
                }, (yes: Boolean) => {
                  if (yes)
                    Redirect(controller.showManageGroupClients(group._id.toString)).toFuture
                  else
                    Redirect(controller.showGroupClientsUpdatedConfirmation(groupId)).toFuture
                }
              )
          }
      }
    }
  }

  def showGroupClientsUpdatedConfirmation(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) {group: AccessGroup =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        sessionCacheService.clearSelectedClients().map(_ =>
        if(selectedClients.isDefined) Ok(clients_update_complete(group.groupName))
        else Redirect(controller.showManageGroupClients(groupId))
        )
      }
    }
  }

}
