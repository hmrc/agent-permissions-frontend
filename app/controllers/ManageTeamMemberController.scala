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
import controllers.actions.{AuthAction, OptInStatusAction}
import forms.SearchAndFilterForm
import models.SearchFilter
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{GroupService, TeamMemberService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.group_member_details._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ManageTeamMemberController @Inject() (
  authAction: AuthAction,
  mcc: MessagesControllerComponents,
  groupService: GroupService,
  teamMemberService: TeamMemberService,
  optInStatusAction: OptInStatusAction,
  manage_team_members: manage_team_members,
  team_member_details: team_member_details
)(
  implicit val appConfig: AppConfig,
  ec: ExecutionContext,
  implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc) with I18nSupport with Logging {

  import authAction._
  import optInStatusAction._

  val PAGINATION_REGEX = "(pagination_)(\\d+)".r

  private val controller: ReverseManageTeamMemberController = routes.ManageTeamMemberController

  def showPageOfTeamMembers(page: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        val eventualTuple = for {
          search        <- sessionCacheService.get(TEAM_MEMBER_SEARCH_INPUT)
          pageOfMembers <- teamMemberService.getPageOfTeamMembers(arn)(page.getOrElse(1), 5)
        } yield (search, pageOfMembers)
        eventualTuple.map { tuple =>
          val (search, paginatedMembers) = (tuple._1, tuple._2)
          println("AAAAAAAAAA")
          println(paginatedMembers.paginationMetaData)
          Ok(
            manage_team_members(
              teamMembers = paginatedMembers.pageContent,
              form = SearchAndFilterForm.form().fill(SearchFilter(search, None, None)),
              paginationMetaData = Some(paginatedMembers.paginationMetaData)
            )
          )
        }
      }
    }
  }

  def submitPageOfTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
        searchFilter.submit.fold(
          Redirect(routes.ManageTeamMemberController.showPageOfTeamMembers(Some(1))).toFuture
        ) {
          case CLEAR_BUTTON =>
            sessionCacheService
              .delete(TEAM_MEMBER_SEARCH_INPUT)
              .map(_ => Redirect(controller.showPageOfTeamMembers(None)))
          case FILTER_BUTTON =>
            sessionCacheService
              .put(TEAM_MEMBER_SEARCH_INPUT, searchFilter.search.getOrElse(""))
              .map(_ => Redirect(controller.showPageOfTeamMembers(None)))
        }
      }
    }
  }

  def showTeamMemberDetails(memberId: String): Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        teamMemberService.lookupTeamMember(arn)(memberId).flatMap {
          case Some(teamMember) =>
            groupService
              .groupSummariesForTeamMember(arn, teamMember)
              .map(gs =>
                Ok(
                  team_member_details(
                    teamMember = teamMember,
                    teamMemberGroups = gs
                  )
                )
              )
          case _ => Redirect(routes.ManageTeamMemberController.showPageOfTeamMembers(None)).toFuture
        }
      }
    }
  }

}
