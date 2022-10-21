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

import models.{AddClientsToGroup, DisplayClient}
import org.scalamock.scalatest.MockFactory
import play.api.mvc.Request
import services.ClientService
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait ClientServiceMocks extends MockFactory {

  def expectGetFilteredClientsFromService(arn: Arn)
                                         (clients: Seq[DisplayClient])
                                         (implicit clientService: ClientService): Unit =
    (clientService
      .getFilteredClientsElseAll(_: Arn)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .returning(Future successful clients).once()

  def expectGetAllClientsFromService(arn: Arn)
                                 (clients: Seq[DisplayClient])
                                 (implicit clientService: ClientService): Unit =
    (clientService
      .getAllClients(_: Arn)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .returning(Future successful clients).once()

  def expectSaveSelectedOrFilteredClients(arn: Arn)(implicit clientService: ClientService): Unit =
    (clientService
      .saveSelectedOrFilteredClients(_: Arn)(_: AddClientsToGroup)
      (_: Arn => Future[Seq[DisplayClient]])(_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
      .expects(arn, *, *, *, *, *)
      .returning(Future.successful(()))
      .once()

  def expectLookupClient(arn: Arn)
                        (client: DisplayClient)
                        (implicit clientService: ClientService): Unit =
    (clientService
      .lookupClient(_: Arn)(_: String)( _: HeaderCarrier, _: ExecutionContext))
      .expects(arn, client.id, *, *)
      .returning(Future successful Some(client)).once()

}
