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
import connectors.{AddMembersToAccessGroupRequest, AgentPermissionsConnector}
import forms.AddGroupsToClientForm
import models.TeamMember
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
import services.{GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.add_groups_to_team_member.{confirm_added, select_groups}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AddTeamMemberToGroupsController @Inject()(
                                                 teamMemberAction: TeamMemberAction,
                                                 mcc: MessagesControllerComponents,
                                                 val agentPermissionsConnector: AgentPermissionsConnector,
                                                 val sessionCacheRepository: SessionCacheRepository,
                                                 val sessionCacheService: SessionCacheService,
                                                 groupService: GroupService,
                                                 select_groups: select_groups,
                                                 confirm_added: confirm_added
                                               )(implicit val appConfig: AppConfig,
                                                 ec: ExecutionContext,
                                                 implicit override val messagesApi: MessagesApi
                                               ) extends FrontendController(mcc)

    with I18nSupport
  with SessionBehaviour
  with Logging {

  import teamMemberAction._

  def showSelectGroupsForTeamMember(id: String): Action[AnyContent] = Action.async { implicit request =>
    withTeamMemberForAuthorisedOptedAgent(id) { (tm: TeamMember, arn: Arn) => {
      sessionCacheRepository.deleteFromSession(GROUP_IDS_ADDED_TO)
      groupService.groups(arn).flatMap { allGroups =>
        groupService.groupSummariesForTeamMember(arn, tm).map { membersGroups =>
          Ok(
            select_groups(
              membersGroups,
              allGroups.diff(membersGroups),
              tm,
              AddGroupsToClientForm.form()
            )
          )
        }
      }
    }
    }
  }

  def submitSelectGroupsForTeamMember(id: String): Action[AnyContent] = Action.async { implicit request =>
    withTeamMemberForAuthorisedOptedAgent(id) { (tm: TeamMember, arn: Arn) => {
      AddGroupsToClientForm.form().bindFromRequest().fold(formErrors => {
        groupService.groups(arn).flatMap { allGroups =>
          groupService.groupSummariesForTeamMember(arn, tm).map { membersGroups =>
            Ok(
              select_groups(
                membersGroups,
                allGroups.diff(membersGroups),
                tm,
                formErrors
              )
            )
          }
        }
      }, { groupIds =>
        val agentUser = TeamMember.toAgentUser(tm)
        Future.sequence(groupIds.map { grp =>
          agentPermissionsConnector.addMembersToGroup(
            grp, AddMembersToAccessGroupRequest(teamMembers = Some(Set(agentUser))
            ))
        }).map { _ =>
          sessionCacheRepository.putSession[Seq[String]](GROUP_IDS_ADDED_TO, groupIds)
          Redirect(routes.AddTeamMemberToGroupsController.showConfirmTeamMemberAddedToGroups(id))
        }
      }
      )
    }
    }
  }

  def showConfirmTeamMemberAddedToGroups(id: String): Action[AnyContent] = Action.async { implicit request =>
    withTeamMemberForAuthorisedOptedAgent(id) { (tm: TeamMember, arn: Arn) => {
      sessionCacheRepository.getFromSession[Seq[String]](GROUP_IDS_ADDED_TO).flatMap { maybeGroupIds =>
        groupService.groups(arn).map { groups =>
          val groupsAddedTo = groups
            .filter(grp => maybeGroupIds.getOrElse(Seq.empty).contains(grp.groupId))
          Ok(confirm_added(tm, groupsAddedTo))
        }
      }
    }
    }
  }

}
