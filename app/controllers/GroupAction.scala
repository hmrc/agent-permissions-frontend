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
import play.api.mvc.Results.NotFound
import play.api.mvc.{AnyContent, MessagesRequest, Result}
import play.api.{Configuration, Environment, Logging}
import services.GroupService
import uk.gov.hmrc.agentmtdidentifiers.model.AccessGroup
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import views.html.groups.manage.group_not_found

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GroupAction @Inject()(val authConnector: AuthConnector,
                            val env: Environment,
                            val config: Configuration,
                            authAction: AuthAction,
                            val groupService: GroupService,
                            optInStatusAction: OptInStatusAction,
                            group_not_found: group_not_found
                           ) extends Logging  {

  import optInStatusAction._

  def withGroupForAuthorisedOptedAgent(groupId: String)
                                              (body: AccessGroup => Future[Result])
                                              (implicit ec: ExecutionContext,
                                               hc: HeaderCarrier,
                                               request: MessagesRequest[AnyContent],
                                               appConfig: AppConfig): Future[Result] = {
    authAction.isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        groupService.getGroup(groupId).flatMap(_.fold(groupNotFound)(body(_)))
      }
    }
  }

  def withGroupForAuthorisedOptedAssistant(groupId: String)
                                      (body: AccessGroup => Future[Result])
                                      (implicit ec: ExecutionContext,
                                       hc: HeaderCarrier,
                                       request: MessagesRequest[AnyContent],
                                       appConfig: AppConfig)
  : Future[Result] = {
    authAction.isAuthorisedAssistant { arn =>
      isOptedIn(arn) { _ =>
        groupService.getGroup(groupId).flatMap(
          _.fold(groupNotFound)(body(_)))
      }
    }
  }

  def groupNotFound(implicit request: MessagesRequest[AnyContent], appConfig: AppConfig): Future[Result] = {
    NotFound(group_not_found()).toFuture
  }
}
