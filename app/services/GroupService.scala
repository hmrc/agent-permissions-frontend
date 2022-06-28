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
import controllers.{FILTERED_CLIENTS, FILTERED_TEAM_MEMBERS, GROUP_CLIENTS_SELECTED, GROUP_TEAM_MEMBERS_SELECTED, HIDDEN_CLIENTS_EXIST, HIDDEN_TEAM_MEMBERS_EXIST}
import models.{AddClientsToGroup, AddTeamMembersToGroup, ButtonSelect, DisplayClient, TeamMember}
import models.ButtonSelect._
import play.api.mvc.Request
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class GroupService @Inject()(agentUserClientDetailsConnector: AgentUserClientDetailsConnector,
                             sessionCacheRepository: SessionCacheRepository) {

  def getClients(arn: Arn)
                (implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[DisplayClient]]] = {
    for {
      es3Clients            <- agentUserClientDetailsConnector.getClients(arn)
      es3AsDisplayClients   = es3Clients.map(clientSeq => clientSeq.map(client => DisplayClient.fromClient(client)))
      maybeSelectedClients  <- sessionCacheRepository.getFromSession[Seq[DisplayClient]](GROUP_CLIENTS_SELECTED)
      es3WithoutPreSelected = es3AsDisplayClients.map(_.filterNot(dc => maybeSelectedClients.fold(false)(_.map(_.hmrcRef).contains(dc.hmrcRef))))
      mergedWithPreselected = es3WithoutPreSelected.map(_.toList ::: maybeSelectedClients.getOrElse(List.empty).toList)
      sorted                = mergedWithPreselected.map(_.sortBy(_.name))
    } yield sorted
  }

  def getTeamMembers(arn: Arn)
                    (maybeSelectedTeamMembers: Option[Seq[TeamMember]] = None)
                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[TeamMember]]] = {
    for {
      ugsUsers              <- agentUserClientDetailsConnector.getTeamMembers(arn)
      ugsAsTeamMembers      = ugsUsers.map(list => list.map(TeamMember.fromUserDetails))
      ugsWithoutPreSelected = ugsAsTeamMembers.map(teamMembers =>
                                teamMembers.filterNot(teamMember =>
                                  maybeSelectedTeamMembers.fold(false)(_.map(_.userId).contains(teamMember.userId))
        )
      )
      mergedWithPreselected = ugsWithoutPreSelected
                                .map(_.toList ::: maybeSelectedTeamMembers.getOrElse(List.empty).toList)
                                .map(_.sortBy(_.name))
    } yield mergedWithPreselected

  }



   def addClientsToGroup(formData: AddClientsToGroup)(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext): Future[Unit] =
    for {
      filteredResult      <- sessionCacheRepository.getFromSession(FILTERED_CLIENTS)
      oldSessionClients   <- sessionCacheRepository.getFromSession(GROUP_CLIENTS_SELECTED)
      formClients         = formData.clients.map(_.map(_.copy(selected = true))) //make them selected to store in session
      selectedFiltered    = filteredResult.map(_.filter(_.selected).toList)
      hiddenSelected      = oldSessionClients.map(old => old diff selectedFiltered.getOrElse(old)) // make this Nil if there was no filter applied
      formDiffHidden      = formClients.map(_ diff hiddenSelected.getOrElse(List.empty))
      newSessionClients   = formDiffHidden.map(_ ::: hiddenSelected.map(_.toList).getOrElse(List.empty)) //combine the hidden selected with the new one's in the form
      _                   = newSessionClients.map(clients => sessionCacheRepository.putSession(GROUP_CLIENTS_SELECTED, clients))
    } yield ()

  def addTeamMembersToGroup(formData: AddTeamMembersToGroup)(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext): Future[Unit] =
    for {
      filteredResult          <- sessionCacheRepository.getFromSession(FILTERED_TEAM_MEMBERS)
      oldSessionTeamMembers   <- sessionCacheRepository.getFromSession(GROUP_TEAM_MEMBERS_SELECTED)
      formTeamMembers         = formData.members.map(_.map(_.copy(selected = true))) //make them selected to store in session
      selectedFiltered        = filteredResult.map(_.filter(_.selected).toList)
      hiddenSelected          = oldSessionTeamMembers.map(old => old diff selectedFiltered.getOrElse(old)) // make this Nil if there was no filter applied
      formDiffHidden          = formTeamMembers.map(_ diff hiddenSelected.getOrElse(List.empty))
      newSessionMembers       = formDiffHidden.map(_ ::: hiddenSelected.map(_.toList).getOrElse(List.empty)) //combine the hidden selected with the new one's in the form
      _                       = newSessionMembers.map(members => sessionCacheRepository.putSession(GROUP_TEAM_MEMBERS_SELECTED, members))
    } yield ()

  def filterClients(arn: Arn)(formData: AddClientsToGroup)(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext): Future[Option[Seq[DisplayClient]]] = {

    for {
      clients             <- getClients(arn).map(_.map(_.toVector))
      maybeTaxService     = formData.filter
      resultByTaxService  = maybeTaxService.fold(clients)(term => if(term == "TRUST") clients.map(_.filter(_.taxService.contains("HMRC-TERS")))
      else clients.map(_.filter(_.taxService == term))
      )
      maybeTaxRefOrName   = formData.search
      resultByName        = maybeTaxRefOrName.fold(resultByTaxService)(term => resultByTaxService.map(_.filter(_.name.contains(term))))
      resultByTaxRef      = maybeTaxRefOrName.fold(resultByTaxService)(term => resultByTaxService.map(_.filter(_.hmrcRef.contains(term))))
      consolidatedResult  = resultByName.map(_ ++ resultByTaxRef.getOrElse(Vector.empty)).map(_.distinct)
      result              = consolidatedResult.map(_.toVector)
      _                   = result.map(filteredResult => sessionCacheRepository.putSession(FILTERED_CLIENTS, filteredResult))
      hiddenClients       = result.map(_.filter(_.selected) diff clients.map(_.filter(_.selected)).getOrElse(Vector.empty))
      _                   = hiddenClients.map(hidden => if(hidden.nonEmpty) sessionCacheRepository.putSession(HIDDEN_CLIENTS_EXIST, true))
    } yield result
  }

  def filterTeamMembers(arn: Arn)(formData: AddTeamMembersToGroup)(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext): Future[Option[Seq[TeamMember]]] = {
    for {
      selectedTeamMembers <- sessionCacheRepository.getFromSession(GROUP_TEAM_MEMBERS_SELECTED)
      teamMembers         <- getTeamMembers(arn)(selectedTeamMembers).map(_.map(_.toVector))
      maybeNameOrEmail    = formData.search
      resultByName        = maybeNameOrEmail.fold(teamMembers)(term => teamMembers.map(_.filter(_.name.toLowerCase.contains(term.toLowerCase))))
      resultByEmail       = maybeNameOrEmail.fold(teamMembers)(term => teamMembers.map(_.filter(_.email.toLowerCase.contains(term.toLowerCase))))
      consolidatedResult  = resultByName.map(_ ++ resultByEmail.getOrElse(Vector.empty)).map(_.distinct)
      result              = consolidatedResult.map(_.toVector)
      _                   = result.map(filteredResult => sessionCacheRepository.putSession(FILTERED_TEAM_MEMBERS, filteredResult))
      hiddenTeamMembers   = result.map(_.filter(_.selected) diff teamMembers.map(_.filter(_.selected)).getOrElse(Vector.empty))
      _                   = hiddenTeamMembers.map(hidden => if(hidden.nonEmpty) sessionCacheRepository.putSession(HIDDEN_TEAM_MEMBERS_EXIST, true))
    } yield result
  }

  def processFormDataForClients(buttonPress: ButtonSelect)(arn: Arn)(formData: AddClientsToGroup)(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext): Future[Unit] = {
    buttonPress match {
      case Clear  =>
        for {
        _                 <- addClientsToGroup(formData)
        _                 <- sessionCacheRepository.deleteFromSession(FILTERED_CLIENTS)
        _                 <- sessionCacheRepository.deleteFromSession(HIDDEN_CLIENTS_EXIST)
      } yield ()

      case Continue =>
        for {
          _               <- addClientsToGroup(formData)
          _               <- sessionCacheRepository.deleteFromSession(FILTERED_CLIENTS)
          _               <- sessionCacheRepository.deleteFromSession(HIDDEN_CLIENTS_EXIST)
        } yield ()

      case Filter   =>
        for {
          _               <- addClientsToGroup(formData)
          _               <- filterClients(arn)(formData)
        } yield ()
    }
  }

  def processFormDataForTeamMembers(buttonPress: ButtonSelect)(arn: Arn)(formData: AddTeamMembersToGroup)(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext): Future[Unit] = {
    buttonPress match {
      case Clear  =>
        for {
          _                 <- addTeamMembersToGroup(formData)
          _                 <- sessionCacheRepository.deleteFromSession(FILTERED_TEAM_MEMBERS)
          _                 <- sessionCacheRepository.deleteFromSession(HIDDEN_TEAM_MEMBERS_EXIST)
        } yield ()

      case Continue =>
        for {
          _               <- addTeamMembersToGroup(formData)
          _               <- sessionCacheRepository.deleteFromSession(FILTERED_TEAM_MEMBERS)
          _               <- sessionCacheRepository.deleteFromSession(HIDDEN_TEAM_MEMBERS_EXIST)
        } yield ()

      case Filter   =>
        for {
          _               <- addTeamMembersToGroup(formData)
          _               <- filterTeamMembers(arn)(formData)
        } yield ()
    }
  }

}
