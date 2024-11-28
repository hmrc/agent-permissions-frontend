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
import connectors.AddOneTeamMemberToGroupRequest
import controllers.actions.TeamMemberAction
import forms.AddGroupsToClientForm
import models.{GroupId, TeamMember}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{GroupService, TaxGroupService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.group_member_details.add_groups_to_team_member.{confirm_added, select_groups}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AddTeamMemberToGroupsController @Inject() (
  teamMemberAction: TeamMemberAction,
  mcc: MessagesControllerComponents,
  groupService: GroupService,
  taxGroupService: TaxGroupService,
  select_groups: select_groups,
  confirm_added: confirm_added
)(implicit val appConfig: AppConfig, ec: ExecutionContext, implicit override val messagesApi: MessagesApi)
    extends FrontendController(mcc) with I18nSupport with Logging {

  import teamMemberAction._

  def showSelectGroupsForTeamMember(id: String): Action[AnyContent] = Action.async { implicit request =>
    withTeamMemberForAuthorisedOptedAgent(id) { (tm: TeamMember, arn: Arn) =>
      sessionCacheService.delete(GROUP_IDS_ADDED_TO)
      groupService.getGroupSummaries(arn).flatMap { allGroups =>
        groupService.groupSummariesForTeamMember(arn, tm).map { membersGroups =>
          Ok(
            select_groups(
              membersGroups,
              allGroups.filterNot(group => membersGroups.exists(_.groupId == group.groupId)),
              tm,
              AddGroupsToClientForm.form()
            )
          )
        }
      }
    }
  }

  def submitSelectGroupsForTeamMember(id: String): Action[AnyContent] = Action.async { implicit request =>
    withTeamMemberForAuthorisedOptedAgent(id) { (tm: TeamMember, arn: Arn) =>
      AddGroupsToClientForm
        .form()
        .bindFromRequest()
        .fold(
          formErrors =>
            groupService.getGroupSummaries(arn).flatMap { allGroups =>
              groupService.groupSummariesForTeamMember(arn, tm).map { membersGroups =>
                Ok(
                  select_groups(
                    membersGroups,
                    allGroups.filterNot(group => membersGroups.exists(_.groupId == group.groupId)),
                    tm,
                    formErrors
                  )
                )
              }
            },
          validForm =>
            if (validForm.contains(AddGroupsToClientForm.NoneValue)) {
              Redirect(appConfig.agentServicesAccountManageAccountUrl).toFuture
            } else {
              val agentUser = TeamMember.toAgentUser(tm)
              var groupsAddedTo: Seq[GroupId] = Seq[GroupId]()
              Future
                .sequence(validForm.map { encoded =>
                  val typeAndGroupId = encoded.split("_")
                  val groupType = typeAndGroupId(0)
                  val groupId: GroupId = GroupId.fromString(typeAndGroupId(1))
                  groupsAddedTo = groupsAddedTo :+ groupId
                  if (GroupType.CUSTOM == groupType) {
                    groupService.addOneMemberToGroup(groupId, AddOneTeamMemberToGroupRequest(agentUser))
                  } else {
                    taxGroupService.addOneMemberToGroup(groupId, AddOneTeamMemberToGroupRequest(agentUser))
                  }
                })
                .map { _ =>
                  sessionCacheService.put[Seq[GroupId]](GROUP_IDS_ADDED_TO, groupsAddedTo)
                  Redirect(routes.AddTeamMemberToGroupsController.showConfirmTeamMemberAddedToGroups(id))
                }
            }
        )
    }
  }

  def showConfirmTeamMemberAddedToGroups(id: String): Action[AnyContent] = Action.async { implicit request =>
    withTeamMemberForAuthorisedOptedAgent(id) { (tm: TeamMember, arn: Arn) =>
      sessionCacheService.get[Seq[GroupId]](GROUP_IDS_ADDED_TO).flatMap { maybeGroupIds =>
        groupService.getGroupSummaries(arn).map { groups =>
          val groupsAddedTo = groups
            .filter(grp => maybeGroupIds.getOrElse(Seq.empty).contains(grp.groupId))
          Ok(confirm_added(tm, groupsAddedTo))
        }
      }
    }
  }

}
