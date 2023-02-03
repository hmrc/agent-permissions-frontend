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
import connectors.{AgentPermissionsConnector, GroupRequest, UpdateAccessGroupRequest}
import controllers.PaginationUtil
import models.DisplayClient
import org.scalamock.handlers.{CallHandler3, CallHandler4}
import org.scalamock.scalatest.MockFactory
import play.api.http.Status.BAD_REQUEST
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.agentmtdidentifiers.utils.PaginatedListBuilder
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

trait AgentPermissionsConnectorMocks extends MockFactory {

  def expectOptInStatusOk(arn: Arn)(optinStatus: OptinStatus)(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .getOptInStatus(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful Some(optinStatus))

  def expectOptInStatusError(arn: Arn)(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .getOptInStatus(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .throwing(UpstreamErrorResponse.apply("error", 503))

  def expectPostOptInAccepted(arn: Arn)(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .optIn(_: Arn, _: Option[String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .returning(Future successful Done)

  def expectPostOptInError(arn: Arn)(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .optIn(_: Arn, _: Option[String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .throwing(UpstreamErrorResponse.apply("error", 503))

  def expectPostOptOutAccepted(arn: Arn)(
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

  def expectGetGroupSummarySuccess(arn: Arn, summaries: Seq[GroupSummary])(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .getGroupSummaries(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful summaries)

  def expectGetUnassignedClientsSuccess(arn: Arn, clients: Seq[DisplayClient], page: Int = 1, pageSize: Int = 20, search: Option[String] = None, filter: Option[String] = None)(
    implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .unassignedClients(_: Arn)(_: Int, _: Int, _: Option[String], _: Option[String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, page, pageSize, search, filter, *, *)
      .returning(Future.successful(PaginatedListBuilder.build(page = page, pageSize = pageSize, fullList = PaginationUtil.filterClients(clients, search, filter))) )

  def expectGetGroupsForClientSuccess(
                                       arn: Arn,
                                       enrolmentKey: String,
                                       groups: Seq[GroupSummary])(
                                    implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .getGroupsForClient(_: Arn, _: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, enrolmentKey, *, *)
      .returning(Future successful groups)

  def expectGetGroupsForTeamMemberSuccess(
                                       arn: Arn,
                                       agentUser: AgentUser,
                                       groups: Option[Seq[GroupSummary]])(
                                       implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .getGroupsForTeamMember(_: Arn, _: AgentUser)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, agentUser, *, *)
      .returning(Future successful groups)

  def expectGetGroupSuccess(id: String, group: Option[CustomGroup])(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .getGroup(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(id, *, *)
      .returning(Future successful group)

  def expectGroupNameCheck(ok: Boolean)(arn: Arn, name: String)(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .groupNameCheck(_: Arn, _: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, name, *, *)
      .returning(Future successful ok)

  def expectGroupNameCheckError(arn: Arn, name: String)(
      implicit agentPermissionsConnector: AgentPermissionsConnector): Unit =
    (agentPermissionsConnector
      .groupNameCheck(_: Arn, _: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, name, *, *)
      .throwing(UpstreamErrorResponse.apply("error", 503))

  def expectUpdateGroupSuccess(id: String, updateGroupRequest: UpdateAccessGroupRequest)(
    implicit agentPermissionsConnector: AgentPermissionsConnector): CallHandler4[String, UpdateAccessGroupRequest, HeaderCarrier, ExecutionContext, Future[Done]] =
    (agentPermissionsConnector.updateGroup(_: String, _: UpdateAccessGroupRequest)(_: HeaderCarrier, _: ExecutionContext))
      .expects(id, updateGroupRequest, *, *)
      .returning(Future successful Done).once()

  def expectDeleteGroupSuccess(id: String)(
    implicit agentPermissionsConnector: AgentPermissionsConnector): CallHandler3[String, HeaderCarrier, ExecutionContext, Future[Done]] =
    (agentPermissionsConnector.deleteGroup(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(id, *, *)
      .returning(Future successful Done)

  def expectGetAvailableTaxServiceClientCountFromConnector(arn: Arn)(implicit agentPermissionsConnector: AgentPermissionsConnector): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Map[String, Int]]] =
    (agentPermissionsConnector
      .getAvailableTaxServiceClientCount(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful Map("HMRC-MTD-IT" -> 1)).once()

  def expectGetTaxGroupClientCountFromConnector(arn: Arn)(implicit agentPermissionsConnector: AgentPermissionsConnector): CallHandler3[Arn, HeaderCarrier, ExecutionContext, Future[Map[String, Int]]] =
    (agentPermissionsConnector
      .getTaxGroupClientCount(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful Map("HMRC-MTD-IT" -> 1)).once()


}
