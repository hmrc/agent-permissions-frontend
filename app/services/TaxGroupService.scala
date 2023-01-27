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

import akka.Done
import com.google.inject.ImplementedBy
import connectors.{AgentPermissionsConnector, CreateTaxServiceGroupRequest, UpdateTaxServiceGroupRequest}
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, TaxGroup}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TaxGroupServiceImpl])
trait TaxGroupService {

  def getTaxGroupClientCount(arn: Arn)
                            (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]]


  def createGroup(arn: Arn, createTaxServiceGroupRequest: CreateTaxServiceGroupRequest)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String]

  def getGroup(groupId: String)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext) : Future[Option[TaxGroup]]

  def deleteGroup(groupId: String)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext) : Future[Done]

  def updateGroup(groupId: String, patchRequestBody: UpdateTaxServiceGroupRequest)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]
}

@Singleton
class TaxGroupServiceImpl @Inject()
(agentPermissionsConnector: AgentPermissionsConnector) extends TaxGroupService with Logging {

  def getTaxGroupClientCount(arn: Arn)
                            (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Int]] = {
    agentPermissionsConnector.getTaxGroupClientCount(arn)
  }

  def createGroup(arn: Arn, createTaxServiceGroupRequest: CreateTaxServiceGroupRequest)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] = {
    agentPermissionsConnector.createTaxServiceGroup(arn)(createTaxServiceGroupRequest)
  }

  def getGroup(groupId: String)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext) : Future[Option[TaxGroup]] = {
    agentPermissionsConnector.getTaxServiceGroup(groupId)
  }

  def deleteGroup(groupId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    agentPermissionsConnector.deleteTaxGroup(groupId)
  }

  def updateGroup(groupId: String, group: UpdateTaxServiceGroupRequest)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] =
    agentPermissionsConnector.updateTaxGroup(groupId, group)

}
