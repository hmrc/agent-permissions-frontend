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

package helpers

import akka.Done
import connectors.CreateTaxServiceGroupRequest
import org.scalamock.scalatest.MockFactory
import services.TaxGroupService
import uk.gov.hmrc.agentmtdidentifiers.model.{AccessGroup, Arn}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait TaxGroupServiceMocks extends MockFactory {

  def expectGetTaxGroupClientCount(arn: Arn)
                                             (numberOfEachService: List[Int])
                                             (implicit taxGroupService: TaxGroupService): Unit = {
    val data : Map[String, Int] = Map(
      "HMRC-MTD-IT" -> numberOfEachService.head,
      "HMRC-MTD-VAT" -> numberOfEachService(1),
      "HMRC-CGT-PD" -> numberOfEachService(2),
      "HMRC-PPT-ORG" -> numberOfEachService(3),
      "HMRC-TERS" -> numberOfEachService(4)
    )
    (taxGroupService
      .getTaxGroupClientCount(_: Arn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *)
      .returning(Future successful data).once()
  }

  def expectCreateTaxGroup(arn: Arn)
                       (implicit taxGroupService: TaxGroupService): Unit =
    (taxGroupService
      .createGroup(_: Arn, _: CreateTaxServiceGroupRequest)(_: HeaderCarrier, _: ExecutionContext))
      .expects(arn, *, *, *)
      .returning(Future.successful("PPT or whatever"))
      .once()

  def expectDeleteTaxGroup(id: String)
                       (implicit taxGroupService: TaxGroupService): Unit =
    (taxGroupService
      .deleteGroup(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(id, *, *)
      .returning(Future.successful(Done)).once()

  def expectGetTaxGroupById(id: String, maybeGroup: Option[AccessGroup])(
    implicit taxGroupService: TaxGroupService): Unit =
    (taxGroupService
      .getGroup(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(id, *, *)
      .returning(Future successful maybeGroup)

}
