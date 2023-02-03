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

import models.{AddClientsToGroup, DisplayClient}
import org.scalamock.scalatest.MockFactory
import play.api.mvc.Request
import services.SessionCacheOperationsService
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait SessionCacheOperationsMocks extends MockFactory {

  def expectSaveSelectedOrFilteredClients(arn: Arn)(implicit sessionCacheOps: SessionCacheOperationsService): Unit =
    (sessionCacheOps
      .saveSelectedOrFilteredClients(_: Arn)(_: AddClientsToGroup)
      (_: Arn => Future[Seq[DisplayClient]])(_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
      .expects(arn, *, *, *, *, *)
      .returning(Future.successful(()))
      .once()

  def expectSavePageOfClients(formData: AddClientsToGroup, members: Seq[DisplayClient] = Seq.empty)
                                 (implicit sessionCacheOps: SessionCacheOperationsService): Unit =
    (sessionCacheOps
      .savePageOfClients(_: AddClientsToGroup)
      (_: HeaderCarrier, _: ExecutionContext, _: Request[_]))
      .expects(formData, *, *, *)
      .returning(Future successful members)

  def expectSaveSearch(searchTerm: Option[String] = None, filterTerm: Option[String] = None)(implicit sessionCacheOps: SessionCacheOperationsService): Unit =
    (sessionCacheOps
      .saveSearch(_: Option[String], _: Option[String])
      (_: Request[_], _: HeaderCarrier, _: ExecutionContext))
      .expects(searchTerm, filterTerm, *, *, *)
      .returning(Future.successful(()))
      .once()
}
