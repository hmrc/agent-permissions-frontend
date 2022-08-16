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
import models.DisplayClient.{format, toEnrolment}
import models.{ButtonSelect, DisplayClient, DisplayGroup, SearchFilter}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
import services.{GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups._
import views.html.groups.manage._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ManageGroupClientsController @Inject()(
     groupAction: GroupAction,
     mcc: MessagesControllerComponents,
     val agentPermissionsConnector: AgentPermissionsConnector,
     val sessionCacheRepository: SessionCacheRepository,
     val sessionCacheService: SessionCacheService,
     groupService: GroupService,
     review_clients_to_add: review_clients_to_add,
     client_group_list: client_group_list,
     existing_clients: existing_clients,
     clients_update_complete: clients_update_complete
    )(implicit val appConfig: AppConfig, ec: ExecutionContext,
      implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

  with GroupsControllerCommon
  with I18nSupport
  with SessionBehaviour
  with Logging {

  import groupAction._

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
          case "clear" =>
            Redirect(routes.ManageGroupClientsController.showExistingGroupClients(groupId))
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
              existing_clients(
                group = displayGroup.copy(clients = filteredClients),
                form = SearchAndFilterForm.form().fill(searchFilter)
              )
            )
        }
      ).toFuture
    }
  }

  def showManageGroupClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId){ group =>
      for {
        _ <- sessionCacheRepository.putSession[Seq[DisplayClient]](SELECTED_CLIENTS, DisplayClient.fromEnrolments(group.clients))
        filteredClients <- sessionCacheRepository.getFromSession[Seq[DisplayClient]](FILTERED_CLIENTS)
        maybeHiddenClients <- sessionCacheRepository.getFromSession[Boolean](HIDDEN_CLIENTS_EXIST)
        clientsForArn <- groupService.getClients(group.arn)
      } yield Ok(
        client_group_list(
          filteredClients.orElse(clientsForArn),
          group.groupName,
          maybeHiddenClients,
          AddClientsToGroupForm.form(),
          formAction = routes.ManageGroupClientsController.submitManageGroupClients(groupId),
          backUrl = Some(routes.ManageGroupClientsController.showExistingGroupClients(groupId).url)
        )
      )
      }
    }

  def submitManageGroupClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>

    val encoded = request.body.asFormUrlEncoded
    val buttonSelection: ButtonSelect = buttonClickedByUserOnFilterFormPage(encoded)

    withGroupForAuthorisedOptedAgent(groupId){ group: AccessGroup =>
      withSessionItem[Seq[DisplayClient]](FILTERED_CLIENTS) { maybeFilteredResult =>
        withSessionItem[Boolean](HIDDEN_CLIENTS_EXIST) { maybeHiddenClients =>
          AddClientsToGroupForm
            .form(buttonSelection)
            .bindFromRequest()
            .fold(
              formWithErrors => {
                for {
                  _ <- if (buttonSelection == ButtonSelect.Continue)
                    sessionCacheService.clearSelectedClients()
                  else ().toFuture
                  clients <- maybeFilteredResult.fold(groupService.getClients(group.arn))(Some(_).toFuture)
                 result <-
                    Ok(client_group_list(
                      clients,
                      group.groupName,
                      maybeHiddenClients,
                      formWithErrors,
                      formAction = routes.ManageGroupClientsController.showManageGroupClients(groupId),
                      backUrl = Some(routes.ManageGroupController.showManageGroups.url)
                    )).toFuture
                } yield result
              },
              formData => {
                groupService.saveSelectedOrFilteredClients(buttonSelection)(group.arn)(formData).flatMap(_ =>
                  if (buttonSelection == ButtonSelect.Continue) {
                    for {
                      enrolments <- sessionCacheRepository
                        .getFromSession[Seq[DisplayClient]](SELECTED_CLIENTS)
                        .map { maybeClients: Option[Seq[DisplayClient]] =>
                          maybeClients
                            .map(_.map(toEnrolment(_)))
                            .map(_.toSet)
                        }
                      groupRequest = UpdateAccessGroupRequest(clients = enrolments)
                      _ <- agentPermissionsConnector.updateGroup(groupId, groupRequest)
                      _ <- sessionCacheRepository.deleteFromSession(FILTERED_CLIENTS)
                      _ <- sessionCacheRepository.deleteFromSession(HIDDEN_CLIENTS_EXIST)
                    } yield
                      Redirect(routes.ManageGroupClientsController.showReviewSelectedClients(groupId))
                  }
                  else Redirect(routes.ManageGroupClientsController.showManageGroupClients(groupId)).toFuture
                )
              }
            )
        }
      }
    }
  }

  def showReviewSelectedClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId){group: AccessGroup =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        selectedClients
          .fold {
            Redirect(routes.ManageGroupClientsController.showManageGroupClients(groupId))
          } { clients =>
            Ok(
              review_clients_to_add(
                clients = clients,
                groupName = group.groupName,
                backUrl = Some(routes.ManageGroupClientsController.showManageGroupClients(groupId).url),
                continueCall = routes.ManageGroupClientsController.showGroupClientsUpdatedConfirmation(groupId)
              )
            )
          }
          .toFuture
      }
    }
  }

  def showGroupClientsUpdatedConfirmation(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) {group: AccessGroup =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
        if(selectedClients.isDefined) Ok(clients_update_complete(group.groupName)).toFuture
        else Redirect(routes.ManageGroupClientsController.showManageGroupClients(groupId)).toFuture
      }
    }
  }

}
