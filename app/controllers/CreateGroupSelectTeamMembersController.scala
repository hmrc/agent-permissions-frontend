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
import controllers.actions.{GroupAction, SessionAction}
import forms.{AddTeamMembersToGroupForm, YesNoForm}
import models.{AddTeamMembersToGroup, TeamMember}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{GroupService, SessionCacheService, TeamMemberService}
import uk.gov.hmrc.agentmtdidentifiers.model.PaginationMetaData
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.create.members.{review_members_paginated, select_paginated_team_members}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class CreateGroupSelectTeamMembersController @Inject()
(
  groupAction: GroupAction,
  sessionAction: SessionAction,
  teamMemberService: TeamMemberService,
  mcc: MessagesControllerComponents,
  val sessionCacheService: SessionCacheService,
  val groupService: GroupService,
  select_paginated_team_members: select_paginated_team_members,
  review_members_paginated: review_members_paginated,
)(implicit val appConfig: AppConfig, ec: ExecutionContext, implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc) with I18nSupport with Logging {

  import groupAction._
  import sessionAction.withSessionItem

  private val controller: ReverseCreateGroupSelectTeamMembersController = routes.CreateGroupSelectTeamMembersController


  def showSelectTeamMembers(page: Option[Int] = None, pageSize: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[String](TEAM_MEMBER_SEARCH_INPUT) { teamMemberSearchTerm =>
        withSessionItem[String](RETURN_URL) { returnUrl =>
          teamMemberService
            .getPageOfTeamMembers(arn)(page.getOrElse(1), pageSize.getOrElse(10))
            .map { paginatedList =>
            Ok(
              select_paginated_team_members(
                paginatedList.pageContent,
                groupName,
                backUrl = Some(returnUrl.getOrElse(routes.CreateGroupSelectClientsController.showReviewSelectedClients(None, None).url)),
                form = AddTeamMembersToGroupForm.form().fill(
                  AddTeamMembersToGroup(
                    search = teamMemberSearchTerm
                  )
                ),
                paginationMetaData = Some(paginatedList.paginationMetaData)
              )
            )
          }
        }
      }
    }
  }

  def submitSelectedTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { maybeSelected =>
        val hasPreSelected = maybeSelected.getOrElse(Seq.empty).nonEmpty
        AddTeamMembersToGroupForm
          .form(hasPreSelected)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              teamMemberService
                .getPageOfTeamMembers(arn)(1, 10)
                .flatMap(paginatedList =>
                  sessionCacheService.get(RETURN_URL).map(returnUrl =>
                    Ok(
                      select_paginated_team_members(
                        paginatedList.pageContent,
                        groupName,
                        backUrl = Some(returnUrl.getOrElse(routes.CreateGroupSelectClientsController.showReviewSelectedClients(None, None).url)),
                        form = formWithErrors,
                        paginationMetaData = Some(paginatedList.paginationMetaData)
                      )
                    )
                  )
                )
            },
            formData => {
              teamMemberService
                .savePageOfTeamMembers(formData)
                .flatMap(nowSelectedMembers => {
                  if (formData.submit == CONTINUE_BUTTON) {
                    // check selected there are still selections after saving
                    if (nowSelectedMembers.nonEmpty) {
                      Redirect(controller.showReviewSelectedTeamMembers(None, None)).toFuture
                    } else {
                      // render page with empty selection error
                      for {
                        paginatedList <- teamMemberService.getPageOfTeamMembers(arn)()
                        returnUrl <- sessionCacheService.get(RETURN_URL)
                      } yield
                        Ok(
                          select_paginated_team_members(
                            paginatedList.pageContent,
                            groupName,
                            backUrl = Some(returnUrl.getOrElse(routes.CreateGroupSelectClientsController.showReviewSelectedClients(None, None).url)),
                            form = AddTeamMembersToGroupForm
                              .form()
                              .fill(AddTeamMembersToGroup(search = formData.search))
                              .withError("members", "error.select-members.empty"),
                            paginationMetaData = Some(paginatedList.paginationMetaData)
                          )
                        )
                    }
                  } else if (formData.submit.startsWith(PAGINATION_BUTTON)) {
                    val pageToShow = formData.submit.replace(s"${PAGINATION_BUTTON}_", "").toInt
                    Redirect(controller.showSelectTeamMembers(Some(pageToShow), Some(10))).toFuture
                  } else {
                    Redirect(controller.showSelectTeamMembers(None, None)).toFuture
                  }
                }
                )
            }
          )
      }
    }
  }

  def showReviewSelectedTeamMembers(maybePage: Option[Int], maybePageSize: Option[Int]): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { maybeTeamMembers =>
        maybeTeamMembers.fold(
          Redirect(controller.showSelectTeamMembers(None, None)).toFuture
        )(members => {
          val pageSize = maybePageSize.getOrElse(10)
          val page = maybePage.getOrElse(1)
          val (currentPageOfMembers: Seq[TeamMember], meta: PaginationMetaData) = paginationForSelectedMembers(members, pageSize, page)
          Ok(
            review_members_paginated(
              teamMembers = currentPageOfMembers,
              groupName = groupName,
              form = YesNoForm.form(),
              backUrl = Some(controller.showSelectTeamMembers(None, None).url),
              formAction = controller.submitReviewSelectedTeamMembers,
              paginationMetaData = Some(meta)
            )
          ).toFuture
        }
        )
      }
    }
  }

  def submitReviewSelectedTeamMembers(): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, _) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { maybeTeamMembers =>
        maybeTeamMembers
          .fold(
            Redirect(controller.showSelectTeamMembers(None, None)).toFuture
          ) { members =>
            YesNoForm
              .form("group.teamMembers.review.error")
              .bindFromRequest
              .fold(
                formWithErrors => {
                  val (currentPageOfMembers, meta) = paginationForSelectedMembers(members, 10, 1)
                  Ok(
                    review_members_paginated(
                      teamMembers = currentPageOfMembers,
                      groupName = groupName,
                      form = formWithErrors,
                      backUrl = Some(controller.showSelectTeamMembers(None, None).url),
                      formAction = controller.submitReviewSelectedTeamMembers,
                      paginationMetaData = Some(meta)
                    )
                  ).toFuture
                }, (yes: Boolean) => {
                  if (yes)
                    Redirect(controller.showSelectTeamMembers(None, None)).toFuture
                  else
                    Redirect(routes.CreateGroupController.showCheckYourAnswers).toFuture
                }
              )
          }
      }
    }
  }

  private def paginationForSelectedMembers(members: Seq[TeamMember], pageSize: Int, page: Int) = {
    val firstMemberInPage = (page - 1) * pageSize
    val lastMemberInPage = page * pageSize
    val currentPageOfMembers = members.slice(firstMemberInPage, lastMemberInPage)
    val numPages = Math.ceil(members.length.toDouble / pageSize.toDouble).toInt
    val meta = PaginationMetaData(
      lastPage = page == numPages,
      firstPage = page == 1,
      totalSize = members.length,
      totalPages = numPages,
      pageSize = pageSize,
      currentPageNumber = page,
      currentPageSize = currentPageOfMembers.length
    )
    (currentPageOfMembers, meta)
  }

}
