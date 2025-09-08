/*
 * Copyright 2025 HM Revenue & Customs
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

package models

import play.api.libs.json.Json
import play.api.libs.json.OFormat
import models.Service.CapitalGains
import models.Service.Cbc
import models.Service.CbcNonUk
import models.Service.MtdIt
import models.Service.MtdItSupp
import models.Service.PersonalIncomeRecord
import models.Service.Pillar2
import models.Service.Ppt
import models.Service.Trust
import models.Service.TrustNT
import models.Service.Vat

case class SuspensionDetails(suspensionStatus: Boolean, regimes: Option[Set[String]]) {

  val suspendedRegimes: Set[String] =
    regimes.fold(Set.empty[String]) { rs =>
      if (rs.contains("ALL") || rs.contains("AGSV")) SuspensionDetails.validSuspensionRegimes
      else rs
    }

  override def toString: String = suspendedRegimes.toSeq.sorted.mkString(",")

}

object SuspensionDetails {

  lazy val serviceToRegime: Map[Service, String] = Map(
    MtdIt                -> "ITSA",
    MtdItSupp            -> "ITSA",
    Vat                  -> "VATC",
    Trust                -> "TRS",
    TrustNT              -> "TRS",
    CapitalGains         -> "CGT",
    PersonalIncomeRecord -> "PIR",
    Ppt                  -> "PPT",
    Cbc                  -> "CBC",
    CbcNonUk             -> "CBC",
    Pillar2              -> "PLR"
  )

  private val suspendableServices = Seq(MtdIt, Vat, Trust, CapitalGains, PersonalIncomeRecord, Ppt, Pillar2)

  lazy val validSuspensionRegimes: Set[String] =
    serviceToRegime.view.filterKeys(suspendableServices.contains(_)).values.toSet

  implicit val formats: OFormat[SuspensionDetails] = Json.format
}
