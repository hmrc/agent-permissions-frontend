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
import connectors.AgentPermissionsConnector
import forms.AddClientsToGroupForm
import models.{ButtonSelect, DisplayClient}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import repository.SessionCacheRepository
import services.{GroupService, SessionCacheService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.group_member_details._

import java.util.Base64.{getDecoder, getEncoder}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageClientController @Inject()(
     authAction: AuthAction,
     mcc: MessagesControllerComponents,
     groupService: GroupService,
     manage_clients_list: manage_clients_list,
     client_details: client_details,
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
        withSessionItem[Seq[DisplayClient]](FILTERED_CLIENTS) { maybeFilteredResult =>
          if (maybeFilteredResult.isDefined)
            Ok(
              manage_clients_list(
                maybeFilteredResult,
                AddClientsToGroupForm.form()
              )
            ).toFuture
          else
            groupService.getClients(arn).flatMap { maybeClients =>
              Ok(
                manage_clients_list(
                  maybeClients,
                  AddClientsToGroupForm.form()
                )
              ).toFuture
            }
        }
      }
    }
  }

  // button is never continue
  def submitFilterAllClients: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        withSessionItem[Seq[DisplayClient]](FILTERED_CLIENTS) { maybeFilteredResult =>
          val buttonSelection: ButtonSelect = buttonClickedByUserOnFilterFormPage(request.body.asFormUrlEncoded)

          AddClientsToGroupForm
            .form(buttonSelection)
            .bindFromRequest()
            .fold(
              formWithErrors => {
                for {
                  result <- if (maybeFilteredResult.isDefined)
                    Ok(
                      manage_clients_list(maybeFilteredResult,
                        formWithErrors)).toFuture
                  else
                    groupService.getClients(arn).flatMap { maybeClients =>
                      Ok(
                        manage_clients_list(
                          maybeClients,
                          formWithErrors)).toFuture
                    }
                } yield result
              },
              formData => {
                groupService.saveSelectedOrFilteredClients(buttonSelection)(arn)(formData)
                  .map(_ => Redirect(routes.ManageClientController.showAllClients))
              }
            )
        }
      }
    }
  }

  def showClientDetails(clientId :String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        val clientJson = Json.parse(new String(getDecoder.decode(clientId.replaceAll("'", "")))).asOpt[DisplayClient]

          groupService.groupSummariesForClient(arn, clientJson.get).flatMap { maybeGroups =>
            Ok(client_details(
              client = clientJson.get,
              clientGroups = maybeGroups
            )).toFuture
        }
      }
    }
  }

}
