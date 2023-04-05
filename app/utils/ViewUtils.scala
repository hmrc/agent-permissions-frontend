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

  def getFiltersTaxServiceListWithClientCount(data: Map[String, Int])
                                             (implicit mgs: Messages): Seq[(String, String)] ={
    data
      .map(entry => (entry._1, displayTaxServiceFromServiceKey(entry._1) + s" (${entry._2})" ))
      .toSeq
      .sortBy(x => displayTaxServiceFromServiceKey(x._1))
  }


  def displayTaxServiceFromServiceKey(serviceKey: String)(
      implicit mgs: Messages): String = {
    serviceKey match {
      case "HMRC-MTD-IT"     => mgs("tax-service.mdt-it")
      case "HMRC-MTD-VAT"    => mgs("tax-service.vat")
      case "HMRC-CGT-PD"     => mgs("tax-service.cgt")
      case "HMRC-PPT-ORG"    => mgs("tax-service.ppt")
      // TRUST not a service key but value for filter
      case s                 => if(s.contains("HMRC-TERS") || s == "TRUST") mgs("tax-service.trusts") else
        throw new Exception(s"str: '$s' is not a value for a tax service")
    }
  }

  // can only display full taxId if no name
  def displayObfuscatedReference(name: String, taxId: String): String = {
    if(name.isEmpty) {
      taxId
    } else {
      "*****".concat(taxId.substring(taxId.length - 4))
    }
  }

  // for hidden labels, name is preferred
  def displayNameOrFullReference(name: String, taxId: String): String = {
    if(name.isEmpty) {
      taxId
    } else {
      name
    }
  }

  // we want to translate - included Admin but it should be deprecated?
  def displayTeamMemberRole(role: String)(implicit mgs: Messages): String = {
    if(role == "User" || role == "Admin") {
      mgs("role.admin")
    } else {
      // role == Assistant
      mgs("role.standard")
    }
  }

  def withErrorPrefix(hasFormErrors: Boolean, str: String)(implicit mgs: Messages): String = {
    val errorPrefix = if(hasFormErrors) { mgs("error-prefix") + " "} else {""}
    errorPrefix.concat(mgs(str))
  }


  def withSearchPrefix( str: String,
                        formFilter: Option[String],
                        formSearch: Option[String]
                       )(implicit mgs: Messages): String = {

    // the form makes search/filter an option but an empty term is usually "" so .isDefined or .nonEmpty are both unhelpful here
    val hasOneInput = if(formFilter.getOrElse("") != "" && formSearch.getOrElse("") != "") {
      Some(false)
    } else {
      if(formFilter.getOrElse("") != "" || formSearch.getOrElse("") != "") {
        Some(true)
      } else {
        None
      }
    }

    val filterOrSearch = if (formFilter.getOrElse("") != "") {
      displayTaxServiceFromServiceKey(formFilter.get)
    } else {
      formSearch.getOrElse("")
    }

    if (hasOneInput.isDefined) {
      if (hasOneInput.get) {
        val prefix = mgs("common.results-for1", filterOrSearch)
        prefix.concat(" " + mgs(str))
      } else {
        val prefix = mgs("common.results-for2", formSearch.get, displayTaxServiceFromServiceKey(formFilter.get))
        prefix.concat(" " + mgs(str))
      }
    } else {
      mgs(str)
    }

  }

  // prioritises Error prefix (error state clears any filter)
  def withSearchAndErrorPrefix(hasFormErrors: Boolean,
                               str: String,
                               formFilter: Option[String],
                               formSearch: Option[String]
                              )(implicit mgs: Messages): String = {
    if (hasFormErrors) {
      withErrorPrefix(hasFormErrors, str)
    } else {
      withSearchPrefix(str, formFilter, formSearch)
    }

  }
}
