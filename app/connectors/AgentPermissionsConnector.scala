/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroupSummary => GroupSummary, TaxServiceAccessGroup => TaxGroup, _}
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, UpstreamErrorResponse}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AgentPermissionsConnectorImpl])
trait AgentPermissionsConnector extends HttpAPIMonitor with Logging {

  val http: HttpClient


  def getOptInStatus(arn: Arn)
                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[OptinStatus]]

  def optIn(arn: Arn, lang: Option[String])
           (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def optOut(arn: Arn)
            (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def createGroup(arn: Arn)
                 (groupRequest: GroupRequest)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def getGroupSummaries(arn: Arn)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[GroupSummary]]

  def unassignedClients(arn: Arn)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayClient]]

  def getPaginatedClientsForCustomGroup(id: String)
                         (page: Int = 1, pageSize: Int = 20, search: Option[String]= None, filter: Option[String]= None)
                         (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[PaginatedList[Client]]

  @deprecated("group could be too big with 5000+ clients - use getCustomGroupSummary & paginated lists instead")
  def getGroup(id: String)
              (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AccessGroup]]

  def getCustomSummary(id: String)
              (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[GroupSummary]]

  def getGroupsForClient(arn: Arn, enrolmentKey: String)
                        (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[GroupSummary]]

  def getGroupsForTeamMember(arn: Arn, agentUser: AgentUser)
                            (implicit hc: HeaderCarrier, ec: ExecutionContext)
  : Future[Option[Seq[GroupSummary]]]

  def updateGroup(id: String, groupRequest: UpdateAccessGroupRequest)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def addMembersToGroup(id: String, groupRequest: AddMembersToAccessGroupRequest)
                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def deleteGroup(id: String)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def groupNameCheck(arn: Arn, name: String)
                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean]

  def isArnAllowed(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean]

  def getAvailableTaxServiceClientCount(arn: Arn)
                                       (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]]

  def getTaxGroupClientCount(arn: Arn)
                            (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]]

  def createTaxServiceGroup(arn: Arn)
                           (createTaxServiceGroupRequest: CreateTaxServiceGroupRequest)
                           (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String]

  def getTaxServiceGroup(groupId: String)
                        (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TaxGroup]]

  def deleteTaxGroup(id: String)
                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def updateTaxGroup(groupId: String, group: UpdateTaxServiceGroupRequest)
                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]


}

