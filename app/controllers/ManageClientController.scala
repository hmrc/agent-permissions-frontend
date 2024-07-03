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
import controllers.actions.{AuthAction, OptInStatusAction}
import forms.{ClientReferenceForm, SearchAndFilterForm}
import models.SearchFilter
import org.apache.pekko.Done
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{ClientService, GroupService, SessionCacheService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.group_member_details._
import views.html.group_member_details.add_groups_to_client.client_not_found

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ManageClientController @Inject() (
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  val sessionCacheService: SessionCacheService,
  groupService: GroupService,
  clientService: ClientService,
  manage_clients_list: manage_clients_list,
  client_details: client_details,
  update_client_reference: update_client_reference,
  update_client_reference_complete: update_client_reference_complete,
  client_not_found: client_not_found,
  optInStatusAction: OptInStatusAction
)(
  implicit val appConfig: AppConfig,
  ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc) with I18nSupport with Logging {

  import authAction._
  import optInStatusAction._

  val controller: ReverseManageClientController = routes.ManageClientController

  // All clients does not include IRV
  def showPageOfClients(page: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        val eventualTuple = for {
          search  <- sessionCacheService.get(CLIENT_SEARCH_INPUT)
          filter  <- sessionCacheService.get(CLIENT_FILTER_INPUT)
          clients <- clientService.getPaginatedClients(arn)(page.getOrElse(1), 10)
        } yield (search, filter, clients)
        eventualTuple.map { tuple =>
          val (search, filter, clients) = (tuple._1, tuple._2, tuple._3)
          val form = SearchAndFilterForm.form().fill(SearchFilter(search, filter, None))
          Ok(manage_clients_list(clients, form))
        }
      }
    }
  }

  def submitPageOfClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
        searchFilter.submit.fold(
          Redirect(controller.showPageOfClients(None)).toFuture
        ) {
          case CLEAR_BUTTON =>
            val eventualDone = for {
              _ <- sessionCacheService.delete(CLIENT_SEARCH_INPUT)
              _ <- sessionCacheService.delete(CLIENT_FILTER_INPUT)
            } yield Done
            eventualDone.map(_ => Redirect(controller.showPageOfClients(None)))
          case FILTER_BUTTON =>
            sessionCacheService
              .put(CLIENT_SEARCH_INPUT, searchFilter.search.getOrElse(""))
              .flatMap(_ => sessionCacheService.put(CLIENT_FILTER_INPUT, searchFilter.filter.getOrElse("")))
              .map(_ => Redirect(controller.showPageOfClients(None)))
        }
      }
    }
  }

  def showClientDetails(clientId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        clientService
          .getClient(arn)(clientId)
          .flatMap { maybeClient =>
            maybeClient.fold(
              Future.successful(NotFound(client_not_found()))
            )(client =>
              groupService.groupSummariesForClient(arn, client).map(groups => Ok(client_details(client, groups)))
            )
          }
      }
    }
  }

  def showUpdateClientReference(clientId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        clientService
          .getClient(arn)(clientId)
          .map {
            case Some(client) =>
              Ok(
                update_client_reference(
                  client = client,
                  form = ClientReferenceForm.form().fill(client.name)
                )
              )
            case None => throw new RuntimeException("client reference supplied did not match any client")
          }
      }
    }
  }

  def submitUpdateClientReference(clientId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        clientService.lookupClient(arn)(clientId).map {
          case Some(client) =>
            ClientReferenceForm
              .form()
              .bindFromRequest()
              .fold(
                formWithErrors => Ok(update_client_reference(client, formWithErrors)),
                (newName: String) => {
                  for {
                    _ <- sessionCacheService.put[String](CLIENT_REFERENCE, newName)
                    _ <- clientService.updateClientReference(arn, client, newName)
                  } yield ()
                  Redirect(routes.ManageClientController.showClientReferenceUpdatedComplete(clientId))
                }
              )
          case None => throw new RuntimeException("client reference supplied did not match any client")
        }
      }
    }
  }

  def showClientReferenceUpdatedComplete(clientId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        clientService
          .lookupClient(arn)(clientId)
          .flatMap(maybeClient =>
            maybeClient.fold(
              Future.successful(NotFound(client_not_found()))
            )(client =>
              sessionCacheService
                .get(CLIENT_REFERENCE)
                .map(newName => Ok(update_client_reference_complete(client, clientRef = newName.get)))
            )
          )

      }
    }
  }

}
