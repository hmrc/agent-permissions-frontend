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
import controllers.actions.{AuthAction, OptInStatusAction, SessionAction}
import forms.{AddTeamMembersToGroupForm, YesNoForm}
import models.{AddTeamMembersToGroup, TeamMember}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{GroupService, SessionCacheService, TeamMemberService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.create.select_paginated_team_members
import views.html.groups.review_team_members_to_add

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateGroupSelectTeamMembersController @Inject()
(
  authAction: AuthAction,
  sessionAction: SessionAction,
  teamMemberService: TeamMemberService,
  mcc: MessagesControllerComponents,
  val sessionCacheService: SessionCacheService,
  val groupService: GroupService,
  optInStatusAction: OptInStatusAction,
  select_paginated_team_members: select_paginated_team_members,
  review_team_members_to_add: review_team_members_to_add,
)(implicit val appConfig: AppConfig, ec: ExecutionContext, implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc) with I18nSupport with Logging {

  import authAction._
  import optInStatusAction._
  import sessionAction.withSessionItem

  private val controller: ReverseCreateGroupSelectTeamMembersController = routes.CreateGroupSelectTeamMembersController


  def showSelectTeamMembers(page: Option[Int] = None, pageSize: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[String](TEAM_MEMBER_SEARCH_INPUT) { teamMemberSearchTerm =>
        withSessionItem[String](RETURN_URL) { returnUrl =>
          teamMemberService.getPageOfTeamMembers(arn)(page.getOrElse(1), pageSize.getOrElse(10)).map { paginatedList =>
            Ok(
              select_paginated_team_members(
                paginatedList.pageContent,
                groupName,
                backUrl = Some(returnUrl.getOrElse(routes.CreateGroupSelectClientsController.showReviewSelectedClients.url)),
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
              teamMemberService.getPageOfTeamMembers(arn)(1, 10).flatMap(paginatedList =>
                sessionCacheService.get(RETURN_URL).map(returnUrl =>
                  Ok(
                    select_paginated_team_members(
                      paginatedList.pageContent,
                      groupName,
                      backUrl = Some(returnUrl.getOrElse(routes.CreateGroupSelectClientsController.showReviewSelectedClients.url)),
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
                    // check selected clients from session cache AFTER saving (removed de-selections)
                    if (nowSelectedMembers.nonEmpty) {
                      Redirect(controller.showReviewSelectedTeamMembers).toFuture
                    } else { // render page with empty selection error
                      for {
                        paginatedList <- teamMemberService.getPageOfTeamMembers(arn)()
                        returnUrl <- sessionCacheService.get(RETURN_URL)
                      } yield
                        Ok(
                          select_paginated_team_members(
                            paginatedList.pageContent,
                            groupName,
                            backUrl = Some(returnUrl.getOrElse(routes.CreateGroupSelectClientsController.showReviewSelectedClients.url)),
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

  def showReviewSelectedTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { maybeTeamMembers =>
        maybeTeamMembers.fold(
          Redirect(controller.showSelectTeamMembers(None, None)).toFuture
        )(members =>
          Ok(
            review_team_members_to_add(
              teamMembers = members,
              groupName = groupName,
              form = YesNoForm.form(),
              backUrl = Some(controller.showSelectTeamMembers(None, None).url),
              formAction = controller.submitReviewSelectedTeamMembers
            )
          ).toFuture
        )
      }
    }
  }

  def submitReviewSelectedTeamMembers(): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameForAuthorisedOptedAgent { (groupName, arn) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { selectedMembers =>
        selectedMembers
          .fold(
            Redirect(controller.showSelectTeamMembers(None, None)).toFuture
          ) { members =>
            YesNoForm
              .form("group.teamMembers.review.error")
              .bindFromRequest
              .fold(
                formWithErrors => {
                  Ok(
                    review_team_members_to_add(
                      teamMembers = members,
                      groupName = groupName,
                      form = formWithErrors,
                      backUrl = Some(controller.showSelectTeamMembers(None, None).url),
                      formAction = controller.submitReviewSelectedTeamMembers
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

  private def withGroupNameForAuthorisedOptedAgent(body: (String, Arn) => Future[Result])
                                                  (implicit ec: ExecutionContext, request: MessagesRequest[AnyContent], appConfig: AppConfig): Future[Result] = {
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](GROUP_NAME)(arn) { maybeGroupName =>
        maybeGroupName.fold(Redirect(routes.CreateGroupController.showGroupName).toFuture) {
          groupName => body(groupName, arn)
        }
      }
    }
  }

}
