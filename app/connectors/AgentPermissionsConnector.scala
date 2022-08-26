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
import play.api.http.Status.{CONFLICT, CREATED, NOT_FOUND, OK}
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AgentPermissionsConnectorImpl])
trait AgentPermissionsConnector extends HttpAPIMonitor with Logging {

  val http: HttpClient

  def getOptInStatus(arn: Arn)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext): Future[Option[OptinStatus]]

  def optIn(arn: Arn)(implicit hc: HeaderCarrier,
                      ec: ExecutionContext): Future[Done]

  def optOut(arn: Arn)(implicit hc: HeaderCarrier,
                       ec: ExecutionContext): Future[Done]

  def createGroup(arn: Arn)(groupRequest: GroupRequest)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext): Future[Done]

  def groupsSummaries(arn: Arn)(implicit hc: HeaderCarrier,
                                ec: ExecutionContext)
    : Future[Option[(Seq[GroupSummary], Seq[DisplayClient])]]

  def getGroup(id: String)(implicit hc: HeaderCarrier,
                           ec: ExecutionContext): Future[Option[AccessGroup]]

  def getGroupsForClient(arn: Arn, enrolment: Enrolment)(implicit hc: HeaderCarrier,
                                                         ec: ExecutionContext): Future[Option[Seq[GroupSummary]]]

  def getGroupsForTeamMember(arn: Arn, agentUser: AgentUser)(implicit hc: HeaderCarrier,
                                                         ec: ExecutionContext): Future[Option[Seq[GroupSummary]]]

  def updateGroup(id: String, groupRequest: UpdateAccessGroupRequest)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext): Future[Done]

  def addMembersToGroup(id: String, groupRequest: AddMembersToAccessGroupRequest)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext): Future[Done]

  def deleteGroup(id: String)(implicit hc: HeaderCarrier,
                              ec: ExecutionContext): Future[Done]

  def groupNameCheck(arn: Arn, name: String)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext): Future[Boolean]

  def isArnAllowed(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean]
}

