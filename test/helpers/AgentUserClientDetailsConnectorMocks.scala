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
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, PaginatedList, PaginationMetaData}
import uk.gov.hmrc.agents.accessgroups.{Client, UserDetails}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

trait AgentUserClientDetailsConnectorMocks extends MockFactory {

  def expectGetAucdClient(arn: Arn)(client: Client)(
    implicit agentUserClientDetailsConnector: AgentUserClientDetailsConnector): Unit =
    (agentUserClientDetailsConnector
      .getClient(_: Arn, _: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, client.enrolmentKey, *, *)
      .returning(Future successful Option(client)).once()

  def expectGetAucdClientNotFound(arn: Arn, enrolmentKey: String)(
    implicit agentUserClientDetailsConnector: AgentUserClientDetailsConnector): Unit =
    (agentUserClientDetailsConnector
      .getClient(_: Arn, _: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, enrolmentKey, *, *)
      .returning(Future successful None).once()
  def expectGetClients(arn: Arn)(clientList: Seq[Client])(
    implicit agentUserClientDetailsConnector: AgentUserClientDetailsConnector): Unit =
    (agentUserClientDetailsConnector
      .getClients(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful clientList).once()

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
    (agentUserClientDetailsConnector.updateClientReference(_: Arn, _: Client)(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *)
      .returning(Future successful Done)

  def expectGetPaginatedClients(arn: Arn)
                               (pageContent: Seq[Client])
                               (page: Int = 1,
                                pageSize: Int = 20,
                                search: Option[String] = None,
                                filter: Option[String] = None)
                               (implicit agentUserClientDetailsConnector: AgentUserClientDetailsConnector): Unit = {
    val paginationMetaData = PaginationMetaData(lastPage = false, firstPage = page == 1, 40, 40 / pageSize, pageSize, page, pageContent.length)
    val paginatedList = PaginatedList(pageContent, paginationMetaData)
    (agentUserClientDetailsConnector.getPaginatedClients(_: Arn)( _: Int, _: Int, _: Option[String], _: Option[String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, page, pageSize, search, filter, *, *)
      .returning(Future successful paginatedList)
  }


}
