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

package helpers

import akka.Done
import connectors.{GroupSummary, UpdateAccessGroupRequest}
import models.{DisplayClient, TeamMember}
import org.scalamock.scalatest.MockFactory
import play.api.mvc.Request
import services.GroupService
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, Arn}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait GroupServiceMocks extends MockFactory {

  def expectGetTeamMembersFromGroup(arn: Arn)(teamMembers: Seq[TeamMember])(
    implicit groupService: GroupService): Unit =
    (groupService
      .getTeamMembersFromGroup(_: Arn)(_: Seq[TeamMember])
      (_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .returning(Future successful teamMembers).once()

  def expectGetGroupById(id: String, maybeGroup: Option[AccessGroup])(
    implicit groupService: GroupService): Unit =
    (groupService
      .getGroup(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(id, *, *)
      .returning(Future successful maybeGroup)

  def expectGetGroupsForArn(arn: Arn)(groups: Seq[GroupSummary])(implicit groupService: GroupService): Unit =
    (groupService
      .groups(_: Arn)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .returning(Future.successful(groups)).once()

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

  def expectUpdateGroup(id: String, payload: UpdateAccessGroupRequest)
                                      (implicit groupService: GroupService): Unit =
    (groupService
      .updateGroup(_: String, _: UpdateAccessGroupRequest)(_: HeaderCarrier,  _: ExecutionContext))
      .expects(id, payload, *, *)
      .returning(Future.successful(Done)).once()
}
