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

package services

import akka.Done
import connectors.AgentUserClientDetailsConnector
import controllers.CLIENT_REFERENCE
import models.DisplayClient
import models.DisplayClient.toEnrolment
import play.api.mvc.Request
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client, EnrolmentKey}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ManageClientService @Inject()(
    agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
    sessionCacheRepository: SessionCacheRepository
) {

  def updateClientReference(arn: Arn, displayClient: DisplayClient, newName: String)(implicit request: Request[_],
                       hc: HeaderCarrier,
                       ec: ExecutionContext): Future[Done] = {
    val client = Client(EnrolmentKey.enrolmentKeys(toEnrolment(displayClient)).head, newName)
    agentUserClientDetailsConnector.updateClientReference(arn, client)
  }

  def getNewNameFromSession()(implicit request: Request[_],
  ec: ExecutionContext): Future[Option[String]] = {
    for {
      newName <- sessionCacheRepository.getFromSession(CLIENT_REFERENCE)
    } yield newName
  }

}