@Singleton
class AgentPermissionsConnectorImpl @Inject()(val http: HttpClient)
                                             (implicit metrics: Metrics, appConfig: AppConfig)
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

  def optIn(arn: Arn, lang: Option[String])(implicit hc: HeaderCarrier,
                                            ec: ExecutionContext): Future[Done] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/optin" + lang.fold("")("?lang=" + _)
    monitor("ConsumedAPI-optin-POST") {
      http.POSTEmpty[HttpResponse](url).map { response =>
        response.status match {
          case CREATED => Done
          case CONFLICT =>
            logger.warn(s"Tried to optin $arn when already opted in")
            Done
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
          case CONFLICT =>
            logger.warn(s"Tried to optout $arn when already opted out")
            Done
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
          case OK => true
          case CONFLICT => false
          case e => throw UpstreamErrorResponse("error on groupNameCheck", e)
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

  def getGroupSummaries(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[GroupSummary]] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/all-groups"
    monitor("ConsumedAPI-allGroupSummaries-GET") {
      http.GET[HttpResponse](url).map { response: HttpResponse =>
        response.status match {
          case OK => response.json.as[Seq[GroupSummary]]
          case anyOtherStatus =>
            throw UpstreamErrorResponse(s"error getting group summaries for arn $arn, from $url", anyOtherStatus)
        }
      }
    }
  }

  def unassignedClients(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayClient]] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/unassigned-clients"
    monitor("ConsumedAPI-unassigned-clients-GET") {
      http.GET[HttpResponse](url).map { response: HttpResponse =>
        response.status match {
          case OK =>
            val clients = response.json.as[Seq[Client]]
            clients.map(DisplayClient.fromClient(_))
          case anyOtherStatus =>
            throw UpstreamErrorResponse(s"error getting unassigned clients for arn $arn, from $url", anyOtherStatus)
        }
      }
    }
  }

  def getGroupsForClient(arn: Arn, enrolmentKey: String)(implicit hc: HeaderCarrier,
                                                         ec: ExecutionContext): Future[Seq[GroupSummary]] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/client/$enrolmentKey/groups"
    monitor("ConsumedAPI-groupSummariesForClient-GET") {
      http.GET[HttpResponse](url).map { response: HttpResponse =>
        val eventuallySummaries = response.status match {
          case OK => response.json.as[Seq[GroupSummary]]
          case NOT_FOUND => Seq.empty[GroupSummary]
          case e =>
            throw UpstreamErrorResponse(
              s"error getting group summary for arn: $arn, client: $enrolmentKey from $url",
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

  @deprecated("group could be too big with 5000+ clients - use getCustomGroupSummary & paginated lists instead")
  def getGroup(id: String)
              (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AccessGroup]] = {
    val url = s"$baseUrl/agent-permissions/groups/$id"
    monitor("ConsumedAPI-group-GET") {
      http
        .GET[HttpResponse](url)
        .map { response =>
          response.status match {
            case OK => response.json.asOpt[AccessGroup]
            case NOT_FOUND =>
              logger.warn(s"ERROR GETTING GROUP DETAILS FOR GROUP $id, from $url")
              None
            case anyOtherStatus =>
              throw UpstreamErrorResponse(s"error getting group details for group $id, from $url", anyOtherStatus)
          }
        }
    }
  }

  def getCustomSummary(id: String)
              (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[GroupSummary]] = {
    val url = s"$baseUrl/agent-permissions/custom-group/$id"
        monitor("ConsumedAPI-customGroupSummary-GET") {
          http
            .GET[HttpResponse](url)
            .map { response =>
              response.status match {
                case OK => response.json.asOpt[GroupSummary]
                case NOT_FOUND =>
                  logger.warn(s"ERROR GETTING GROUP DETAILS FOR GROUP $id, from $url")
                  None
                case anyOtherStatus =>
                  throw UpstreamErrorResponse(s"error getting group details for group $id, from $url", anyOtherStatus)
              }
            }
        }
  }


  def getPaginatedClientsForCustomGroup(id: String)
                         (page: Int, pageSize: Int, search: Option[String]= None, filter: Option[String]= None)
                         (implicit hc: HeaderCarrier, ec: ExecutionContext)
  : Future[PaginatedList[Client]] = {
    val searchParam = search.fold("")(searchTerm => s"&search=$searchTerm")
    val filterParam = filter.fold("")(filterTerm => s"&filter=$filterTerm")
    val url = s"$baseUrl/agent-permissions/group/$id/clients" +
      s"?page=$page&pageSize=$pageSize$searchParam$filterParam"
    monitor("ConsumedAPI-getPaginatedClientsForGroup-GET") {
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case OK => response.json.as[PaginatedList[Client]]
          case e => throw UpstreamErrorResponse(s"error getClientList for group $id", e)
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
              throw UpstreamErrorResponse(s"error PUTing members to group request to $url", anyOtherStatus)
          }
        }
    }
  }

  override def deleteGroup(id: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    val url = s"$baseUrl/agent-permissions/groups/$id"
    monitor("ConsumedAPI-custom-group-DELETE") {
      deleteAccessGroup(url)
    }
  }

  def deleteTaxGroup(id: String)
                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    val url = s"$baseUrl/agent-permissions/tax-group/$id"
    monitor("ConsumedAPI-tax-group-DELETE") {
      deleteAccessGroup(url)
    }
  }

  override def isArnAllowed(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    val url = s"$baseUrl/agent-permissions/arn-allowed"
    monitor("ConsumedAPI-GranPermsArnAllowed-GET") {
      http
        .GET[HttpResponse](url)
        .map { response =>
          response.status match {
            case OK => true
            case other =>
              logger.warn(s"ArnAllowed call returned status $other")
              false
          }
        }
    }
  }

  override def getAvailableTaxServiceClientCount(arn: Arn)
                                                (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/client-count/available-tax-services"
    monitor("ConsumedAPI-AvailableTaxServiceClientCount-GET") {
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case OK => response.json.as[Map[String, Int]]
          case e => throw UpstreamErrorResponse(s"error getting AvailableTaxService client count", e)
        }
      }
    }
  }

  override def getTaxGroupClientCount(arn: Arn)
                                     (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/client-count/tax-groups"
    monitor("ConsumedAPI-TaxGroupClientCount-GET") {
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case OK => response.json.as[Map[String, Int]]
          case e => throw UpstreamErrorResponse(s"error getting Tax Group client count", e)
        }
      }
    }
  }

  override def createTaxServiceGroup(arn: Arn)
                                    (createTaxServiceGroupRequest: CreateTaxServiceGroupRequest)
                                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] = {
    val url = s"$baseUrl/agent-permissions/arn/${arn.value}/tax-group"
    monitor("ConsumedAPI-createTaxServiceGroup-POST") {
      http
        .POST[CreateTaxServiceGroupRequest, HttpResponse](url, createTaxServiceGroupRequest)
        .map { response =>
          response.status match {
            case OK => response.json.asOpt[String].get
            case CREATED => response.json.asOpt[String].get
            case anyOtherStatus => throw UpstreamErrorResponse(s"error creating tax service group $url", anyOtherStatus)
          }
        }
    }
  }

  override def getTaxServiceGroup(groupId: String)
                                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TaxGroup]] = {
    val url = s"$baseUrl/agent-permissions/tax-group/$groupId"
    monitor("ConsumedAPI-getTaxServiceGroup-GET") {
      http
        .GET[HttpResponse](url)
        .map { response =>
          response.status match {
            case OK => response.json.asOpt[TaxGroup]
            case NOT_FOUND =>
              logger.warn(s"ERROR GETTING GROUP DETAILS FOR GROUP $groupId, from $url")
              None
            case anyOtherStatus =>
              throw UpstreamErrorResponse(s"error getting group details for group $groupId, from $url", anyOtherStatus)
          }
        }
    }

  }

  private def deleteAccessGroup(url: String)
                               (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    http.DELETE[HttpResponse](url).map { response =>
      response.status match {
        case OK => Done
        case anyOtherStatus =>
          throw UpstreamErrorResponse(s"error DELETING update group request to $url", anyOtherStatus)
      }
    }
  }

  def updateTaxGroup(groupId: String, patchRequest: UpdateTaxServiceGroupRequest)
                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {

    val url = s"$baseUrl/agent-permissions/tax-group/$groupId"
    monitor("ConsumedAPI-update tax group-PATCH") {
      http
        .PATCH[UpdateTaxServiceGroupRequest, HttpResponse](url, patchRequest)
        .map { response =>
          response.status match {
            case OK => Done
            case anyOtherStatus =>
              throw UpstreamErrorResponse(s"error PATCHing update group request to $url", anyOtherStatus)
          }
        }
    }
  }
}
