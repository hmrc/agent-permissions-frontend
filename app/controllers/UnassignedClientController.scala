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
import controllers.actions.{AuthAction, OptInStatusAction, SessionAction}
import forms._
import models.{AddClientsToGroup, DisplayClient}
import play.api.Logging
import play.api.data.{Form, FormError}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.JsNumber
import play.api.mvc._
import services.{ClientService, GroupService, SessionCacheOperationsService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentmtdidentifiers.utils.PaginatedListBuilder
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.create.clients._
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
                                            optInStatusAction: OptInStatusAction,
                                            sessionAction: SessionAction,
                                            val sessionCacheService: SessionCacheService,
                                            val sessionCacheOps: SessionCacheOperationsService,
                                            unassigned_clients_list: unassigned_clients_list,
                                            review_clients_paginated: review_clients_paginated,
                                            select_groups_for_clients: select_groups_for_clients,
                                            clients_added_to_groups_complete: clients_added_to_groups_complete
    )
    (implicit val appConfig: AppConfig, ec: ExecutionContext, implicit override val messagesApi: MessagesApi)
  extends FrontendController(mcc)

  with I18nSupport
    with Logging {

  import authAction._
  import optInStatusAction._
  import sessionAction.withSessionItem

  private val controller: ReverseUnassignedClientController = routes.UnassignedClientController

  private val UNASSIGNED_CLIENTS_PAGE_SIZE = 20

  private def renderUnassignedClients(arn: Arn, form: Form[AddClientsToGroup], page: Option[Int], pageSize: Option[Int] = None, search: Option[String] = None, filter: Option[String] = None)(implicit request: Request[_]): Future[Result] =
    for {
      // TODO considerable duplication between this code and ClientService.getPaginatedClients. Unify please at next opportunity.
      maybeSelectedClients <- sessionCacheService.get(SELECTED_CLIENTS)
      unmarkedPaginatedClients <- clientService.getUnassignedClients(arn)(page.getOrElse(1), pageSize.getOrElse(UNASSIGNED_CLIENTS_PAGE_SIZE), search = search, filter = filter)
      markedPaginatedClients =
        unmarkedPaginatedClients.copy(
        pageContent = unmarkedPaginatedClients.pageContent.map( c => if(maybeSelectedClients.isEmpty) c else c.copy(selected = maybeSelectedClients.get.map(_.id).contains(c.id)))
      )
      totalClientsSelected = maybeSelectedClients.fold(0)(_.length)
      metadataWithExtra = unmarkedPaginatedClients.paginationMetaData.copy(extra = Some(Map("totalSelected" -> JsNumber(totalClientsSelected))))  // This extra data is needed to display correct 'selected' count in front-end
      _ <- sessionCacheService.put(CURRENT_PAGE_CLIENTS, markedPaginatedClients.pageContent)
    } yield {
      val defaultFormData: AddClientsToGroup = AddClientsToGroup()
      Ok(
        unassigned_clients_list(
          markedPaginatedClients.pageContent,
          form = form.fill(form.value.getOrElse(defaultFormData).copy(search = search, filter = filter)),
          paginationMetaData = Some(metadataWithExtra)
        )
      )
    }


  def showUnassignedClients(page: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        for {
          search <- sessionCacheService.get(CLIENT_SEARCH_INPUT)
          filter <- sessionCacheService.get(CLIENT_FILTER_INPUT)
          result <- renderUnassignedClients(arn, form = AddClientsToGroupForm.form(), page = page, search = search, filter = filter)
        } yield result
      }
    }
  }

  def submitAddUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        new POSTPaginatedSearchableClientSelectHandler(sessionCacheService, sessionCacheOps) {
          val renderPage: Form[AddClientsToGroup] => Future[Result] = formData =>
            for {
              search <- sessionCacheService.get(CLIENT_SEARCH_INPUT)
              filter <- sessionCacheService.get(CLIENT_FILTER_INPUT)
              result <- renderUnassignedClients(arn, form = formData, page = Some(1), search = search, filter = filter)
            } yield result
          val reloadCall: (Option[Int], Option[String], Option[String]) => Call = {
            case (pageNumber, _, _) => controller.showUnassignedClients(pageNumber)
          }
          val onContinue: AddClientsToGroup => Future[Result] = _ => Future.successful(Redirect(controller.showSelectedUnassignedClients()))
        }.handlePost
      }
    }
  }

  def showSelectedUnassignedClients(page: Option[Int] = None, pageSize: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { selectedClients =>
          selectedClients
            .fold {
              Redirect(controller.showUnassignedClients()).toFuture
            } { clients =>
              val paginatedClients = PaginatedListBuilder.build(page = page.getOrElse(1), pageSize = pageSize.getOrElse(UNASSIGNED_CLIENTS_PAGE_SIZE), fullList = clients)

              Ok(
                review_clients_paginated(
                  clients = paginatedClients.pageContent,
                  groupName = "",
                  form = YesNoForm.form(),
                  backUrl = Some(controller.showUnassignedClients().url),
                  formAction = controller.submitSelectedUnassignedClients,
                  paginationMetaData = Some(paginatedClients.paginationMetaData)
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
        withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { maybeSelected =>
          maybeSelected
            .fold {
              Redirect(controller.showUnassignedClients()).toFuture
            } { clients =>
              YesNoForm
                .form("group.clients.review.error")
                .bindFromRequest
                .fold(
                  formWithErrors => {
                    Ok(review_clients_paginated(
                      clients,
                      "",
                      formWithErrors,
                      backUrl = Some(controller.showUnassignedClients().url),
                      formAction = controller.submitSelectedUnassignedClients)
                    ).toFuture
                  }, (yes: Boolean) => {
                    if (yes) {
                      Redirect(controller.showUnassignedClients()).toFuture
                    } else {
                      Redirect(controller.showSelectGroupsForSelectedUnassignedClients).toFuture
                    }
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
        groupService.getGroupSummaries(arn).map(groups =>
          Ok(
            select_groups_for_clients(
              SelectGroupsForm.form().fill(SelectGroups(None, None)),
              groups.filter(_.isCustomGroup()))))
      }
    }
  }

  def submitSelectGroupsForSelectedUnassignedClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInComplete(arn) { _ =>
        SelectGroupsForm.form().bindFromRequest().fold(
          formWithErrors => {
            groupService.getGroupSummaries(arn).map(groups => {
              val clonedForm = formWithErrors.copy(
                errors = Seq(FormError("field-wrapper", formWithErrors.errors.head.message))
              )
              Ok(select_groups_for_clients(clonedForm, groups))
            }
            )
          }, validForm => {
            if (validForm.createNew.isDefined) Redirect(routes.CreateGroupSelectNameController.showGroupName).toFuture
            else {
              for {
                allGroups <- groupService.getGroupSummaries(arn)
                groupsToAddTo = allGroups.filter(groupSummary => validForm.groups.get.contains(groupSummary.groupId))
                _ <- sessionCacheService.put(GROUPS_FOR_UNASSIGNED_CLIENTS, groupsToAddTo.map(_.groupName))
                selectedClients <- sessionCacheService.get(SELECTED_CLIENTS)
                result <- selectedClients.fold(
                  Redirect(routes.ManageGroupController.showManageGroups(None,None)).toFuture
                ) { displayClients =>
                  val clients: Set[Client] = displayClients.map(dc => Client(dc.enrolmentKey, dc.name)).toSet
                  Future.sequence(groupsToAddTo.map { grp =>
                    groupService.addMembersToGroup(
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
        sessionCacheService.get(GROUPS_FOR_UNASSIGNED_CLIENTS).flatMap {
          case None =>
            Future.successful(Redirect(controller.showSelectGroupsForSelectedUnassignedClients.url))
          case Some(groups) =>
            sessionCacheService
            .delete(SELECTED_CLIENTS)
            .map(_ => Ok(clients_added_to_groups_complete(groups)))
        }
      }
    }
  }
}
