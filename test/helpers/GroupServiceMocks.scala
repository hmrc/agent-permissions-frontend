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

package helpers

import akka.Done
import connectors.{AddMembersToAccessGroupRequest, UpdateAccessGroupRequest}
import models.{DisplayClient, TeamMember}
import org.scalamock.handlers.{CallHandler4, CallHandler6}
import org.scalamock.scalatest.MockFactory
import play.api.mvc.Request
import services.GroupService
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, CustomGroup, GroupSummary, PaginationMetaData}
import uk.gov.hmrc.agentmtdidentifiers.utils.PaginatedListBuilder
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait GroupServiceMocks extends MockFactory {

  def expectGetTeamMembersFromGroup(arn: Arn)(teamMembers: Seq[TeamMember])
                                   (implicit groupService: GroupService): Unit =
    (groupService
      .getTeamMembersFromGroup(_: Arn)(_: Seq[TeamMember])
      (_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .returning(Future successful teamMembers).once()

  def expectGetGroupById(id: String, maybeGroup: Option[CustomGroup])(
    implicit groupService: GroupService): Unit =
    (groupService
      .getGroup(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(id, *, *)
      .returning(Future successful maybeGroup)

  def expectGetCustomSummaryById(id: String, maybeSummary: Option[GroupSummary])(
    implicit groupService: GroupService): Unit =
    (groupService
      .getCustomSummary(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(id, *, *)
      .returning(Future successful maybeSummary)
      .once()

  def expectGetGroupsForArn(arn: Arn)(groups: Seq[GroupSummary])(implicit groupService: GroupService): Unit =
    (groupService
      .getGroupSummaries(_: Arn)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .returning(Future.successful(groups)).once()

  def expectGetPaginatedGroupSummaries(arn: Arn, filterTerm: String = "")(page:Int, pageSize:Int)(groups: Seq[GroupSummary])(implicit groupService: GroupService): Unit =
    (groupService
      .getPaginatedGroupSummaries(_: Arn, _: String)(_:Int,_:Int)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
      .expects(arn, filterTerm, page, pageSize, *, *, *)
      .returning(Future.successful(PaginatedListBuilder.build[GroupSummary](page, pageSize, groups))).once()

  def expectGetPaginatedClientsForCustomGroup(groupId: String)
                                             (page:Int, pageSize:Int)
                                             (pageData: (Seq[DisplayClient], PaginationMetaData))(implicit groupService: GroupService): CallHandler6[String, Int, Int, Request[_], HeaderCarrier, ExecutionContext, Future[(Seq[DisplayClient], PaginationMetaData)]] =
    (groupService
      .getPaginatedClientsForCustomGroup(_: String)(_:Int,_:Int)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
      .expects(groupId, page, pageSize, *, *, *)
      .returning(Future.successful(pageData)).once()

  def expectGetGroupSummariesForTeamMember(arn: Arn)(teamMember: TeamMember)
                                          (groupsAlreadyAssociatedToMember: Seq[GroupSummary])
                                          (implicit groupService: GroupService): Unit =
    (groupService
      .groupSummariesForTeamMember(_: Arn, _: TeamMember)
      (_: Request[_], _: ExecutionContext, _: HeaderCarrier)
      ).expects(arn, teamMember, *, *, *)
      .returning(Future.successful(groupsAlreadyAssociatedToMember)).once()

  def expectGetGroupSummariesForClient(arn: Arn)(client: DisplayClient)
                                      (groupsAlreadyAssociatedToClient: Seq[GroupSummary])
                                      (implicit groupService: GroupService): Unit =
    (groupService
      .groupSummariesForClient(_: Arn, _: DisplayClient)(_: Request[_], _: ExecutionContext, _: HeaderCarrier))
      .expects(arn, client, *, *, *)
      .returning(Future.successful(groupsAlreadyAssociatedToClient)).once()

  def expectCreateGroup(arn: Arn)(groupName: String)
                       (implicit groupService: GroupService): Unit =
    (groupService
      .createGroup(_: Arn, _: String)(_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
      .expects(arn, groupName, *, *, *)
      .returning(Future.successful(Done)).once()

  def expectUpdateGroup(id: String, payload: UpdateAccessGroupRequest)
                       (implicit groupService: GroupService): Unit =
    (groupService
      .updateGroup(_: String, _: UpdateAccessGroupRequest)(_: HeaderCarrier, _: ExecutionContext))
      .expects(id, payload, *, *)
      .returning(Future.successful(Done)).once()

  def expectDeleteGroup(id: String)
                       (implicit groupService: GroupService): Unit =
    (groupService
      .deleteGroup(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(id, *, *)
      .returning(Future.successful(Done)).once()

  def expectAddMembersToGroup(id: String, payload: AddMembersToAccessGroupRequest)
                       (implicit groupService: GroupService): Unit =
    (groupService
      .addMembersToGroup(_: String, _: AddMembersToAccessGroupRequest)(_: HeaderCarrier, _: ExecutionContext))
      .expects(id, payload, *, *)
      .returning(Future successful Done)

  def expectGroupNameCheckOK(arn: Arn, groupName: String)
                          (implicit groupService: GroupService): CallHandler4[Arn, String, HeaderCarrier, ExecutionContext, Future[Boolean]] =
    (groupService.groupNameCheck(_:Arn, _:String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, groupName, *, *)
      .returning(Future successful true).once()

  def expectGroupNameCheckConflict(arn: Arn, groupName: String)
                            (implicit groupService: GroupService): CallHandler4[Arn, String, HeaderCarrier, ExecutionContext, Future[Boolean]] =
    (groupService.groupNameCheck(_:Arn, _:String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, groupName, *, *)
      .returning(Future successful false).once()
}
