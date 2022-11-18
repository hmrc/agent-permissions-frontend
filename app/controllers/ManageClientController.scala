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
import controllers.actions.{AuthAction, OptInStatusAction}
import forms.{ClientReferenceForm, SearchAndFilterForm}
import models.SearchFilter
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{ClientService, GroupService, SessionCacheService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.group_member_details._
import views.html.groups.add_groups_to_client.client_not_found

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ManageClientController @Inject()(
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
    optInStatusAction: OptInStatusAction)
  (implicit val appConfig: AppConfig, ec: ExecutionContext,
   implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

  with I18nSupport
    with Logging {

  import authAction._
  import optInStatusAction._

  // All clients does not include IRV
  def showAllClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        clientService.getAllClients(arn).flatMap { clients =>
          val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
          searchFilter.submit.fold(
            //no filter/clear was applied
            Ok(manage_clients_list(
              clients,
              SearchAndFilterForm.form()
            )).toFuture
          )({
            //clear/filter buttons pressed
            case CLEAR_BUTTON =>
              Redirect(routes.ManageClientController.showAllClients).toFuture
            case FILTER_BUTTON =>
              val filteredClients = clients
                .filter(_.name.toLowerCase.contains(searchFilter.search.getOrElse("").toLowerCase))
                .filter(dc =>
                  if (searchFilter.filter.isEmpty) true
                  else {
                    val filter = searchFilter.filter.get
                    dc.taxService.equalsIgnoreCase(filter) || (filter == "TRUST" && dc.taxService.startsWith("HMRC-TERS"))
                  })
              Ok(
                manage_clients_list(filteredClients, SearchAndFilterForm.form().fill(searchFilter))).toFuture
          })
        }
      }
    }
  }

  def showClientDetails(clientId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        clientService.lookupClient(arn)(clientId).flatMap { maybeClient =>
          maybeClient.fold(
            Future.successful(NotFound(client_not_found()))
          )(client =>
            groupService.groupSummariesForClient(arn, client).map ( groups =>
              Ok(client_details(client,  groups))
            )
          )
        }
      }
    }
  }

  def showUpdateClientReference(clientId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        clientService.lookupClient(arn)(clientId).map {
          case Some(client) =>
            Ok(update_client_reference(
              client = client,
              form = ClientReferenceForm.form().fill(client.name)
            ))
          case None => throw new RuntimeException("client reference supplied did not match any client")
        }
      }
    }
  }

  def submitUpdateClientReference(clientId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        clientService.lookupClient(arn)(clientId).map
        {
          case Some(client) =>
            ClientReferenceForm.form()
              .bindFromRequest()
              .fold(
                formWithErrors => {
                  Ok(
                    update_client_reference(
                      client,
                      formWithErrors))
                },
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
              sessionCacheService.get(CLIENT_REFERENCE).map(newName =>
                Ok(update_client_reference_complete(
                  client,
                  clientRef = newName.get
                ))
              )
            )
          )

      }
    }
  }

}
