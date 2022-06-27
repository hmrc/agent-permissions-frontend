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
import connectors.{AgentPermissionsConnector, GroupSummary}
import models.DisplayClient
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repository.SessionCacheRepository
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.manage.dashboard

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageGroupController @Inject()
(
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  dashboard: dashboard,
  val agentPermissionsConnector: AgentPermissionsConnector,
  val sessionCacheRepository: SessionCacheRepository,
)(
  implicit val appConfig: AppConfig, ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc) with I18nSupport with SessionBehaviour with Logging {

  import authAction._

  def showManageGroups: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      val eventuallySummaries = for {
        response <- agentPermissionsConnector.groupsSummaries(arn)
      } yield response

      eventuallySummaries.map { summaries: Option[(Seq[GroupSummary], Seq[DisplayClient])] =>
        Ok(dashboard(summaries.getOrElse((Seq.empty[GroupSummary], Seq.empty[DisplayClient]))))
      }
    }
  }

  def showManageGroupClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      Ok(s"showManageGroupClients not yet implemented ${groupId}").toFuture
    }
  }

  def showManageGroupTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      Ok(s"showManageGroupTeamMembers not yet implemented ${groupId}").toFuture
    }
  }

}
