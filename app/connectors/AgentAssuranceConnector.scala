/*
 * Copyright 2025 HM Revenue & Customs
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

package connectors

import config.AppConfig
import play.api.http.Status.NOT_FOUND
import play.api.http.Status.NO_CONTENT
import play.api.http.Status.OK
import uk.gov.hmrc.agentmtdidentifiers.model.{SuspensionDetails, SuspensionDetailsNotFound}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AgentAssuranceConnector @Inject() (http: HttpClientV2)(implicit
  val metrics: Metrics,
  appConfig: AppConfig
) {

  def getSuspensionDetails()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SuspensionDetails] = {
    val url = url"${appConfig.agentAssuranceBaseUrl}/agent-assurance/agent-record-with-checks"
    val timer = metrics.defaultRegistry.timer("Timer-ConsumerAPI-AA-AgencySuspensionDetails-GET")
    timer.time()
    http.get(url).execute[HttpResponse].map { response =>
      timer.time().stop()
      response.status match {
        case OK         => (response.json \ "suspensionDetails").as[SuspensionDetails]
        case NO_CONTENT => SuspensionDetails(suspensionStatus = false, None)
        case NOT_FOUND  => throw SuspensionDetailsNotFound("No record found for this agent")
        case _ =>
          throw UpstreamErrorResponse(s"Error ${response.status} unable to get suspension details", response.status)
      }
    }
  }
}
