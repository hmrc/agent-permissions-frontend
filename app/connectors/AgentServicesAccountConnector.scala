/*
 * Copyright 2026 HM Revenue & Customs
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
import models.SuspensionDetails
import play.api.http.Status.OK
import play.api.libs.json.JsDefined
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.http.client.HttpClientV2

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentServicesAccountConnector @Inject() (http: HttpClientV2)(implicit
  appConfig: AppConfig,
  ec: ExecutionContext
) {

  def getSuspensionDetails()(implicit hc: HeaderCarrier): Future[SuspensionDetails] = {
    val url = url"${appConfig.agentServicesAccountBaseUrl}/agent-services-account/agent-record-with-checks"
    http.get(url).execute[HttpResponse].map { response =>
      response.status match {
        case OK =>
          response.json \ "suspensionDetails" match {
            case JsDefined(json) => json.as[SuspensionDetails]
            case _               => SuspensionDetails(suspensionStatus = false, None)
          }
        case _ =>
          throw UpstreamErrorResponse(s"Error ${response.status} unable to get suspension details", response.status)
      }
    }
  }

}
