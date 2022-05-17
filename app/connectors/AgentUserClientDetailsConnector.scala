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

import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import play.api.Logging
import play.api.http.Status.{ACCEPTED, OK}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Enrolment}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@ImplementedBy(classOf[AgentUserClientDetailsConnectorImpl])
trait AgentUserClientDetailsConnector extends HttpAPIMonitor with Logging {
  val http: HttpClient

  def getClientList(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[Enrolment]]]

}

@Singleton
class AgentUserClientDetailsConnectorImpl @Inject()(val http: HttpClient)(implicit metrics: Metrics, appConfig: AppConfig) extends AgentUserClientDetailsConnector with HttpAPIMonitor with Logging {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val baseUrl = appConfig.agentPermissionsBaseUrl

  def getClientList(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[Enrolment]]] = {
    val url = s"$baseUrl/agent-user-client-details/arn/${arn.value}/client-list"
    monitor("ConsumedAPI-getClientList-GET") {
      http.GET[HttpResponse](url).map{
        response => response.status match {
          case ACCEPTED   => None
          case OK         =>  response.json.asOpt[Seq[Enrolment]]
          case e          => throw UpstreamErrorResponse(s"error getClientList for ${arn.value}",e)
        }
      }
    }
  }
}
