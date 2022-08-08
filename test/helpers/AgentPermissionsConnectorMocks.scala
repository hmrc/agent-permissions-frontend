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
import connectors.{AgentPermissionsConnector, GroupRequest, GroupSummary, UpdateAccessGroupRequest}
import models.DisplayClient
import org.scalamock.handlers.CallHandler4
import org.scalamock.scalatest.MockFactory
import play.api.http.Status.BAD_REQUEST
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, Arn, Enrolment, OptinStatus}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

trait AgentPermissionsConnectorMocks extends MockFactory {

  def stubOptInStatusOk(arn: Arn)(optinStatus: OptinStatus)(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .getOptInStatus(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful Some(optinStatus))

  def stubOptInStatusError(arn: Arn)(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .getOptInStatus(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .throwing(UpstreamErrorResponse.apply("error", 503))

  def stubPostOptInAccepted(arn: Arn)(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .optIn(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful Done)

  def stubPostOptInError(arn: Arn)(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .optIn(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .throwing(UpstreamErrorResponse.apply("error", 503))

  def stubPostOptOutAccepted(arn: Arn)(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .optOut(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful Done)

  def expectCreateGroupSuccess(arn: Arn, groupRequest: GroupRequest)(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .createGroup(_: Arn)(_: GroupRequest)(_: HeaderCarrier,
                                            _: ExecutionContext))
      .expects(arn, groupRequest, *, *)
      .returning(Future successful Done)

  def expectCreateGroupFails(arn: Arn)(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .createGroup(_: Arn)(_: GroupRequest)(_: HeaderCarrier,
                                            _: ExecutionContext))
      .expects(arn, *, *, *)
      .throwing(UpstreamErrorResponse.apply("error", BAD_REQUEST))

  def expectGetGroupSummarySuccess(
      arn: Arn,
      summaries: Option[(Seq[GroupSummary], Seq[DisplayClient])])(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .groupsSummaries(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful summaries)

  def expectGetGroupsForClientSuccess(
                                    arn: Arn,
                                    enrolment: Enrolment,
                                    groups: Option[Seq[GroupSummary]])(
                                    implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .getGroupsForClient(_: Arn, _: Enrolment)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, enrolment, *, *)
      .returning(Future successful groups)

  def expectGetGroupSuccess(id: String, group: Option[AccessGroup])(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .getGroup(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(id, *, *)
      .returning(Future successful group)

  def getGroupNameCheckReturns(ok: Boolean)(arn: Arn, name: String)(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .groupNameCheck(_: Arn, _: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, name, *, *)
      .returning(Future successful ok)

  def getGroupNameCheckError(arn: Arn, name: String)(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .groupNameCheck(_: Arn, _: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, name, *, *)
      .throwing(UpstreamErrorResponse.apply("error", 503))

  def expectUpdateGroupSuccess(id: String, updateGroupRequest: UpdateAccessGroupRequest)(
    implicit agentPermissionsConnector: AgentPermissionsConnector) =
    (agentPermissionsConnector.updateGroup(_: String, _: UpdateAccessGroupRequest)(_: HeaderCarrier, _: ExecutionContext))
      .expects(id, updateGroupRequest, *, *)
      .returning(Future successful Done)

}
