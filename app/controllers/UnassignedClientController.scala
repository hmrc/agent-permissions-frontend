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
import connectors.{AddMembersToAccessGroupRequest, AgentPermissionsConnector, GroupSummary}
import forms._
import models.{ButtonSelect, DisplayClient}
import play.api.Logging
import play.api.data.FormError
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
import services.{ClientService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups._
import views.html.groups.manage._
import views.html.groups.unassigned_clients.select_groups_for_clients

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UnassignedClientController @Inject()(
     authAction: AuthAction,
     mcc: MessagesControllerComponents,
     clientService: ClientService,
     val agentPermissionsConnector: AgentPermissionsConnector,
     val sessionCacheRepository: SessionCacheRepository,
     val sessionCacheService: SessionCacheService,
     dashboard: dashboard,
     review_clients_to_add: review_clients_to_add,
     select_groups_for_clients: select_groups_for_clients,
     clients_added_to_groups_complete: clients_added_to_groups_complete
    )
                                          (implicit val appConfig: AppConfig, ec: ExecutionContext,
    implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

  with GroupsControllerCommon
  with I18nSupport
  with SessionBehaviour
  with Logging {

  import authAction._

  private val controller: ReverseUnassignedClientController = routes.UnassignedClientController

  def showSelectedUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
          selectedClients
            .fold {
              Redirect(routes.ManageGroupController.showManageGroups).toFuture
            } { clients => Ok(
                review_clients_to_add(
                  clients = clients,
                  groupName = "",
                  form = YesNoForm.form(),
                  backUrl = Some(s"${routes.ManageGroupController.showManageGroups.url}#unassigned-clients"),
                  // TODO needs to be updated to a submit for the yes no form
                  continueCall = controller.showSelectGroupsForSelectedUnassignedClients
                )
              ).toFuture
            }
        }
      }
    }
  }

  def showSelectGroupsForSelectedUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        getGroupSummaries(arn).map(tuple =>
        Ok(select_groups_for_clients(SelectGroupsForm
          .form()
          .fill(SelectGroups(None, None)), tuple._1)))
      }
    }
  }

  def submitSelectGroupsForSelectedUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        SelectGroupsForm.form().bindFromRequest().fold(
          formWithErrors => {
            getGroupSummaries(arn).map( tuple => {
              val clonedForm = formWithErrors.copy(
                errors = Seq(FormError("field-wrapper", formWithErrors.errors.head.message))
              )
              Ok(select_groups_for_clients(clonedForm, tuple._1))
            }
            )
          }, validForm => {
            if(validForm.createNew.isDefined) Redirect(routes.CreateGroupController.showGroupName).toFuture
            else {
              for {
                summaries <- getGroupSummaries(arn)
                groupsToAddTo = summaries._1
                  .filter(groupSummary => validForm.groups.get.contains(groupSummary.groupId))
                _ <- sessionCacheRepository.putSession(GROUPS_FOR_UNASSIGNED_CLIENTS, groupsToAddTo.map(_.groupName))
                selectedClients <- sessionCacheRepository.getFromSession(SELECTED_CLIENTS)
                result <- selectedClients.fold(
                  Redirect(routes.ManageGroupController.showManageGroups).toFuture
                )
                { displayClients =>
                  val clients: Set[Client] = displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet
                  Future.sequence( groupsToAddTo.map{ grp =>
                    //TODO: what do we do if 3 out of 4 fail to save?
                    agentPermissionsConnector.addMembersToGroup (
                      grp.groupId, AddMembersToAccessGroupRequest(clients = Some(clients))
                    )
                  }).map{ _ =>
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

  def showConfirmClientsAddedToGroups: Action[AnyContent] = Action.async {implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        sessionCacheRepository.getFromSession(GROUPS_FOR_UNASSIGNED_CLIENTS).flatMap {
          case None => Future.successful(Redirect(controller.showSelectGroupsForSelectedUnassignedClients.url))
          case Some(groups) => sessionCacheRepository.deleteFromSession(SELECTED_CLIENTS).map(_ => Ok(clients_added_to_groups_complete(groups)))
        }
      }
    }
  }

  def submitAddUnassignedClients: Action[AnyContent] = Action.async { implicit request =>

    val encoded = request.body.asFormUrlEncoded
    val buttonSelection: ButtonSelect = buttonClickedByUserOnFilterFormPage(encoded)

    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        withSessionItem[Seq[DisplayClient]](FILTERED_CLIENTS) { maybeFilteredClients =>
          withSessionItem[Boolean](HIDDEN_CLIENTS_EXIST) { maybeHiddenClients =>
            AddClientsToGroupForm
              .form(buttonSelection)
              .bindFromRequest()
              .fold(
                formWithErrors => {
                  for {
                    groupSummaries <- agentPermissionsConnector.groupsSummaries(arn)
                    _ <- if (buttonSelection == ButtonSelect.Continue) sessionCacheService.clearSelectedClients() else Future.successful(())
                    result = if (maybeFilteredClients.isDefined)
                      Ok(dashboard(groupSummaries.getOrElse(Seq.empty[GroupSummary], Seq.empty[DisplayClient]), formWithErrors, FilterByGroupNameForm.form,maybeHiddenClients, showUnassignedClients = true))
                    else
                      Ok(dashboard(groupSummaries.getOrElse(Seq.empty[GroupSummary], Seq.empty[DisplayClient]), formWithErrors, FilterByGroupNameForm.form, maybeHiddenClients, showUnassignedClients = true))
                  } yield result
                },
                formData => {
                  clientService.saveSelectedOrFilteredClients(buttonSelection)(arn)(formData)(clientService.getUnassignedClients).map(_ =>
                    if(buttonSelection == ButtonSelect.Continue)
                  Redirect(controller.showSelectedUnassignedClients)
                    else Redirect(s"${routes.ManageGroupController.showManageGroups}#unassigned-clients")
                  )
                }
              )
          }
        }
      }
    }
  }

  private def getGroupSummaries(arn: Arn)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[(Seq[GroupSummary], Seq[DisplayClient])] = {
    agentPermissionsConnector.groupsSummaries(arn).map { maybeSummaries =>
      maybeSummaries.getOrElse((Seq.empty[GroupSummary], Seq.empty[DisplayClient]))
    }
  }
}
