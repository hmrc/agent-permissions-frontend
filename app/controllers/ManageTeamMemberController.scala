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
import forms.SearchAndFilterForm
import models.SearchFilter
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
import services.{GroupService, SessionCacheService, TeamMemberService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.group_member_details._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageTeamMemberController @Inject()(
                                            authAction: AuthAction,
                                            mcc: MessagesControllerComponents,
                                            groupService: GroupService,
                                            teamMemberService: TeamMemberService,
                                            optInStatusAction: OptInStatusAction,
                                            manage_team_members_list: manage_team_members_list,
                                            team_member_details: team_member_details,
                                            val agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
                                            val agentPermissionsConnector: AgentPermissionsConnector,
                                            val sessionCacheRepository: SessionCacheRepository,
                                            val sessionCacheService: SessionCacheService)
                                          (implicit val appConfig: AppConfig, ec: ExecutionContext,
    implicit override val messagesApi: MessagesApi) extends FrontendController(mcc)

    with I18nSupport
    with Logging {

  import authAction._
  import optInStatusAction._

  def showAllTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        teamMemberService.getFilteredTeamMembersElseAll(arn).flatMap { teamMembers =>
          val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
          searchFilter.submit.fold(
            //no filter/clear was applied
            Ok(manage_team_members_list(teamMembers = teamMembers, form = SearchAndFilterForm.form()))
          )({
            //clear/filter buttons pressed
            case CLEAR_BUTTON =>
              Redirect(routes.ManageTeamMemberController.showAllTeamMembers)
            case FILTER_BUTTON =>
              val filterByName = teamMembers.filter(_.name.toLowerCase.contains(searchFilter.search.getOrElse("").toLowerCase))
              val filterByEmail = teamMembers.filter(_.email.toLowerCase.contains(searchFilter.search.getOrElse("").toLowerCase))
              val filtered = (filterByName ++ filterByEmail).distinct
              Ok(manage_team_members_list(filtered, SearchAndFilterForm.form().fill(searchFilter)))
          }).toFuture
        }
      }
    }
  }

  def showTeamMemberDetails(memberId :String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        teamMemberService.lookupTeamMember(arn)(memberId).flatMap {
          case Some(teamMember) =>
            groupService.groupSummariesForTeamMember(arn, teamMember)
              .map(gs =>
                Ok(team_member_details(
                  teamMember = teamMember,
                  teamMemberGroups = gs
                ))
          )
          case _ => Redirect(routes.ManageTeamMemberController.showAllTeamMembers).toFuture
        }
        }
      }
  }

}
