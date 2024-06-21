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
import org.scalatest.wordspec.AnyWordSpec
import play.api.mvc.Request
import services.SessionCacheOperationsService

import scala.concurrent.{ExecutionContext, Future}

trait SessionCacheOperationsMocks extends AnyWordSpec with MockFactory {

  def expectSavePageOfClients(formData: AddClientsToGroup, members: Seq[DisplayClient] = Seq.empty)(implicit
    sessionCacheOps: SessionCacheOperationsService
  ): Unit =
    (sessionCacheOps
      .savePageOfClientsForCreateGroup(_: AddClientsToGroup)(_: ExecutionContext, _: Request[_]))
      .expects(formData, *, *)
      .returning(Future successful members)

  def expectSaveClientsToAddToExistingGroup(formData: AddClientsToGroup, members: Seq[DisplayClient] = Seq.empty)(
    implicit sessionCacheOps: SessionCacheOperationsService
  ): Unit =
    (sessionCacheOps
      .saveClientsToAddToExistingGroup(_: AddClientsToGroup)(_: ExecutionContext, _: Request[_]))
      .expects(formData, *, *)
      .returning(Future successful members)

  def expectSaveSearch(searchTerm: Option[String] = None, filterTerm: Option[String] = None)(implicit
    sessionCacheOps: SessionCacheOperationsService
  ): Unit =
    (sessionCacheOps
      .saveSearch(_: Option[String], _: Option[String])(_: Request[_], _: ExecutionContext))
      .expects(searchTerm, filterTerm, *, *)
      .returning(Future.successful(()))
      .once()
}
