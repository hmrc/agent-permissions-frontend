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

package services

import com.google.inject.ImplementedBy
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.{CLIENT_FILTER_INPUT, CLIENT_SEARCH_INPUT, CURRENT_PAGE_CLIENTS, SELECTED_CLIENTS, ToFuture}
import models.{DisplayClient, GroupId}
import org.apache.pekko.Done
import play.api.libs.json.JsNumber
import play.api.mvc.Request
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, PaginatedList}
import uk.gov.hmrc.agents.accessgroups.{Client, GroupSummary}
import uk.gov.hmrc.http.HeaderCarrier
import utils.EncryptionUtil

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ClientServiceImpl])
trait ClientService {

  def getPaginatedClients(arn: Arn)(page: Int, pageSize: Int)(implicit
    request: Request[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[PaginatedList[DisplayClient]]

  def getPaginatedClientsToAddToGroup(
    id: GroupId
  )(page: Int, pageSize: Int, search: Option[String] = None, filter: Option[String] = None)(implicit
    request: Request[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[(GroupSummary, PaginatedList[DisplayClient])]

  def getUnassignedClients(
    arn: Arn
  )(page: Int = 1, pageSize: Int = 20, search: Option[String] = None, filter: Option[String] = None)(implicit
    request: Request[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[PaginatedList[DisplayClient]]

  def lookupClient(arn: Arn)(
    clientId: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DisplayClient]]

  def getClient(arn: Arn)(
    clientId: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DisplayClient]]

  def lookupClients(arn: Arn)(
    ids: Option[List[String]]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[DisplayClient]]

  def updateClientReference(arn: Arn, displayClient: DisplayClient, newName: String)(implicit
    request: Request[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Done]

  def getAvailableTaxServiceClientCount(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]]

}

@Singleton
class ClientServiceImpl @Inject() (
  agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
  agentPermissionsConnector: AgentPermissionsConnector,
  val sessionCacheService: SessionCacheService
) extends ClientService with GroupMemberOps {

  def getPaginatedClients(arn: Arn)(page: Int = 1, pageSize: Int = 20)(implicit
    request: Request[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[PaginatedList[DisplayClient]] =
    for {
      searchTerm <- sessionCacheService.get(CLIENT_SEARCH_INPUT)
      filterTerm <- sessionCacheService.get(CLIENT_FILTER_INPUT)
      pageOfClients <-
        agentUserClientDetailsConnector.getPaginatedClients(arn)(page, pageSize, searchTerm, filterTerm)
      maybeSelectedClients <- sessionCacheService.get[Seq[DisplayClient]](SELECTED_CLIENTS)
      existingSelectedClientIds = maybeSelectedClients.getOrElse(Nil).map(_.id)
      pageOfClientsMarkedSelected =
        pageOfClients.pageContent
          .map(cl => DisplayClient.fromClient(cl))
          .map(dc => if (existingSelectedClientIds.contains(dc.id)) dc.copy(selected = true) else dc)
      totalClientsSelected = maybeSelectedClients.fold(0)(_.length)
      metadataWithExtra = pageOfClients.paginationMetaData.copy(extra =
                            Some(Map("totalSelected" -> JsNumber(totalClientsSelected)))
                          ) // This extra data is needed to display correct 'selected' count in front-end
      _ <- sessionCacheService.put(CURRENT_PAGE_CLIENTS, pageOfClientsMarkedSelected)
    } yield PaginatedList(pageOfClientsMarkedSelected, metadataWithExtra)

  def getPaginatedClientsToAddToGroup(
    id: GroupId
  )(page: Int, pageSize: Int, search: Option[String] = None, filter: Option[String] = None)(implicit
    request: Request[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[(GroupSummary, PaginatedList[DisplayClient])] =
    for {
      maybeSelectedClients <- sessionCacheService.get[Seq[DisplayClient]](SELECTED_CLIENTS)
      existingSelectedClientIds = maybeSelectedClients.getOrElse(Nil).map(_.id)
      tuple <- agentPermissionsConnector.getPaginatedClientsToAddToGroup(id)(page, pageSize, search, filter)
      pageOfClientsMarkedSelected =
        tuple._2.pageContent
          .map(dc => if (existingSelectedClientIds.contains(dc.id)) dc.copy(selected = true) else dc)
      _ <- sessionCacheService.put(CURRENT_PAGE_CLIENTS, pageOfClientsMarkedSelected)
      clientsMarkedAsSelected =
        tuple._2.pageContent.map(dc => if (existingSelectedClientIds.contains(dc.id)) dc.copy(selected = true) else dc)
      x = (
            tuple._1,
            PaginatedList[DisplayClient](
              pageContent = clientsMarkedAsSelected,
              paginationMetaData = tuple._2.paginationMetaData
                .copy(extra = Some(Map("totalSelected" -> JsNumber(maybeSelectedClients.getOrElse(Seq.empty).length))))
            )
          )
    } yield x

  def getUnassignedClients(
    arn: Arn
  )(page: Int = 1, pageSize: Int = 20, search: Option[String] = None, filter: Option[String] = None)(implicit
    request: Request[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[PaginatedList[DisplayClient]] =
    agentPermissionsConnector.unassignedClients(arn)(page, pageSize, search, filter)

  def lookupClient(
    arn: Arn
  )(clientId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DisplayClient]] =
    for {
      es3AsDisplayClients <- getFromEs3AsDisplayClients(arn)
      maybeClient = es3AsDisplayClients.find(_.id == clientId)
    } yield maybeClient

  def getClient(
    arn: Arn
  )(clientId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DisplayClient]] = {
    val enrolmentKey = EncryptionUtil.decryptEnrolmentKey(clientId)
    agentUserClientDetailsConnector
      .getClient(arn, enrolmentKey)
      .map(maybeClient => maybeClient.map(c => DisplayClient.fromClient(c)))
  }

  def lookupClients(
    arn: Arn
  )(ids: Option[List[String]])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[DisplayClient]] =
    ids.fold(List.empty[DisplayClient].toFuture) { ids =>
      getFromEs3AsDisplayClients(arn).map(clients => clients.filter(c => ids.contains(c.id)).toList)
    }

  private def getFromEs3AsDisplayClients(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[DisplayClient]] =
    for {
      es3Clients <- agentUserClientDetailsConnector.getClients(arn)
      es3AsDisplayClients = es3Clients.map(client => DisplayClient.fromClient(client))
    } yield es3AsDisplayClients

  def updateClientReference(arn: Arn, displayClient: DisplayClient, newName: String)(implicit
    request: Request[_],
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Done] = {
    val client = Client(displayClient.enrolmentKey, newName)
    agentUserClientDetailsConnector.updateClientReference(arn, client)
  }

  def getAvailableTaxServiceClientCount(
    arn: Arn
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]] =
    agentPermissionsConnector.getAvailableTaxServiceClientCount(arn)

}
