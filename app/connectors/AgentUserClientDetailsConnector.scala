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

import com.google.inject.ImplementedBy
import config.AppConfig
import models.AgencyDetails
import org.apache.pekko.Done
import play.api.Logging
import play.api.http.Status.{ACCEPTED, NOT_FOUND, NO_CONTENT, OK}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, PaginatedList}
import uk.gov.hmrc.agents.accessgroups.{Client, UserDetails}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics
import utils.HttpAPIMonitor

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AgentUserClientDetailsConnectorImpl])
trait AgentUserClientDetailsConnector extends HttpAPIMonitor with Logging {
  val http: HttpClientV2

  def getClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[Client]]

  def getClient(arn: Arn, enrolmentKey: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[Client]]

  def getPaginatedClients(
    arn: Arn
  )(page: Int, pageSize: Int, search: Option[String] = None, filter: Option[String] = None)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[PaginatedList[Client]]

  def getTeamMembers(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[UserDetails]]

  def updateClientReference(arn: Arn, client: Client)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def getAgencyDetails(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgencyDetails]]
}

@Singleton
class AgentUserClientDetailsConnectorImpl @Inject() (val http: HttpClientV2)(implicit
  val metrics: Metrics,
  appConfig: AppConfig,
  val ec: ExecutionContext
) extends AgentUserClientDetailsConnector with HttpAPIMonitor with Logging {

  private lazy val baseUrl = appConfig.agentUserClientDetailsBaseUrl

  def getClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[Client]] = {
    val url: URL = url"$baseUrl/agent-user-client-details/arn/${arn.value}/client-list"
    monitor("ConsumedAPI-getClientList-GET") {
      http.get(url).execute[HttpResponse].map { response =>
        response.status match {
          case ACCEPTED => Seq.empty[Client]
          case OK       => response.json.as[Seq[Client]]
          case e =>
            throw UpstreamErrorResponse(s"error getClientList for ${arn.value}", e)
        }
      }
    }
  }

  def getClient(arn: Arn, enrolmentKey: String)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Option[Client]] = {
    val url: URL = url"$baseUrl/agent-user-client-details/arn/${arn.value}/client/$enrolmentKey"
    monitor("ConsumedAPI-getClientList-GET") {
      http
        .get(url)
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case OK => Option(response.json.as[Client])
            case _  => None
          }
        }
    }
  }

  def getPaginatedClients(
    arn: Arn
  )(page: Int, pageSize: Int, search: Option[String] = None, filter: Option[String] = None)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[PaginatedList[Client]] = {
    val params = List(
      "page"     -> page,
      "pageSize" -> pageSize,
      "search"   -> search,
      "filter"   -> filter
    )
    val url: URL =
      url"$baseUrl/agent-user-client-details/arn/${arn.value}/clients?$params"
    monitor("ConsumedAPI-getClientList-GET") {
      http.get(url).execute[HttpResponse].map { response =>
        response.status match {
          case OK => response.json.as[PaginatedList[Client]]
          case e  => throw UpstreamErrorResponse(s"error getClientList for ${arn.value}", e)
        }
      }
    }
  }

  override def getTeamMembers(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[UserDetails]] = {
    val url: URL = url"$baseUrl/agent-user-client-details/arn/${arn.value}/team-members"
    monitor("ConsumedAPI-team-members-GET") {
      http.get(url).execute[HttpResponse].map { response =>
        response.status match {
          case ACCEPTED => Seq.empty[UserDetails]
          case OK       => response.json.as[Seq[UserDetails]]
          case e        => throw UpstreamErrorResponse(s"error getTeamMemberList for ${arn.value}", e)
        }
      }
    }

  }

  override def updateClientReference(arn: Arn, client: Client)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Done] = {
    val url: URL = url"$baseUrl/agent-user-client-details/arn/${arn.value}/update-friendly-name"

    monitor("ConsumedAPI-update-friendly-name-PUT") {
      http
        .put(url)
        .execute[HttpResponse]
        .map { response =>
          response.status match {
            case NO_CONTENT => Done
            case e =>
              throw UpstreamErrorResponse(s"error PUTing friendlyName for $client with agent ${arn.value}", e)
          }
        }
    }

  }

  override def getAgencyDetails(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgencyDetails]] = {
    val url: URL = url"$baseUrl/agent-user-client-details/arn/${arn.value}/agency-details"
    monitor("ConsumedAPI-agency-details-GET") {
      http.get(url).execute[HttpResponse].map { response =>
        response.status match {
          case OK        => response.json.asOpt[AgencyDetails]
          case NOT_FOUND => None
          case e         => throw UpstreamErrorResponse(s"error getTeamMemberList for ${arn.value}", e)
        }
      }
    }
  }
}
