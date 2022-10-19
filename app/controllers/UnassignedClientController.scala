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
import connectors.{AddMembersToAccessGroupRequest, AgentPermissionsConnector}
import forms._
import models.{AddClientsToGroup, DisplayClient}
import play.api.Logging
import play.api.data.FormError
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
import services.{ClientService, GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups._
import views.html.groups.manage._
import views.html.groups.unassigned_clients._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UnassignedClientController @Inject()(
                                            authAction: AuthAction,
                                            mcc: MessagesControllerComponents,
                                            groupService: GroupService,
                                            clientService: ClientService,
                                            val agentPermissionsConnector: AgentPermissionsConnector,
                                            val sessionCacheRepository: SessionCacheRepository,
                                            val sessionCacheService: SessionCacheService,
                                            unassigned_clients_list: unassigned_clients_list,
                                            review_clients_to_add: review_clients_to_add,
                                            select_groups_for_clients: select_groups_for_clients,
                                            clients_added_to_groups_complete: clients_added_to_groups_complete
                                          )
                                          (implicit val appConfig: AppConfig, ec: ExecutionContext,
                                           implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

  with I18nSupport
  with SessionBehaviour
  with Logging {

  import authAction._

  private val controller: ReverseUnassignedClientController = routes.UnassignedClientController

  def showUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        withSessionItem[String](CLIENT_FILTER_INPUT) { filterTerm =>
          withSessionItem[String](CLIENT_SEARCH_INPUT) { searchTerm =>
            withSessionItem[Boolean](HIDDEN_CLIENTS_EXIST) { maybeHiddenClients =>
              clientService.getUnassignedClients(arn).map(unassignedClients => {
                val filteredClients =
                  unassignedClients
                    .filter(dc => filterTerm.isEmpty || filterTerm.get == "" || dc.taxService.equalsIgnoreCase(filterTerm.get))
                    .filter( dc => searchTerm.isEmpty || dc.name.toLowerCase.contains(searchTerm.get.toLowerCase))
                Ok(
                  unassigned_clients_list(
                    filteredClients,
                    AddClientsToGroupForm.form().fill(
                      AddClientsToGroup(
                        maybeHiddenClients.getOrElse(false),
                        search = searchTerm,
                        filter = filterTerm,
                        clients = None)),
                    maybeHiddenClients))
              }
              )
            }
          }
        }
      }
    }
  }

  def submitAddUnassignedClients: Action[AnyContent] = Action.async { implicit request =>

    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        withSessionItem[Seq[DisplayClient]](FILTERED_CLIENTS) { maybeFilteredClients =>
          withSessionItem[Boolean](HIDDEN_CLIENTS_EXIST) { maybeHiddenClients =>
            AddClientsToGroupForm
              .form()
              .bindFromRequest()
              .fold(
                formWithErrors => {
                  for {
                    unassignedClients <- clientService.getUnassignedClients(arn)
                    _ <- if (CONTINUE_BUTTON == formWithErrors.data.get("submit")) sessionCacheService
                      .clearSelectedClients() else
                      Future.successful(())
                    result = if (maybeFilteredClients.isDefined)
                      Ok(unassigned_clients_list(unassignedClients, formWithErrors, maybeHiddenClients))
                    else
                      Ok(unassigned_clients_list(unassignedClients, formWithErrors, maybeHiddenClients))
                  } yield result
                },
                formData => {
                  clientService.saveSelectedOrFilteredClients(arn)(formData)(clientService.getMaybeUnassignedClients).map(_ =>
                    if (formData.submit == CONTINUE_BUTTON)
                      Redirect(controller.showSelectedUnassignedClients)
                    else Redirect(controller.showUnassignedClients)
                  )
                }
              )
          }
        }
      }
    }
  }

  def showSelectedUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
          selectedClients
            .fold {
              Redirect(controller.showUnassignedClients).toFuture
            } { clients =>
              Ok(
                review_clients_to_add(
                  clients = clients,
                  groupName = "",
                  form = YesNoForm.form(),
                  backUrl = Some(controller.showUnassignedClients.url),
                  continueCall = controller.submitSelectedUnassignedClients
                )
              ).toFuture
            }
        }
      }
    }
  }

  def submitSelectedUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
          selectedClients
            .fold {
              Redirect(controller.showUnassignedClients).toFuture
            } { clients =>
              YesNoForm
                .form("group.clients.review.error")
                .bindFromRequest
                .fold(
                  formWithErrors => {
                    Ok(review_clients_to_add(
                      clients,
                      "",
                      formWithErrors,
                      backUrl = Some(controller.showUnassignedClients.url),
                      continueCall = controller.submitSelectedUnassignedClients)
                    ).toFuture
                  }, (yes: Boolean) => {
                    if (yes)
                      Redirect(controller.showUnassignedClients).toFuture
                    else
                      Redirect(controller.showSelectGroupsForSelectedUnassignedClients).toFuture
                  }
                )
            }
        }
      }
    }
  }

  def showSelectGroupsForSelectedUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        groupService.groups(arn).map(groups =>
          Ok(select_groups_for_clients(SelectGroupsForm.form().fill(SelectGroups(None, None)), groups)))
      }
    }
  }

  def submitSelectGroupsForSelectedUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        SelectGroupsForm.form().bindFromRequest().fold(
          formWithErrors => {
            groupService.groups(arn).map(groups => {
              val clonedForm = formWithErrors.copy(
                errors = Seq(FormError("field-wrapper", formWithErrors.errors.head.message))
              )
              Ok(select_groups_for_clients(clonedForm, groups))
            }
            )
          }, validForm => {
            if (validForm.createNew.isDefined) Redirect(routes.CreateGroupController.showGroupName).toFuture
            else {
              for {
                allGroups <- groupService.groups(arn)
                groupsToAddTo = allGroups.filter(groupSummary => validForm.groups.get.contains(groupSummary))
                _ <- sessionCacheRepository.putSession(GROUPS_FOR_UNASSIGNED_CLIENTS, groupsToAddTo.map(_.groupName))
                selectedClients <- sessionCacheRepository.getFromSession(SELECTED_CLIENTS)
                result <- selectedClients.fold(
                  Redirect(routes.ManageGroupController.showManageGroups).toFuture
                ) { displayClients =>
                  val clients: Set[Client] = displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet
                  Future.sequence(groupsToAddTo.map { grp =>
                    //TODO: what do we do if 3 out of 4 fail to save?
                    agentPermissionsConnector.addMembersToGroup(
                      grp.groupId, AddMembersToAccessGroupRequest(clients = Some(clients))
                    )
                  }).map { _ =>
                    Redirect(controller.showConfirmClientsAddedToGroups)
                  }
                }
              } yield result

            }
          }
        )
      }
    }
  }

  def showConfirmClientsAddedToGroups: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        sessionCacheRepository.getFromSession(GROUPS_FOR_UNASSIGNED_CLIENTS).flatMap {
          case None => Future.successful(Redirect(controller.showSelectGroupsForSelectedUnassignedClients.url))
          case Some(groups) => sessionCacheRepository.deleteFromSession(SELECTED_CLIENTS).map(_ => Ok(clients_added_to_groups_complete(groups)))
        }
      }
    }
  }
}
