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
import models.{DisplayClient, TeamMember}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}


@Singleton
class GroupService @Inject()(agentUserClientDetailsConnector: AgentUserClientDetailsConnector) {

  def getClients(arn: Arn)(maybeSelectedClients: Option[Seq[DisplayClient]] = None)
                (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[DisplayClient]]] = {
    for {
    es3Clients                    <- agentUserClientDetailsConnector.getClients(arn)
    es3AsDisplayClients            = es3Clients.map(clientSeq => clientSeq.map(client => DisplayClient.fromClient(client)))
    es3WithoutPreSelected          = es3AsDisplayClients.map(_.filterNot(dc => maybeSelectedClients.fold(false)(_.map(_.hmrcRef).contains(dc.hmrcRef))))
    mergedWithPreselected          = es3WithoutPreSelected.map( _.toList ::: maybeSelectedClients.getOrElse(List.empty).toList)
    sorted                         = mergedWithPreselected.map(_.sortBy(_.name))
    } yield sorted
  }

  def getTeamMembers(arn: Arn)(maybeTeamMembers: Option[Seq[TeamMember]] = None)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[TeamMember]]] = {

    val selectedOnes = maybeTeamMembers.getOrElse(Seq.empty)
    val members = (1 to 30)
      .map(i => TeamMember(s"Name $i", s"bob$i@accounting.com",
        !selectedOnes.filter(_.name.equals(s"Name $i")).isEmpty))
    Future.successful(Some(members))
  }
}
