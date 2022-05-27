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
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.DATA_KEY
import models.JourneySession
import play.api.mvc.Request
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OptInService @Inject()(
                              agentPermissionsConnector: AgentPermissionsConnector,
                              agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
                              sessionCacheRepository: SessionCacheRepository
                            ) {


  def processOptIn(arn: Arn)(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    for {
      _ <- agentPermissionsConnector.optIn(arn)
      maybeClientList <- agentUserClientDetailsConnector.getClientList(arn)
      status <- agentPermissionsConnector.getOptInStatus(arn)
      _ <- sessionCacheRepository
        .putSession(
          DATA_KEY,
          JourneySession(
            optInStatus = status.getOrElse(throw new RuntimeException("could not complete opt-in process as opt-in status was unavailable")),
            clientList = maybeClientList)
        )
    } yield Done
  }

}