@Singleton
class AgentPermissionsConnectorImpl @Inject()(val http: HttpClient)(
    implicit metrics: Metrics,
    appConfig: AppConfig)
    extends AgentPermissionsConnector {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private lazy val baseUrl = appConfig.agentPermissionsBaseUrl

  override def getOptInStatus(arn: Arn)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext): Future[Option[OptinStatus]] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/optin-status"
    monitor("ConsumedAPI-GetOptinStatus-GET") {
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case OK => response.json.asOpt[OptinStatus]
          case e =>
            logger.warn(s"getOptInStatus returned status $e ${response.body}")
            None
        }
      }
    }
  }

  def optIn(arn: Arn)(implicit hc: HeaderCarrier,
                      ec: ExecutionContext): Future[Done] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/optin"
    monitor("ConsumedAPI-optin-POST") {
      http.POSTEmpty[HttpResponse](url).map { response =>
        response.status match {
          case CREATED => Done
          case e =>
            throw UpstreamErrorResponse(
              s"error sending opt-in request for ${arn.value}",
              e)
        }
      }
    }
  }

  def optOut(arn: Arn)(implicit hc: HeaderCarrier,
                       ec: ExecutionContext): Future[Done] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/optout"
    monitor("ConsumedAPI-optout-POST") {
      http.POSTEmpty[HttpResponse](url).map { response =>
        response.status match {
          case CREATED => Done
          case e =>
            throw UpstreamErrorResponse(s"error sending opt out request", e)
        }
      }
    }
  }

  def groupNameCheck(arn: Arn, name: String)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext): Future[Boolean] = {

    val encodedName = URLEncoder.encode(name, UTF_8.name)
    val url =
      s"$baseUrl/agent-permissions/arn/${arn.value}/access-group-name-check?name=$encodedName"

    monitor("ConsumedAPI-accessGroupNameCheck-GET") {
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case OK       => true
          case CONFLICT => false
          case e        => throw UpstreamErrorResponse("error on groupNameCheck", e)
        }
      }
    }
  }

  def createGroup(arn: Arn)(groupRequest: GroupRequest)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext): Future[Done] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/groups"
    monitor("ConsumedAPI-createGroup-POST") {
      http.POST[GroupRequest, HttpResponse](url, groupRequest).map { response =>
        response.status match {
          case CREATED => Done
          case anyOtherStatus =>
            throw UpstreamErrorResponse(
              s"error posting createGroup request to $url",
              anyOtherStatus)
        }
      }
    }
  }

  def groupsSummaries(arn: Arn)(implicit hc: HeaderCarrier,
                                ec: ExecutionContext)
    : Future[Option[(Seq[GroupSummary], Seq[DisplayClient])]] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/groups"
    monitor("ConsumedAPI-groupSummaries-GET") {
      http.GET[HttpResponse](url).map { response: HttpResponse =>
        val eventuallySummaries = response.status match {
          case OK => response.json.asOpt[AccessGroupSummaries]
          case anyOtherStatus =>
            throw UpstreamErrorResponse(
              s"error getting group summary for arn $arn, from $url",
              anyOtherStatus)
        }
        val maybeTuple = eventuallySummaries.map { summaries =>
          (summaries.groups,
           summaries.unassignedClients.map(DisplayClient.fromClient(_)).toSeq)
        }
        maybeTuple
      }
    }
  }

  def getGroupsForClient(arn: Arn, enrolment: Enrolment)(implicit hc: HeaderCarrier,
                                ec: ExecutionContext): Future[Option[Seq[GroupSummary]]] = {
    val enrolmentKey = EnrolmentKey.enrolmentKeys(enrolment).head
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/client/$enrolmentKey/groups"
    monitor("ConsumedAPI-groupSummariesForClient-GET") {
      http.GET[HttpResponse](url).map { response: HttpResponse =>
        val eventuallySummaries = response.status match {
          case OK => response.json.asOpt[Seq[GroupSummary]]
          case NOT_FOUND => None
          case e =>
            throw UpstreamErrorResponse(
              s"error getting group summary for arn: $arn, client: $enrolment from $url",
              e)
        }
        val maybeGroups = eventuallySummaries.map { summaries =>
          summaries
        }
        maybeGroups
      }
    }
  }

  def getGroupsForTeamMember(arn: Arn, agentUser: AgentUser)(implicit hc: HeaderCarrier,
                                                         ec: ExecutionContext): Future[Option[Seq[GroupSummary]]] = {
    val userId = agentUser.id
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/team-member/$userId/groups"
    monitor("ConsumedAPI-groupSummariesForTeamMember-GET") {
      http.GET[HttpResponse](url).map { response: HttpResponse =>
        val eventuallySummaries = response.status match {
          case OK => response.json.asOpt[Seq[GroupSummary]]
          case NOT_FOUND => None
          case e =>
            throw UpstreamErrorResponse(
              s"error getting group summary for arn: $arn, teamMember: $userId from $url",
              e)
        }
        val maybeGroups = eventuallySummaries.map { summaries =>
          summaries
        }
        maybeGroups
      }
    }
  }

  override def getGroup(id: String)(
      implicit hc: HeaderCarrier,
      ec: ExecutionContext): Future[Option[AccessGroup]] = {
    val url = s"$baseUrl/agent-permissions/groups/$id"
    monitor("ConsumedAPI-group-GET") {
      http.GET[HttpResponse](url).map { response: HttpResponse =>
        response.status match {
          case OK => response.json.asOpt[AccessGroup]
          case NOT_FOUND =>
            logger.warn(s"ERROR GETTING GROUP DETAILS FOR GROUP $id, from $url")
            None
          case anyOtherStatus =>
            throw UpstreamErrorResponse(
              s"error getting group details for group $id, from $url",
              anyOtherStatus)

        }
      }
    }
  }

  override def updateGroup(id: String, groupRequest: UpdateAccessGroupRequest)
                          (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    val url = s"$baseUrl/agent-permissions/groups/$id"
    monitor("ConsumedAPI-update group-PATCH") {
      http
        .PATCH[UpdateAccessGroupRequest, HttpResponse](url, groupRequest)
        .map { response =>
          response.status match {
            case OK => Done
            case anyOtherStatus =>
              throw UpstreamErrorResponse(
                s"error PATCHing update group request to $url",
                anyOtherStatus)
          }
        }
    }
  }

  override def addMembersToGroup(id: String, groupRequest: AddMembersToAccessGroupRequest)
                                (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    val url = s"$baseUrl/agent-permissions/groups/$id/add-unassigned"
    monitor("ConsumedAPI- add members to group -PUT") {
      http
        .PUT[AddMembersToAccessGroupRequest, HttpResponse](url, groupRequest)
        .map { response =>
          response.status match {
            case OK => Done
            case anyOtherStatus =>
              throw UpstreamErrorResponse(
                s"error PUTing members to group request to $url",
                anyOtherStatus)
          }
        }
    }
  }

  override def deleteGroup(id: String)(implicit hc: HeaderCarrier,
                                       ec: ExecutionContext): Future[Done] = {
    val url = s"$baseUrl/agent-permissions/groups/$id"
    monitor("ConsumedAPI-update group-PATCH") {
      http.DELETE[HttpResponse](url).map { response =>
        response.status match {
          case OK => Done
          case anyOtherStatus =>
            throw UpstreamErrorResponse(
              s"error DELETING update group request to $url",
              anyOtherStatus)
        }
      }
    }
  }

  override def isArnAllowed(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    val url = s"$baseUrl/agent-permissions/arn-allowed"
    monitor("ConsumedAPI-GranPermsArnAllowed-GET") {
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case OK => true
          case other =>
            logger.warn(s"ArnAllowed call returned status $other")
            false
        }
      }
    }
  }
}

case class GroupRequest(groupName: String,
                        teamMembers: Option[Seq[AgentUser]],
                        clients: Option[Seq[Enrolment]])

case object GroupRequest {
  implicit val formatCreateAccessGroupRequest: OFormat[GroupRequest] =
    Json.format[GroupRequest]
}

case class GroupSummary(groupId: String,
                        groupName: String,
                        clientCount: Int,
                        teamMemberCount: Int)

case object GroupSummary {
  implicit val formatCreateAccessGroupRequest: OFormat[GroupSummary] =
    Json.format[GroupSummary]
}

case class AccessGroupSummaries(groups: Seq[GroupSummary],
                                unassignedClients: Set[Client])

object AccessGroupSummaries {
  implicit val format: OFormat[AccessGroupSummaries] =
    Json.format[AccessGroupSummaries]
}

case class UpdateAccessGroupRequest(
    groupName: Option[String] = None,
    teamMembers: Option[Set[AgentUser]] = None,
    clients: Option[Set[Enrolment]] = None
)
object UpdateAccessGroupRequest {
  implicit val format: OFormat[UpdateAccessGroupRequest] =
    Json.format[UpdateAccessGroupRequest]
}

case class AddMembersToAccessGroupRequest(
                                           teamMembers: Option[Set[AgentUser]] = None,
                                          clients: Option[Set[Enrolment]] = None
                                         )

object AddMembersToAccessGroupRequest {
  implicit val format: OFormat[AddMembersToAccessGroupRequest] = Json.format[AddMembersToAccessGroupRequest]
}