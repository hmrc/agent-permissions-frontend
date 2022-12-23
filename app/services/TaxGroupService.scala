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

import com.google.inject.ImplementedBy
import connectors.{AgentPermissionsConnector, CreateTaxServiceGroupRequest}
import play.api.Logging
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TaxGroupServiceImpl])
trait TaxGroupService {

  def createGroup(arn: Arn, createTaxServiceGroupRequest: CreateTaxServiceGroupRequest)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String]

}

@Singleton
class TaxGroupServiceImpl @Inject()
(agentPermissionsConnector: AgentPermissionsConnector) extends TaxGroupService with Logging {

  def createGroup(arn: Arn, createTaxServiceGroupRequest: CreateTaxServiceGroupRequest)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] = {
    agentPermissionsConnector.createTaxServiceGroup(arn)(createTaxServiceGroupRequest)
  }

}