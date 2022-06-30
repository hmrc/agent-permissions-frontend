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
import connectors.{AgentPermissionsConnector, GroupSummary, UpdateAccessGroupRequest}
import forms.GroupNameForm
import models.DisplayClient
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.AccessGroup
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.manage._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageGroupController @Inject()
(
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  dashboard: dashboard,
  rename_group: rename_group,
  rename_group_complete: rename_group_complete,
  group_not_found: group_not_found,
  val agentPermissionsConnector: AgentPermissionsConnector,
  val sessionCacheRepository: SessionCacheRepository,
)(
  implicit val appConfig: AppConfig, ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi,
) extends FrontendController(mcc) with I18nSupport with SessionBehaviour with Logging {

  import authAction._

  def showManageGroups: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        val eventuallySummaries = for {
          response <- agentPermissionsConnector.groupsSummaries(arn)
        } yield response

        eventuallySummaries.map { summaries: Option[(Seq[GroupSummary], Seq[DisplayClient])] =>
          Ok(dashboard(summaries.getOrElse((Seq.empty[GroupSummary], Seq.empty[DisplayClient]))))
        }
      }
    }
  }

  def showManageGroupClients(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        Ok(s"showManageGroupClients not yet implemented ${groupId}").toFuture
      }
    }
  }

  def showManageGroupTeamMembers(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        Ok(s"showManageGroupTeamMembers not yet implemented ${groupId}").toFuture
      }
    }
  }

  def showRenameGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        agentPermissionsConnector.getGroup(groupId).map(_.fold (groupNotFound)
        ( group => Ok(rename_group(GroupNameForm.form.fill(group.groupName), group, groupId)) )
        )
      }
    }
  }

  def submitRenameGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        agentPermissionsConnector.getGroup(groupId).map(maybeGroup => {
          maybeGroup.fold (groupNotFound){ (group: AccessGroup) =>
            GroupNameForm.form()
              .bindFromRequest
              .fold(formWithErrors => {
                Ok(rename_group(formWithErrors, group, groupId))
              }, (newName: String) => {
                for {
                  _ <- sessionCacheRepository.putSession[String](GROUP_RENAMED_FROM, group.groupName)
                  patchRequestBody = UpdateAccessGroupRequest(groupName = Some(newName))
                  _ <- agentPermissionsConnector.updateGroup(groupId, patchRequestBody)
                } yield ()
                Redirect(routes.ManageGroupController.showGroupRenamed(groupId))
              }
              )
          }
        }
        )
      }
    }
  }

  def showGroupRenamed(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        val result = for {
          group <- agentPermissionsConnector.getGroup(groupId)
          oldName <- sessionCacheRepository.getFromSession(GROUP_RENAMED_FROM)
        } yield (group, oldName)
        result.map(tuple =>
          Ok(rename_group_complete(tuple._2.get, tuple._1.get.groupName))
        )
      }
    }
  }

  def showDeleteGroup(groupId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        agentPermissionsConnector.getGroup(groupId).map { maybeGroup =>
          Ok(s"delete group not yet implemented ${maybeGroup.get.groupName}")
        }
      }
    }
  }

  private def groupNotFound(implicit request: MessagesRequest[AnyContent]) : Result = {
    NotFound(group_not_found())
  }


}
