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
import connectors.CreateTaxServiceGroupRequest
import controllers.actions.{AuthAction, GroupAction, OptInStatusAction, SessionAction}
import forms.{AddTeamMembersToGroupForm, YesNoForm}
import models.TeamMember.toAgentUser
import models.{AddTeamMembersToGroup, TeamMember}
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.{SessionCacheService, TaxGroupService, TeamMemberService}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, PaginationMetaData}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.create.group_created
import views.html.groups.create.members.{confirm_remove_member, review_members_paginated, select_paginated_team_members}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CreateGroupSelectTeamMembersController @Inject()
(
  groupAction: GroupAction,
  authAction: AuthAction,
  optInStatusAction: OptInStatusAction,
  sessionAction: SessionAction,
  teamMemberService: TeamMemberService,
  taxGroupService: TaxGroupService,
  confirm_remove_member: confirm_remove_member,
  mcc: MessagesControllerComponents,
  val sessionCacheService: SessionCacheService,
  group_created: group_created,
  select_paginated_team_members: select_paginated_team_members,
  review_members_paginated: review_members_paginated,
)(implicit val appConfig: AppConfig, ec: ExecutionContext, implicit override val messagesApi: MessagesApi
) extends FrontendController(mcc) with I18nSupport with Logging {

  import authAction.isAuthorisedAgent
  import groupAction._
  import optInStatusAction.isOptedInWithSessionItem
  import sessionAction.withSessionItem

  private val controller: ReverseCreateGroupSelectTeamMembersController = routes.CreateGroupSelectTeamMembersController
  private val selectClientsController: ReverseCreateGroupSelectClientsController = routes.CreateGroupSelectClientsController
  private val selectNameController: ReverseCreateGroupSelectNameController = routes.CreateGroupSelectNameController

  def showSelectTeamMembers(page: Option[Int] = None, pageSize: Option[Int] = None): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, groupType, arn) =>
      withSessionItem[String](TEAM_MEMBER_SEARCH_INPUT) { teamMemberSearchTerm =>
        val backUrl = if (groupType == CUSTOM_GROUP) {
          Some(selectClientsController.showReviewSelectedClients(None, None).url)
        } else Some(selectNameController.showConfirmGroupName.url)
        teamMemberService
          .getPageOfTeamMembers(arn)(page.getOrElse(1), pageSize.getOrElse(10))
          .map { paginatedList =>
            Ok(
              select_paginated_team_members(
                paginatedList.pageContent,
                groupName,
                backUrl = backUrl,
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

  def submitSelectedTeamMembers: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, _, arn) =>
      withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { maybeSelected =>
        val hasPreSelected = maybeSelected.getOrElse(Seq.empty).nonEmpty
        AddTeamMembersToGroupForm
          .form(hasPreSelected)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              teamMemberService
                .getPageOfTeamMembers(arn)(1, 10)
                .map(paginatedList =>
                  Ok(
                    select_paginated_team_members(
                      paginatedList.pageContent,
                      groupName,
                      backUrl = Some(selectClientsController.showReviewSelectedClients(None, None).url),
                      form = formWithErrors,
                      paginationMetaData = Some(paginatedList.paginationMetaData)
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
                      teamMemberService
                        .getPageOfTeamMembers(arn)()
                        .map(paginatedList =>
                          Ok(
                            select_paginated_team_members(
                              paginatedList.pageContent,
                              groupName,
                              backUrl = Some(selectClientsController.showReviewSelectedClients(None, None).url),
                              form = AddTeamMembersToGroupForm
                                .form()
                                .fill(AddTeamMembersToGroup(search = formData.search))
                                .withError("members", "error.select-members.empty"),
                              paginationMetaData = Some(paginatedList.paginationMetaData)
                            )
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
    withGroupNameAndAuthorised { (groupName, _, _) =>
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
    withGroupNameAndAuthorised { (groupName, groupType, arn) =>
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
                  else {
                    groupType match {
                      case TAX_SERVICE_GROUP =>
                        submitTaxServiceGroup(arn, members, groupName)
                      case CUSTOM_GROUP =>
                        groupService
                          .createGroup(arn, groupName)
                          .map(_ => Redirect(controller.showGroupCreated))
                    }
                  }
                }
              )
          }
      }
    }
  }

  def showConfirmRemoveTeamMember(memberId: String): Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, _, arn) =>
      withSessionItem(SELECTED_TEAM_MEMBERS) { selectedMembers =>
        selectedMembers.getOrElse(Seq.empty).find(_.id == memberId)
          .fold {
            Redirect(controller.showSelectTeamMembers(None, None)).toFuture
          } { teamMember =>
            sessionCacheService
              .put(MEMBER_TO_REMOVE, teamMember)
              .flatMap(_ =>
                Ok(confirm_remove_member(YesNoForm.form(), groupName, teamMember)).toFuture
              )
          }
      }
    }
  }

  def submitConfirmRemoveTeamMember: Action[AnyContent] = Action.async { implicit request =>
    withGroupNameAndAuthorised { (groupName, _, _) =>
      withSessionItem[TeamMember](MEMBER_TO_REMOVE) { maybeTeamMember =>
        withSessionItem[Seq[TeamMember]](SELECTED_TEAM_MEMBERS) { maybeSelectedTeamMembers =>
          if (maybeTeamMember.isEmpty || maybeSelectedTeamMembers.isEmpty) {
            Redirect(controller.showSelectTeamMembers(None, None)).toFuture
          }
          else {
            YesNoForm
              .form("group.member.remove.error")
              .bindFromRequest
              .fold(
                formWithErrors => {
                  Ok(confirm_remove_member(formWithErrors, groupName, maybeTeamMember.get)).toFuture
                }, (yes: Boolean) => {
                  if (yes) {
                    val clientsMinusRemoved = maybeSelectedTeamMembers.get.filterNot(_ == maybeTeamMember.get)
                    sessionCacheService
                      .put(SELECTED_TEAM_MEMBERS, clientsMinusRemoved)
                      .map(_ =>
                        if (clientsMinusRemoved.isEmpty) {
                          Redirect(controller.showSelectTeamMembers(None, None))
                        } else {
                          Redirect(controller.showReviewSelectedTeamMembers(None, None))
                        }
                      )
                  }
                  else Redirect(controller.showReviewSelectedTeamMembers(None, None)).toFuture
                }
              )
          }
        }
      }
    }
  }

  private def submitTaxServiceGroup(arn: Arn, members: Seq[TeamMember], groupName: String)
                                   (implicit request: MessagesRequest[AnyContent]): Future[Result] = {
    sessionCacheService
      .get[String](GROUP_SERVICE_TYPE)
      .flatMap(maybeService => {
        val startAgainRoute = controllers.routes.CreateGroupSelectGroupTypeController.showSelectGroupType
        maybeService.fold(
          Redirect(startAgainRoute).toFuture
        )(service => {
          val req = CreateTaxServiceGroupRequest(groupName, Some(members.map(toAgentUser).toSet), service, autoUpdate = true, None)
          taxGroupService
            .createGroup(arn, req)
            .flatMap(_ =>
              for {
                _ <- sessionCacheService.put(NAME_OF_GROUP_CREATED, groupName)
                _ <- sessionCacheService.deleteAll(creatingGroupKeys)
              } yield Redirect(controller.showGroupCreated)
            )
        }
        )
      }
      )
  }

  def showGroupCreated: Action[AnyContent] = Action.async { implicit request =>
    isAuthorisedAgent { arn =>
      isOptedInWithSessionItem[String](NAME_OF_GROUP_CREATED)(arn) { maybeGroupName =>
        Ok(group_created(maybeGroupName.getOrElse(""))).toFuture
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
