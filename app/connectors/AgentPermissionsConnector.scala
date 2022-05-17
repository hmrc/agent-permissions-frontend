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

package connectors

import akka.Done
import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import play.api.Logging
import play.api.http.Status.{ACCEPTED, OK}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, OptinStatus}
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AgentPermissionsConnectorImpl])
trait AgentPermissionsConnector extends HttpAPIMonitor with   Logging{
  val http: HttpClient

  def getOptinStatus(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[OptinStatus]]
  def optin(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]
}

@Singleton
class AgentPermissionsConnectorImpl @Inject()(val http: HttpClient)
                                         (implicit metrics: Metrics, appConfig: AppConfig)
  extends AgentPermissionsConnector {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val baseUrl = appConfig.agentPermissionsBaseUrl

  override def getOptinStatus(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[OptinStatus]] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/optin-status"
    monitor("ConsumedAPI-GetOptinStatus-GET"){
      http.GET[HttpResponse](url).map{ response =>
        response.status match {
          case OK => response.json.asOpt[OptinStatus]
          case e => logger.warn(s"getOptinStatus returned status $e ${response.body}"); None
        }
      }
    }
  }

  def optin(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/optin"
    monitor("ConsumedAPI-optin-POST"){
      http.POSTEmpty[HttpResponse](url).map{ response =>
        response.status match {
          case ACCEPTED => Done
          case e => throw UpstreamErrorResponse(s"error sending optin request for ${arn.value}",e)
        }
      }
    }
  }
}
