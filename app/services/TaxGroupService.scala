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
import connectors._
import models.GroupId
import org.apache.pekko.Done
import play.api.Logging
import models.Arn
import models.accessgroups.TaxGroup
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TaxGroupServiceImpl])
trait TaxGroupService {
  def addOneMemberToGroup(id: GroupId, groupRequest: AddOneTeamMemberToGroupRequest)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Done]

  def addMembersToGroup(id: GroupId, groupRequest: AddMembersToTaxServiceGroupRequest)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Done]

  def getTaxGroupClientCount(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]]

  def createGroup(arn: Arn, createTaxServiceGroupRequest: CreateTaxServiceGroupRequest)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[String]

  def getGroup(groupId: GroupId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TaxGroup]]

  def deleteGroup(groupId: GroupId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]

  def updateGroup(groupId: GroupId, patchRequestBody: UpdateTaxServiceGroupRequest)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Done]
}

@Singleton
class TaxGroupServiceImpl @Inject() (agentPermissionsConnector: AgentPermissionsConnector)
    extends TaxGroupService with Logging {

  def getTaxGroupClientCount(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]] =
    agentPermissionsConnector.getTaxGroupClientCount(arn)

  def createGroup(arn: Arn, createTaxServiceGroupRequest: CreateTaxServiceGroupRequest)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[String] =
    agentPermissionsConnector.createTaxServiceGroup(arn)(createTaxServiceGroupRequest)

  def getGroup(groupId: GroupId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[TaxGroup]] =
    agentPermissionsConnector.getTaxServiceGroup(groupId)

  def deleteGroup(groupId: GroupId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] =
    agentPermissionsConnector.deleteTaxGroup(groupId)

  def updateGroup(groupId: GroupId, group: UpdateTaxServiceGroupRequest)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Done] =
    agentPermissionsConnector.updateTaxGroup(groupId, group)

  def addOneMemberToGroup(id: GroupId, groupRequest: AddOneTeamMemberToGroupRequest)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Done] =
    agentPermissionsConnector.addOneTeamMemberToTaxGroup(id, groupRequest)

  def addMembersToGroup(id: GroupId, groupRequest: AddMembersToTaxServiceGroupRequest)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Done] =
    agentPermissionsConnector.addMembersToTaxGroup(id, groupRequest)

}
