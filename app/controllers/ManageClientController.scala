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
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector}
import forms.{ClientReferenceForm, SearchAndFilterForm}
import models.SearchFilter
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
import services.{ClientService, GroupService, SessionCacheService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.group_member_details._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageClientController @Inject()(
     authAction: AuthAction,
     mcc: MessagesControllerComponents,
     groupService: GroupService,
     clientService: ClientService,
     manage_clients_list: manage_clients_list,
     client_details: client_details,
     update_client_reference: update_client_reference,
     update_client_reference_complete: update_client_reference_complete,
     val agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
     val agentPermissionsConnector: AgentPermissionsConnector,
     val sessionCacheRepository: SessionCacheRepository,
     val sessionCacheService: SessionCacheService)
                                      (implicit val appConfig: AppConfig, ec: ExecutionContext,
    implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

  with GroupsControllerCommon
  with I18nSupport
  with SessionBehaviour
  with Logging {

  import authAction._

  // All clients does not include IRV
  def showAllClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        clientService.getAllClients(arn).flatMap { maybeClients =>
          val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
          searchFilter.submit.fold(
            //no filter/clear was applied
            Ok(manage_clients_list(
                maybeClients,
                SearchAndFilterForm.form()
            )).toFuture
          )({
            //clear/filter buttons pressed
            case "clear" =>
              Redirect(routes.ManageClientController.showAllClients).toFuture
            case "filter" =>
              val filteredClients = maybeClients.get
                .filter(_.name.toLowerCase.contains(searchFilter.search.getOrElse("").toLowerCase))
                .filter(dc =>
                  if (searchFilter.filter.isEmpty) true
                  else {
                    val filter = searchFilter.filter.get
                    dc.taxService.equalsIgnoreCase(filter) || (filter == "TRUST" && dc.taxService.startsWith("HMRC-TERS"))
                  })
              Ok(
                manage_clients_list(
                  Some(filteredClients),
                  SearchAndFilterForm.form().fill(searchFilter)
                )).toFuture
          })
        }
      }
    }
  }

  def showClientDetails(clientId :String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        clientService.lookupClient(arn)(clientId).flatMap {
          case Some(client) =>
          groupService.groupSummariesForClient(arn, client).map { maybeGroups =>
            Ok(client_details(
              client = client,
              clientGroups = maybeGroups
            ))
          }
        }
      }
    }
  }

  def showUpdateClientReference(clientId :String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        clientService.lookupClient(arn)(clientId).map{
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

  def submitUpdateClientReference(clientId :String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        clientService.lookupClient(arn)(clientId).map {
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
                    _ <- sessionCacheRepository.putSession[String](CLIENT_REFERENCE, newName)
                    _ <- clientService.updateClientReference(arn, client, newName)
                  } yield ()
                  Redirect(routes.ManageClientController.showClientReferenceUpdatedComplete(clientId))
                }
              )
        }
      }
    }
  }

  def showClientReferenceUpdatedComplete(clientId :String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        clientService.lookupClient(arn)(clientId).flatMap{
          case Some(client) =>
            clientService.getNewNameFromSession().map( newName =>
              Ok(update_client_reference_complete(
                client,
                clientRef = newName.get
              )))
        }

      }
    }
  }

}
