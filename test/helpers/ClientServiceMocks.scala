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

import models.{AddClientsToGroup, ButtonSelect, DisplayClient}
import org.scalamock.scalatest.MockFactory
import play.api.mvc.Request
import services.{ClientService, GroupService}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait ClientServiceMocks extends MockFactory {

  def stubGetClients(arn: Arn)(clients: Seq[DisplayClient])(
    implicit clientService: ClientService): Unit =
    (clientService
      .getClients(_: Arn)(_: Request[_], _: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .returning(Future successful Some(clients))

  def expectProcessFormDataForClients(buttonPress: ButtonSelect)(arn: Arn)(implicit clientService: ClientService): Unit =
    (clientService
      .saveSelectedOrFilteredClients(_: ButtonSelect)(_: Arn)(_: AddClientsToGroup)
      (_: HeaderCarrier,  _: ExecutionContext,  _: Request[_]))
      .expects(buttonPress, arn, *, *, *, *)
      .returning(Future successful ())

}
