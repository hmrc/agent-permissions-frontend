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

import models.{AddTeamMembersToGroup, TeamMember}
import org.scalamock.scalatest.MockFactory
import play.api.mvc.Request
import services.TeamMemberService
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, PaginatedList, PaginationMetaData}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait TeamMemberServiceMocks extends MockFactory {

  def expectGetFilteredTeamMembersElseAll(arn: Arn)
                                         (teamMembers: Seq[TeamMember])
                                         (implicit teamMemberService: TeamMemberService): Unit =
    (teamMemberService
      .getFilteredTeamMembersElseAll(_: Arn)(_: HeaderCarrier,
        _: ExecutionContext, _: Request[_]))
      .expects(arn, *, *, *)
      .returning(Future successful teamMembers)

  def expectSavePageOfTeamMembers(formData: AddTeamMembersToGroup, teamMembers: Seq[TeamMember] = Seq.empty)
                                 (implicit teamMemberService: TeamMemberService): Unit =
    (teamMemberService
      .savePageOfTeamMembers(_: AddTeamMembersToGroup)
      (_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
      .expects(formData, *, *, *)
      .returning(Future successful teamMembers)

  def expectGetPageOfTeamMembers(arn: Arn, page: Int = 1, pageSize: Int = 10)
                                (teamMembers: Seq[TeamMember])
                                (implicit teamMemberService: TeamMemberService): Unit = {
    val paginatedList = PaginatedList(pageContent = teamMembers,
      paginationMetaData = PaginationMetaData(false, page == 1, 40, 40 / pageSize, pageSize, page, teamMembers.length))
    (teamMemberService
      .getPageOfTeamMembers(_: Arn)(_: Int, _: Int)(_: HeaderCarrier,
        _: ExecutionContext, _: Request[_]))
      .expects(arn, page, pageSize, *, *, *)
      .returning(Future successful paginatedList)
  }

  def expectLookupTeamMember(arn: Arn)
                            (teamMember: TeamMember)
                            (implicit teamMemberService: TeamMemberService): Unit =
    (teamMemberService
      .lookupTeamMember(_: Arn)(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, teamMember.id, *, *)
      .returning(Future successful Some(teamMember)).once()

  def expectGetAllTeamMembers(arn: Arn)
                             (teamMembers: Seq[TeamMember])
                             (implicit teamMemberService: TeamMemberService): Unit =
    (teamMemberService
      .getAllTeamMembers(_: Arn)(_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
      .expects(arn, *, *, *)
      .returning(Future successful teamMembers).once()

  def expectSaveSelectedOrFilteredTeamMembers(arn: Arn)
                                             (buttonSelect: String, formData: AddTeamMembersToGroup)
                                             (implicit teamMemberService: TeamMemberService): Unit =
    (teamMemberService
      .saveSelectedOrFilteredTeamMembers(_: String)(_: Arn)(_: AddTeamMembersToGroup)
      (_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
      .expects(buttonSelect, arn, formData, *, *, *)
      .returning(Future successful (())).once()
}
