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

import models.{AddTeamMembersToGroup, ButtonSelect, TeamMember}
import play.api.mvc.Request
import services.{GroupService, TeamMemberService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier
import org.scalamock.scalatest.MockFactory

import scala.concurrent.{ExecutionContext, Future}

trait TeamMemberServiceMocks extends MockFactory {

  def stubGetTeamMembers(arn: Arn)(teamMembers: Seq[TeamMember])(
    implicit teamMemberService: TeamMemberService): Unit =
    (teamMemberService
      .getTeamMembers(_: Arn)(_: HeaderCarrier,
        _: ExecutionContext, _: Request[_]))
      .expects(arn, *, *, *)
      .returning(Future successful Some(teamMembers))

  def expectProcessFormDataForTeamMembers(buttonPress: ButtonSelect)(arn: Arn)(implicit teamMemberService: TeamMemberService): Unit =
    (teamMemberService
      .saveSelectedOrFilteredTeamMembers(_: ButtonSelect)(_: Arn)(_: AddTeamMembersToGroup)
      (_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
      .expects(buttonPress, arn, *, *, *, *)
      .returning(Future successful ())
}