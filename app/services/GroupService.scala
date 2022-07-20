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

package services

import connectors.AgentUserClientDetailsConnector
import controllers.{
  FILTERED_CLIENTS,
  FILTERED_TEAM_MEMBERS,
  SELECTED_CLIENTS,
  SELECTED_TEAM_MEMBERS,
  HIDDEN_CLIENTS_EXIST,
  HIDDEN_TEAM_MEMBERS_EXIST
}
import models.{
  AddClientsToGroup,
  AddTeamMembersToGroup,
  ButtonSelect,
  DisplayClient,
  Selectable,
  TeamMember
}
import models.ButtonSelect._
import play.api.libs.json.{Reads, Writes}
import play.api.mvc.Request
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.DataKey

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
@Singleton
class GroupService @Inject()(
    agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
    sessionCacheRepository: SessionCacheRepository
) {

  def getClients(
      arn: Arn
  )(implicit request: Request[_],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[Seq[DisplayClient]]] =
    for {
      es3Clients <- agentUserClientDetailsConnector.getClients(arn)
      es3AsDisplayClients = es3Clients.map(clientSeq =>
        clientSeq.map(client => DisplayClient.fromClient(client)))
      maybeSelectedClients <- sessionCacheRepository
        .getFromSession[Seq[DisplayClient]](SELECTED_CLIENTS)
      es3WithoutPreSelected = es3AsDisplayClients.map(
        _.filterNot(
          dc =>
            maybeSelectedClients.fold(false)(
              _.map(_.hmrcRef).contains(dc.hmrcRef)))
      )
      mergedWithPreselected = es3WithoutPreSelected.map(
        _.toList ::: maybeSelectedClients.getOrElse(List.empty).toList)
      sorted = mergedWithPreselected.map(_.sortBy(_.name))
    } yield sorted

  def getTeamMembers(arn: Arn)(
      maybeSelectedTeamMembers: Option[Seq[TeamMember]] = None
  )(implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[Seq[TeamMember]]] =
    for {
      ugsUsers <- agentUserClientDetailsConnector.getTeamMembers(arn)
      ugsAsTeamMembers = ugsUsers.map(list =>
        list.map(TeamMember.fromUserDetails))
      ugsWithoutPreSelected = ugsAsTeamMembers.map(
        teamMembers =>
          teamMembers.filterNot(teamMember =>
            maybeSelectedTeamMembers.fold(false)(
              _.map(_.userId).contains(teamMember.userId))))
      mergedWithPreselected = ugsWithoutPreSelected
        .map(_.toList ::: maybeSelectedTeamMembers.getOrElse(List.empty).toList)
        .map(_.sortBy(_.name))
    } yield mergedWithPreselected

  // Compares users in group with users on ARN & fetches missing details (email & cred role)
  def getTeamMembersFromGroup(arn: Arn)(
    teamMembersInGroup: Option[Seq[TeamMember]] = None
  )(implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[Seq[TeamMember]]] =
    for {
      ugsUsers <- agentUserClientDetailsConnector.getTeamMembers(arn)
      ugsAsTeamMembers = ugsUsers.map(list =>
        list.map(TeamMember.fromUserDetails))
      groupTeamMembers = ugsAsTeamMembers.map(
        teamMembers =>
          teamMembers.filter(teamMember =>
            teamMembersInGroup.fold(true)(
              _.map(_.userId).contains(teamMember.userId))))
        .map(_.sortBy(_.name))
      groupTeamMembersSelected = groupTeamMembers.map(_.map(_.copy(selected = true))) // makes them selected
    } yield groupTeamMembersSelected


  /*
   * a) add the group members that are in the form that are not already in the session
   * b) remove from the session the group members that were de-selected
   *
   * */
  def addSelectablesToSession[T <: Selectable](formData: Option[List[T]])
                                              (sessionMembersDataKey: DataKey[Seq[T]],
                                         filteredMembersDataKey: DataKey[Seq[T]])
                                              (implicit hc: HeaderCarrier, request: Request[Any],
                                         ec: ExecutionContext, reads: Reads[Seq[T]], writes: Writes[Seq[T]]): Future[Unit] =
    for {
      inSession <- sessionCacheRepository.getFromSession[Seq[T]](sessionMembersDataKey).map(_.map(_.toList))

      filteredSelected <- sessionCacheRepository
        .getFromSession[Seq[T]](filteredMembersDataKey)
        .map(_.map(_.filter(_.selected == true).toList))

      deSelected = filteredSelected.orElse(inSession).map(_ diff formData.getOrElse(Nil))
      added = formData.map(_ diff filteredSelected.getOrElse(Nil))

      toSave = added.getOrElse(Nil) ::: inSession.getOrElse(Nil) diff deSelected.getOrElse(Nil)
      _ <- sessionCacheRepository.putSession[Seq[T]](sessionMembersDataKey, toSave.distinct)

    } yield ()

  def filterClients(arn: Arn)(formData: AddClientsToGroup)
                   (implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext)
  : Future[Option[Seq[DisplayClient]]] =
    for {
      clients <- getClients(arn).map(_.map(_.toVector))
      maybeTaxService = formData.filter
      resultByTaxService = maybeTaxService.fold(clients)(
        term =>
          if (term == "TRUST")
            clients.map(_.filter(_.taxService.contains("HMRC-TERS")))
          else clients.map(_.filter(_.taxService == term)))
      maybeTaxRefOrName = formData.search
      resultByName = maybeTaxRefOrName.fold(resultByTaxService)(
        term =>
          resultByTaxService.map(
            _.filter(_.name.toLowerCase.contains(term.toLowerCase))))
      resultByTaxRef = maybeTaxRefOrName.fold(resultByTaxService)(
        term =>
          resultByTaxService.map(
            _.filter(_.hmrcRef.toLowerCase.contains(term.toLowerCase))))
      consolidatedResult = resultByName
        .map(_ ++ resultByTaxRef.getOrElse(Vector.empty))
        .map(_.distinct)
      result = consolidatedResult.map(_.toVector)
      _ <- result match {
        case Some(filteredResult) => sessionCacheRepository.putSession(FILTERED_CLIENTS, filteredResult)
        case _ => Future.successful(())
      }
      hiddenClients = clients.map(
        _.filter(_.selected) diff result.getOrElse(Vector.empty)
          .filter(_.selected)
      )
      _ <- hiddenClients match {
        case Some(hidden) if hidden.nonEmpty => sessionCacheRepository.putSession(HIDDEN_CLIENTS_EXIST, true)
        case _ => Future.successful(())
      }
    } yield result

  def filterTeamMembers(arn: Arn)(
      formData: AddTeamMembersToGroup
  )(implicit hc: HeaderCarrier,
    request: Request[Any],
    ec: ExecutionContext): Future[Option[Seq[TeamMember]]] =
    for {
      selectedTeamMembers <- sessionCacheRepository.getFromSession(
        SELECTED_TEAM_MEMBERS)
      teamMembers <- getTeamMembers(arn)(selectedTeamMembers)
        .map(_.map(_.toVector))
      maybeNameOrEmail = formData.search
      resultByName = maybeNameOrEmail.fold(teamMembers)(
        term =>
          teamMembers.map(
            _.filter(_.name.toLowerCase.contains(term.toLowerCase))))
      resultByEmail = maybeNameOrEmail.fold(teamMembers)(
        term =>
          teamMembers.map(
            _.filter(_.email.toLowerCase.contains(term.toLowerCase))))
      consolidatedResult = resultByName
        .map(_ ++ resultByEmail.getOrElse(Vector.empty))
        .map(_.distinct)
      result = consolidatedResult.map(_.toVector)
      _ <- result match {
        case Some(filteredResult) => sessionCacheRepository.putSession(FILTERED_TEAM_MEMBERS, filteredResult)
        case _ => Future.successful(())
      }
      hiddenTeamMembers = teamMembers.map(
        _.filter(_.selected) diff result
          .map(_.filter(_.selected))
          .getOrElse(Vector.empty)
      )
      _ <- hiddenTeamMembers match {
        case Some(hidden) if hidden.nonEmpty => sessionCacheRepository.putSession(HIDDEN_TEAM_MEMBERS_EXIST, true)
        case _ => Future.successful(())
      }
    } yield result

  def saveSelectedOrFilteredClients[T <: Selectable](buttonPress: ButtonSelect)
                                   (arn: Arn)(formData: AddClientsToGroup)
                                  (implicit hc: HeaderCarrier, request: Request[Any],
                                   ec: ExecutionContext): Future[Unit] = {
    buttonPress match {
      case Clear =>
        for {
          _ <- addSelectablesToSession(
            formData.clients.map(_.map(_.copy(selected = true)))
          )(SELECTED_CLIENTS, FILTERED_CLIENTS)
          _ <- sessionCacheRepository.deleteFromSession(FILTERED_CLIENTS)
          _ <- sessionCacheRepository.deleteFromSession(HIDDEN_CLIENTS_EXIST)
        } yield ()

      case Continue =>
        for {
          _ <- addSelectablesToSession(
            formData.clients.map(_.map(_.copy(selected = true))))(
            SELECTED_CLIENTS,
            FILTERED_CLIENTS
          )
          _ <- sessionCacheRepository.deleteFromSession(FILTERED_CLIENTS)
          _ <- sessionCacheRepository.deleteFromSession(HIDDEN_CLIENTS_EXIST)
        } yield ()

      case Filter =>
        for {
          _ <- addSelectablesToSession(
            formData.clients.map(_.map(_.copy(selected = true)))
          )(SELECTED_CLIENTS, FILTERED_CLIENTS)
          _ <- filterClients(arn)(formData)
        } yield ()
    }
  }

  def saveSelectedOrFilteredTeamMembers(buttonPress: ButtonSelect)(arn: Arn)(
      formData: AddTeamMembersToGroup
  )(implicit hc: HeaderCarrier,
    request: Request[Any],
    ec: ExecutionContext): Future[Unit] =
    buttonPress match {
      case Clear =>
        for {
          _ <- addSelectablesToSession(
            formData.members.map(_.map(_.copy(selected = true))))(
            SELECTED_TEAM_MEMBERS,
            FILTERED_TEAM_MEMBERS
          )
          _ <- sessionCacheRepository.deleteFromSession(FILTERED_TEAM_MEMBERS)
          _ <- sessionCacheRepository.deleteFromSession(
            HIDDEN_TEAM_MEMBERS_EXIST)
        } yield ()

      case Continue =>
        for {
          _ <- addSelectablesToSession(
            formData.members.map(_.map(_.copy(selected = true))))(
            SELECTED_TEAM_MEMBERS,
            FILTERED_TEAM_MEMBERS
          )
          _ <- sessionCacheRepository.deleteFromSession(FILTERED_TEAM_MEMBERS)
          _ <- sessionCacheRepository.deleteFromSession(
            HIDDEN_TEAM_MEMBERS_EXIST)
        } yield ()

      case Filter =>
        for {
          _ <- addSelectablesToSession(
            formData.members.map(_.map(_.copy(selected = true))))(
            SELECTED_TEAM_MEMBERS,
            FILTERED_TEAM_MEMBERS
          )
          _ <- filterTeamMembers(arn)(formData)
        } yield ()
    }

}
