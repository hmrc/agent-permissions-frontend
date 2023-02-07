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

import config.AppConfig
import controllers._
import play.api.mvc.Results.{NotFound, Redirect}
import play.api.mvc.{AnyContent, MessagesRequest, Result}
import play.api.{Configuration, Environment, Logging}
import services.{GroupService, TaxGroupService}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import views.html.groups.manage.group_not_found

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GroupAction @Inject()
(
  val authConnector: AuthConnector,
  val env: Environment,
  val config: Configuration,
  authAction: AuthAction,
  val groupService: GroupService,
  val taxGroupService: TaxGroupService,
  optInStatusAction: OptInStatusAction,
  sessionAction: SessionAction,
  group_not_found: group_not_found
) extends Logging {

  import optInStatusAction._

  @deprecated("use withGroupSummaryForAuthorisedOptedAgent")
  def withGroupForAuthorisedOptedAgent(groupId: String)
                                      (body: CustomGroup => Future[Result])
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

  def withAccessGroupForAuthorisedOptedAgent(groupId: String, isCustom: Boolean = true)
                                            (callback: (AccessGroup, Arn) => Future[Result])
                                            (implicit ec: ExecutionContext, hc: HeaderCarrier,
                                             request: MessagesRequest[AnyContent], appConfig: AppConfig): Future[Result] = {
    authAction.isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        if (isCustom) {
          groupService
            .getGroup(groupId)
            .flatMap(_.fold(groupNotFound)(customGroup => callback(customGroup, arn)))
        } else {
          taxGroupService
            .getGroup(groupId)
            .flatMap(_.fold(groupNotFound)(taxGroup => callback(taxGroup, arn)))
        }
      }
    }
  }

  def withGroupSummaryForAuthorisedOptedAgent(groupId: String, isCustom: Boolean = true)
                                             (callback: (GroupSummary, Arn) => Future[Result])
                                             (implicit ec: ExecutionContext,
                                              hc: HeaderCarrier,
                                              request: MessagesRequest[AnyContent],
                                              appConfig: AppConfig): Future[Result] = {
    authAction.isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        if (isCustom) {
          groupService
            .getCustomSummary(groupId)
            .flatMap(_.fold(groupNotFound)(callback(_, arn)))
        } else {
          taxGroupService
            .getGroup(groupId)
            .flatMap(_.fold(groupNotFound)(taxGroup => callback(GroupSummary.fromAccessGroup(taxGroup), arn)))
        }
      }
    }
  }

  // TODO use withGroupSummaryForAuthorisedOptedAgent or withAccessGroupForAuthorisedOptedAgent
  def withTaxGroupForAuthorisedOptedAgent(groupId: String, isCustom: Boolean = true)
                                         (body: TaxGroup => Future[Result])
                                         (implicit ec: ExecutionContext,
                                          hc: HeaderCarrier,
                                          request: MessagesRequest[AnyContent],
                                          appConfig: AppConfig): Future[Result] = {
    authAction.isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        taxGroupService.getGroup(groupId).flatMap(_.fold(groupNotFound)(body(_)))
      }
    }
  }

  @Deprecated // use withGroupTypeAndAuthorised for the new flow
  def withGroupNameForAuthorisedOptedAgent(body: (String, Arn) => Future[Result])
                                          (implicit ec: ExecutionContext, hc: HeaderCarrier,
                                           request: MessagesRequest[AnyContent], appConfig: AppConfig): Future[Result] = {
    authAction.isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        maybeGroupName.fold(Redirect(controllers.routes.CreateGroupController.showGroupName).toFuture) {
          groupName => body(groupName, arn)
        }
      }
    }
  }

  def withGroupTypeAndAuthorised(body: (String, Arn) => Future[Result])
                                (implicit ec: ExecutionContext, hc: HeaderCarrier,
                                 request: MessagesRequest[AnyContent], appConfig: AppConfig): Future[Result] = {
    authAction.isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_TYPE)(arn) { maybeGroupType =>
        maybeGroupType.fold(Redirect(controllers.routes.CreateGroupSelectGroupTypeController.showSelectGroupType).toFuture)(
          groupType => body(groupType, arn)
        )
      }
    }
  }

  def withGroupNameAndAuthorised(body: (String, String, Arn) => Future[Result])
                                (implicit ec: ExecutionContext, hc: HeaderCarrier,
                                 request: MessagesRequest[AnyContent], appConfig: AppConfig): Future[Result] = {
    authAction.isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_TYPE)(arn) { maybeGroupType =>
        maybeGroupType.fold(Redirect(controllers.routes.CreateGroupSelectGroupTypeController.showSelectGroupType).toFuture)(
          groupType =>
            sessionAction.withSessionItem[String](GROUP_NAME) { maybeGroupName =>
              maybeGroupName.fold(Redirect(controllers.routes.CreateGroupSelectNameController.showGroupName).toFuture) {
                groupName => body(groupName, groupType, arn)
              }
            })
      }
    }
  }

  def withGroupForAuthorisedOptedAssistant(groupId: String)
                                          (body: CustomGroup => Future[Result])
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
