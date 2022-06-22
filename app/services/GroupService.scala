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
import controllers.{FILTERED_CLIENTS, GROUP_CLIENTS_SELECTED, HIDDEN_CLIENTS_EXIST}
import forms.AddClientsToGroup
import models.{ButtonSelect, DisplayClient, TeamMember}
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
                    (maybeTeamMembers: Option[Seq[TeamMember]] = None)
                    (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[TeamMember]]] = {
    for {
      es3Users              <- agentUserClientDetailsConnector.getTeamMembers(arn)
      es3AsTeamMembers      = es3Users.map(list => list.map(TeamMember.fromUserDetails(_)))
      es3WithoutPreSelected = es3AsTeamMembers.map(teamMembers =>
                                teamMembers.filterNot(teamMember =>
                                  maybeTeamMembers.fold(false)(_.map(_.userId).contains(teamMember.userId))
        )
      )
      mergedWithPreselected = es3WithoutPreSelected
                                .map(_.toList ::: maybeTeamMembers.getOrElse(List.empty).toList)
                                .map(_.sortBy(_.name))
    } yield mergedWithPreselected

  }



   def addClientsToGroup(formData: AddClientsToGroup)(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext) =

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

  def filterClients(arn: Arn)(formData: AddClientsToGroup)(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext): Future[Option[Seq[DisplayClient]]] = {

    for {
      clients             <- getClients(arn).map(_.map(_.toVector))
      maybeTaxService     = formData.filter
      resultByTaxService  = maybeTaxService.fold(clients)(term => clients.map(_.filter(_.taxService == term)))
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

  def processFormData(buttonPress: ButtonSelect)(arn: Arn)(formData: AddClientsToGroup)(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext) = {
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

}
