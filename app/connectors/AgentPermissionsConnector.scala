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
import models.DisplayClient
import play.api.Logging
import play.api.http.Status.{CREATED, OK}
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Arn, Client, Enrolment, OptinStatus}
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AgentPermissionsConnectorImpl])
trait AgentPermissionsConnector extends HttpAPIMonitor with Logging {

  val http: HttpClient

  def getOptInStatus(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[OptinStatus]]

  def optIn(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def optOut(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def createGroup(arn: Arn)(groupRequest: GroupRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def groupsSummaries(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[(Seq[GroupSummary], Seq[DisplayClient])]]
}

@Singleton
class AgentPermissionsConnectorImpl @Inject()(val http: HttpClient)
                                             (implicit metrics: Metrics, appConfig: AppConfig)
  extends AgentPermissionsConnector {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val baseUrl = appConfig.agentPermissionsBaseUrl

  override def getOptInStatus(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[OptinStatus]] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/optin-status"
    monitor("ConsumedAPI-GetOptinStatus-GET") {
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case OK => response.json.asOpt[OptinStatus]
          case e => logger.warn(s"getOptInStatus returned status $e ${response.body}"); None
        }
      }
    }
  }

  def optIn(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/optin"
    monitor("ConsumedAPI-optin-POST") {
      http.POSTEmpty[HttpResponse](url).map { response =>
        response.status match {
          case CREATED => Done
          case e => throw UpstreamErrorResponse(s"error sending opt-in request for ${arn.value}", e)
        }
      }
    }
  }

  def optOut(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/optout"
    monitor("ConsumedAPI-optout-POST") {
      http.POSTEmpty[HttpResponse](url).map { response =>
        response.status match {
          case CREATED => Done
          case e => throw UpstreamErrorResponse(s"error sending opt out request", e)
        }
      }
    }
  }


  def createGroup(arn: Arn)(groupRequest: GroupRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/group/create "
    monitor("ConsumedAPI-createGroup-POST") {
      http.POST[GroupRequest, HttpResponse](url, groupRequest).map { response =>
        response.status match {
          case CREATED        => Done
          case anyOtherStatus => throw UpstreamErrorResponse(s"error posting createGroup request to $url", anyOtherStatus)
        }
      }
    }
  }

  def groupsSummaries(arn: Arn)
                     (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[(Seq[GroupSummary], Seq[DisplayClient])]] =  {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/groups-information "
    monitor("ConsumedAPI-groupSummaries-GET") {
      http.GET[HttpResponse](url).map { response =>
        println(response.json.toString())

        val maybeSummaries = response.status match {
          case OK => response.json.asOpt[AccessGroupSummaries]
          case anyOtherStatus => throw UpstreamErrorResponse(s"error getting group summary for arn $arn, from $url", anyOtherStatus)
        }
        val maybeTuple = maybeSummaries.map { maybeSummaries =>
          (maybeSummaries.groups, maybeSummaries.unassignedClients.map(DisplayClient.fromClient(_)).toSeq)
        }
        maybeTuple
      }
    }
  }
}

case class GroupRequest(groupName: String, teamMembers: Option[Seq[AgentUser]], clients: Option[Seq[Enrolment]])

case object GroupRequest {
  implicit val formatCreateAccessGroupRequest: OFormat[GroupRequest] = Json.format[GroupRequest]
}

case class GroupSummary(groupId: String, groupName: String, clientCount: Int, teamMemberCount: Int)

case object GroupSummary{
  implicit val formatCreateAccessGroupRequest: OFormat[GroupSummary] = Json.format[GroupSummary]
}

case class AccessGroupSummaries(groups: Seq[GroupSummary], unassignedClients: Set[Client])

object AccessGroupSummaries {
  implicit val format: OFormat[AccessGroupSummaries] = Json.format[AccessGroupSummaries]
}
