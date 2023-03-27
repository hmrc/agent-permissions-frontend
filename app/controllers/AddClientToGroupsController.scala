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
import connectors.AddMembersToAccessGroupRequest
import controllers.actions.ClientAction
import forms.AddGroupsToClientForm
import models.{DisplayClient, GroupId}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agents.accessgroups.Client
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.group_member_details.add_groups_to_client.{confirm_added, select_groups}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AddClientToGroupsController @Inject()(
   clientAction: ClientAction,
   mcc: MessagesControllerComponents,
   val sessionCacheService: SessionCacheService,
   groupService: GroupService,
   select_groups: select_groups,
   confirm_added: confirm_added
)(implicit val appConfig: AppConfig,
  ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi)
  extends FrontendController(mcc)
    with I18nSupport
    with Logging {

  import clientAction._

  def showSelectGroupsForClient(clientId: String): Action[AnyContent] = Action.async { implicit request =>
    withClientForAuthorisedOptedAgent(clientId) { (displayClient: DisplayClient, arn: Arn) => {
      sessionCacheService.delete(GROUP_IDS_ADDED_TO)
      groupService.getGroupSummaries(arn).flatMap { allGroups =>
        groupService.groupSummariesForClient(arn, displayClient).map { clientGroups =>
          Ok(
            select_groups(
              clientGroups,
              allGroups.diff(clientGroups).filter(s => !s.isTaxGroup), // only custom groups they're not already in
              displayClient,
              AddGroupsToClientForm.form()
            )
          )
        }
      }
    }
    }
  }

  def submitSelectGroupsForClient(clientId: String): Action[AnyContent] = Action.async { implicit request =>
    withClientForAuthorisedOptedAgent(clientId) { (displayClient: DisplayClient, arn: Arn) => {
      AddGroupsToClientForm.form().bindFromRequest().fold(formErrors => {
        groupService.getGroupSummaries(arn).flatMap { allGroups =>
          groupService.groupSummariesForClient(arn, displayClient).map { clientGroups =>
            Ok(
              select_groups(
                clientGroups,
                allGroups.diff(clientGroups).filter(s => !s.isTaxGroup), // only custom groups they're not already in
                displayClient,
                formErrors
              )
            )
          }
        }
      }, { groupIdsStr =>
        val groupIds: Seq[GroupId] = groupIdsStr.map(GroupId.fromString)
        val client = Client(displayClient.enrolmentKey, displayClient.name)
        Future.sequence(groupIds.map { grp =>
          groupService.addMembersToGroup(
            grp, AddMembersToAccessGroupRequest(clients = Some(Set(client))
            ))
        }).map { _ =>
          sessionCacheService.put[Seq[GroupId]](GROUP_IDS_ADDED_TO, groupIds)
          Redirect(routes.AddClientToGroupsController.showConfirmClientAddedToGroups(clientId))
        }
      }
      )
    }
    }
  }

  def showConfirmClientAddedToGroups(clientId: String): Action[AnyContent] = Action.async { implicit request =>
    withClientForAuthorisedOptedAgent(clientId) { (displayClient: DisplayClient, arn: Arn) => {
      sessionCacheService.get[Seq[GroupId]](GROUP_IDS_ADDED_TO)
        .flatMap { maybeGroupIds =>
        groupService.getGroupSummaries(arn).map { groups =>
          val groupsAddedTo = groups.filter(grp => maybeGroupIds.getOrElse(Seq.empty).contains(grp.groupId))
          Ok(confirm_added(displayClient, groupsAddedTo))
        }
      }
    }
    }
  }

}
