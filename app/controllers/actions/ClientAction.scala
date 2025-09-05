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

package controllers.actions

import controllers._
import config.AppConfig
import models.DisplayClient
import play.api.mvc.Results.Ok
import play.api.mvc.{AnyContent, MessagesRequest, Result}
import play.api.{Configuration, Environment, Logging}
import services.ClientService
import models.Arn
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import views.html.group_member_details.add_groups_to_client.client_not_found

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientAction @Inject() (
  val authConnector: AuthConnector,
  val env: Environment,
  val config: Configuration,
  authAction: AuthAction,
  optInStatusAction: OptInStatusAction,
  val clientService: ClientService,
  client_not_found: client_not_found
) extends Logging {

  import optInStatusAction._

  def withClientForAuthorisedOptedAgent(clientId: String)(fn: (DisplayClient, Arn) => Future[Result])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier,
    request: MessagesRequest[AnyContent],
    appConfig: AppConfig
  ): Future[Result] =
    authAction.isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        clientService
          .lookupClient(arn)(clientId)
          .flatMap(
            _.fold(clientNotFound)(client => fn(client, arn))
          )
      }
    }

  def clientNotFound(implicit request: MessagesRequest[AnyContent], appConfig: AppConfig): Future[Result] =
    Ok(client_not_found()).toFuture

}
