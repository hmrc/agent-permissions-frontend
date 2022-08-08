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

package utils

import play.api.i18n.Messages

object ViewUtils {

  def getFiltersByTaxService()(implicit mgs: Messages): Seq[(String, String)] =
    Seq(
      ("HMRC-MTD-IT", mgs("tax-service.mdt-it")),
      ("HMRC-MTD-VAT", mgs("tax-service.vat")),
      ("HMRC-CGT-PD", mgs("tax-service.cgt")),
      ("HMRC-PPT-ORG", mgs("tax-service.ppt")),
      ("TRUST", mgs("tax-service.trusts"))
    )

  def displayTaxServiceFromServiceKey(serviceKey: String)(
      implicit mgs: Messages): String = {
    serviceKey match {
      case "HMRC-MTD-IT"     => mgs("tax-service.mdt-it")
      case "HMRC-MTD-VAT"    => mgs("tax-service.vat")
      case "HMRC-CGT-PD"     => mgs("tax-service.cgt")
      case "HMRC-PPT-ORG"    => mgs("tax-service.ppt")
      case "HMRC-TERS-ORG"   => mgs("tax-service.trusts")
      case "HMRC-TERSNT-ORG" => mgs("tax-service.trusts")
      case s                 => throw new Exception(s"$s is not a service key")
    }
  }

  // can only display full taxId if no name
  def displayObfuscatedReference(name: String, taxId: String): String = {
    if(name.isEmpty) {
      taxId
    } else {
      // TODO - change obfuscation depending on reference?
      "ending in ".concat(taxId.substring(taxId.length - 4))
    }
  }

}
