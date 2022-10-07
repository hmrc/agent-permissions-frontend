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
import play.api.http.Status.{ACCEPTED, NO_CONTENT, OK}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client, UserDetails}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AgentUserClientDetailsConnectorImpl])
trait AgentUserClientDetailsConnector extends HttpAPIMonitor with Logging {
  val http: HttpClient

  def getClients(arn: Arn)(implicit hc: HeaderCarrier,
                           ec: ExecutionContext): Future[Option[Seq[Client]]]

  def getTeamMembers(arn: Arn)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext): Future[Option[Seq[UserDetails]]]

  def updateClientReference(arn: Arn, client: Client)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[Done]]

}

@Singleton
class AgentUserClientDetailsConnectorImpl @Inject()(val http: HttpClient)(
    implicit metrics: Metrics,
    appConfig: AppConfig)
    extends AgentUserClientDetailsConnector
    with HttpAPIMonitor
    with Logging {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private lazy val baseUrl = appConfig.agentUserClientDetailsBaseUrl

  def getClients(arn: Arn)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext): Future[Option[Seq[Client]]] = {
    val url = s"$baseUrl/agent-user-client-details/arn/${arn.value}/client-list"
    monitor("ConsumedAPI-getClientList-GET") {
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case ACCEPTED => None
          case OK       => response.json.asOpt[Seq[Client]]
          case e =>
            logger.warn(
              s"Got $e when fetching clients for ${arn.value}. Response message: '${response.body}''")
            None
        }
      }
    }
  }

  override def getTeamMembers(arn: Arn)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext): Future[Option[Seq[UserDetails]]] = {
    val url =
      s"$baseUrl/agent-user-client-details/arn/${arn.value}/team-members"
    monitor("ConsumedAPI-team-members-GET") {
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case ACCEPTED => None
          case OK       => response.json.asOpt[Seq[UserDetails]]
          case e =>
            logger.warn(
              s"Got $e when fetching team members for ${arn.value}. Response message: '${response.body}''")
            None
        }
      }
    }

  }

  override def updateClientReference(arn: Arn, client: Client)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[Done]] = {
    val url =
      s"$baseUrl/agent-user-client-details/arn/${arn.value}/update-friendly-name"

    monitor("ConsumedAPI-update-friendly-name-PUT") {
      http
        .PUT[Client, HttpResponse](url, client).map { response =>
        response.status match {
          case NO_CONTENT => Some(Done)
          case e =>
            logger.warn(
              s"Got $e when PUTing friendlyName for $client with agent ${arn.value}. Response message: '${response.body}''")
            None
        }
      }
    }

  }


}
