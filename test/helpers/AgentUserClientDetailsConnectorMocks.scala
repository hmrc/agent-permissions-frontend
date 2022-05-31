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

import connectors.AgentUserClientDetailsConnector
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import scala.concurrent.{ExecutionContext, Future}

trait AgentUserClientDetailsConnectorMocks extends MockFactory{

  def stubGetClientListOk(arn: Arn)(clientList: Seq[Client])(implicit agentUserClientDetailsConnector: AgentUserClientDetailsConnector): Unit =
    (agentUserClientDetailsConnector.getClientList(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful (Some(clientList)))

  def stubGetClientListAccepted(arn: Arn)(implicit agentUserClientDetailsConnector: AgentUserClientDetailsConnector): Unit =
    (agentUserClientDetailsConnector.getClientList(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful None)

  def stubGetClientListError(arn: Arn)(implicit agentUserClientDetailsConnector: AgentUserClientDetailsConnector): Unit =
    (agentUserClientDetailsConnector.getClientList(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .throwing(UpstreamErrorResponse.apply("error", 503))
}
