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
import forms.{AddClientsToGroupForm, ClientReferenceForm}
import models.{ButtonSelect, DisplayClient}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import repository.SessionCacheRepository
import services.{GroupService, ManageClientService, SessionCacheService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.group_member_details._

import java.util.Base64.getDecoder
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageTeamMemberController @Inject()(
     authAction: AuthAction,
     mcc: MessagesControllerComponents,
     groupService: GroupService,
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

  def showAllTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        Ok(s"showAllTeamMembers not yet implemented $arn").toFuture
      }
    }
  }

  def showTeamMemberDetails(memberId :String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        Ok(s"showTeamMemberDetails for $memberId not yet implemented $arn").toFuture
      }
    }
  }

}
