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
import connectors.UpdateAccessGroupRequest
import controllers.actions.{GroupAction, SessionAction}
import forms._
import models.DisplayClient.format
import models.{AddClientsToGroup, DisplayClient, SearchFilter}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{ClientService, GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.{TaxServiceAccessGroup => TaxGroup, AccessGroupSummary => GroupSummary, _}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.manage._
import views.html.groups.manage.clients._
import views.html.groups.create.clients.search_clients

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
                                              search_clients: search_clients,
                                              existing_tax_group_clients: existing_tax_group_clients,
                                              clients_update_complete: clients_update_complete
                                            )(implicit val appConfig: AppConfig, ec: ExecutionContext,
                                              implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

  with I18nSupport
  with Logging {

  import groupAction._
  import sessionAction.withSessionItem

  private val controller: ReverseManageGroupClientsController = routes.ManageGroupClientsController

  // custom clients
  def showExistingGroupClients(groupId: String, page: Option[Int] = None, pageSize: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withSummaryForAuthorisedOptedAgent(groupId) { summary: GroupSummary =>
      val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
      searchFilter.submit.fold( // fresh page load or pagination reload
        groupService.getPaginatedClientsForCustomGroup(groupId)(page.getOrElse(1), pageSize.getOrElse(20)).map({ paginatedList: (Seq[DisplayClient], PaginationMetaData) =>
          Ok(existing_clients(
            group = summary,
            groupClients = paginatedList._1,
            form = SearchAndFilterForm.form(), // TODO fill form when reloading page via pagination
            paginationMetaData = Some(paginatedList._2)
          ))
        })
      ) { // a button was clicked
       case FILTER_BUTTON =>
         for {
           _ <- sessionCacheService.put(CLIENT_SEARCH_INPUT, searchFilter.search.getOrElse(""))
           _ <- sessionCacheService.put(CLIENT_FILTER_INPUT, searchFilter.filter.getOrElse(""))
           paginatedList <- groupService.getPaginatedClientsForCustomGroup(groupId)(1, pageSize.getOrElse(20))
         } yield Ok(existing_clients(
              group = summary,
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

   // TODO move to ManageTaxGroupClientsController? View only atm, and v similar to showExistingGroupClients
    def showTaxGroupClients(groupId: String, page: Option[Int] = None, pageSize: Option[Int] = None) : Action[AnyContent] = Action.async { implicit request =>
    withTaxGroupForAuthorisedOptedAgent(groupId) { group: TaxGroup =>
      val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
      searchFilter.submit.fold( // fresh page load or pagination reload
      for {
        _ <- sessionCacheService.put(CLIENT_FILTER_INPUT, if (group.service == "HMRC-TERS") {"TRUST"} else {group.service})
        paginatedClients <- clientService.getPaginatedClients(group.arn)(page.getOrElse(1), pageSize.getOrElse(20))
      } yield Ok(existing_tax_group_clients(
          group = GroupSummary.convertTaxServiceGroup(group),
          groupClients = paginatedClients.pageContent,
          form = SearchAndFilterForm.form(), // TODO fill form when reloading page via pagination
          paginationMetaData = Some(paginatedClients.paginationMetaData)
        ))
      ) { // a button was clicked
        case FILTER_BUTTON =>
          for {
            _ <- sessionCacheService.put(CLIENT_SEARCH_INPUT, searchFilter.search.getOrElse(""))
            _ <- sessionCacheService.put(CLIENT_FILTER_INPUT, if (group.service == "HMRC-TERS") {"TRUST"} else {group.service})
            paginatedClients <- clientService.getPaginatedClients(group.arn)(page.getOrElse(1), pageSize.getOrElse(20))
          } yield Ok(existing_tax_group_clients(
            group = GroupSummary.convertTaxServiceGroup(group),
            groupClients = paginatedClients.pageContent,
            form = SearchAndFilterForm.form().fill(searchFilter),
            paginationMetaData = Some(paginatedClients.paginationMetaData)
          ))
        case CLEAR_BUTTON =>
          sessionCacheService.delete(CLIENT_SEARCH_INPUT).map(_ =>
            Redirect(controller.showTaxGroupClients(groupId, Some(1), Some(20)))
          )
        case button =>
          if (button.startsWith(PAGINATION_BUTTON)) {
            val pageToShow = button.replace(s"${PAGINATION_BUTTON}_", "").toInt
            Redirect(controller.showTaxGroupClients(groupId, Some(pageToShow), Some(20))).toFuture
          } else { // bad submit
            Redirect(controller.showTaxGroupClients(groupId, Some(1), Some(20))).toFuture
          }
      }
    }
  }

  // or remove for now...?
  def showSearchClientsToAdd(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withSummaryForAuthorisedOptedAgent(groupId) { summary: GroupSummary =>
      withSessionItem[String](CLIENT_FILTER_INPUT) { clientFilterTerm =>
        withSessionItem[String](CLIENT_SEARCH_INPUT) { clientSearchTerm =>
          Ok(
            search_clients(
              form = SearchAndFilterForm.form().fill(SearchFilter(clientSearchTerm, clientFilterTerm, None)),
              groupName = summary.groupName,
              backUrl = Some(controller.showExistingGroupClients(groupId, None, None).url)
            )
          ).toFuture
        }
      }
    }
  }

  def submitSearchClientsToAdd(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withSummaryForAuthorisedOptedAgent(groupId) { summary: GroupSummary =>
      SearchAndFilterForm
        .form()
        .bindFromRequest
        .fold(
          formWithErrors => {
            Ok(search_clients(formWithErrors, summary.groupName, Some(controller.showExistingGroupClients(groupId, None, None).url))).toFuture
          }, formData => {
            clientService.saveSearch(formData.search, formData.filter).flatMap(_ => {
              Redirect(controller.showManageGroupClients(groupId)).toFuture
            })
          })
    }
  }

  // Needs new BE endpoint
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
                backUrl = Some(controller.showExistingGroupClients(groupId, None, None).url)
              )
            )
          }
        }
      }
    }
  }

  def submitManageGroupClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupForAuthorisedOptedAgent(groupId) { group: AccessGroup =>
      withSessionItem[Seq[DisplayClient]](SELECTED_CLIENTS) { maybeSelected =>
        // allows form to bind if preselected clients so we can `.saveSelectedOrFilteredClients`
        val hasPreSelected = maybeSelected.getOrElse(Seq.empty).nonEmpty
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
                backUrl = Some(controller.showExistingGroupClients(groupId, None, None).url)
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
                            backUrl = Some(controller.showExistingGroupClients(groupId, None, None).url)
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
            Redirect(controller.showExistingGroupClients(groupId, None, None)).toFuture
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
