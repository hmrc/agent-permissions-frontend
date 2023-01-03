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
import connectors.AgentUserClientDetailsConnector
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client, UserDetails}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

trait AgentUserClientDetailsConnectorMocks extends MockFactory {

  def expectGetClients(arn: Arn)(clientList: Seq[Client])(
      implicit agentUserClientDetailsConnector: AgentUserClientDetailsConnector): Unit =
    (agentUserClientDetailsConnector
      .getClients(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful clientList).once()

  def expectGetGroupTaxTypeInfoFromConnector(arn: Arn)(
    implicit agentUserClientDetailsConnector: AgentUserClientDetailsConnector): Unit =
    (agentUserClientDetailsConnector
      .getGroupTaxTypeInfo(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful Map("HMRC-MTD-IT" -> 1)).once()


  def expectGetClientsReturningNone(arn: Arn)(
      implicit agentUserClientDetailsConnector: AgentUserClientDetailsConnector): Unit =
    (agentUserClientDetailsConnector
      .getClients(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful Seq.empty)

  def expectGetClientsWithUpstreamError(arn: Arn)(
      implicit agentUserClientDetailsConnector: AgentUserClientDetailsConnector): Unit =
    (agentUserClientDetailsConnector
      .getClients(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .throwing(UpstreamErrorResponse.apply("error", 503))

  def expectGetTeamMembers(arn: Arn)(teamMembers: Seq[UserDetails])(
      implicit agentUserClientDetailsConnector: AgentUserClientDetailsConnector): Unit =
    (agentUserClientDetailsConnector
      .getTeamMembers(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful teamMembers)

  def expectGetTeamMembersWithUpstreamError(arn: Arn)(
      implicit agentUserClientDetailsConnector: AgentUserClientDetailsConnector): Unit =
    (agentUserClientDetailsConnector
      .getTeamMembers(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .throwing(UpstreamErrorResponse.apply("error", 503))

  def expectUpdateClientReferenceSuccess()
                                        (implicit agentUserClientDetailsConnector: AgentUserClientDetailsConnector): Unit =
    (agentUserClientDetailsConnector.updateClientReference(_: Arn, _:Client)(_: HeaderCarrier, _: ExecutionContext))
    .expects(*, *,*,*)
    .returning(Future successful Done)


}
