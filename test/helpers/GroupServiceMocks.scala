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

import models.{DisplayClient, TeamMember}
import org.scalamock.scalatest.MockFactory
import services.GroupService
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait GroupServiceMocks extends MockFactory{

  def stubGetTeamMembers(arn: Arn)(teamMembers: Seq[TeamMember])(implicit groupService: GroupService): Unit =
    (groupService.getTeamMembers(_: Arn)(_:Option[Seq[TeamMember]] )(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .returning(Future successful Some(teamMembers))

  def stubGetClients(arn: Arn)(clients: Seq[DisplayClient])(implicit groupService: GroupService): Unit =
    (groupService.getClients(_: Arn)(_:Option[Seq[DisplayClient]] )(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .returning(Future successful (Some(clients)))
}
