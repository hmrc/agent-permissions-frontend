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

import config.AppConfig
import play.api.i18n.Messages

object ViewUtils {

  def getFiltersByTaxService()(implicit mgs: Messages, appConfig: AppConfig): Seq[(String, String)] = {
    val filters = Seq(
      ("HMRC-MTD-IT", mgs("tax-service.mdt-it")),
      ("HMRC-MTD-VAT", mgs("tax-service.vat")),
      ("HMRC-CGT-PD", mgs("tax-service.cgt")),
      ("HMRC-PPT-ORG", mgs("tax-service.ppt")),
      ("TRUST", mgs("tax-service.trusts")) // TODO update to HMRC-TERS
    )

    if(appConfig.cbcEnabled) {
      filters ++ Seq(("HMRC-CBC", mgs("tax-service.cbc")))
    } else filters
  }

  def getFiltersTaxServiceListWithClientCount(data: Map[String, Int])
                                             (implicit mgs: Messages): Seq[(String, String)] = {
    data
      .map(entry => (entry._1, displayTaxServiceFromServiceKey(entry._1) + s" (${entry._2})" ))
      .toSeq
      .sortBy(x => displayTaxServiceFromServiceKey(x._1))
  }


  def displayTaxServiceFromServiceKey(serviceKey: String)(implicit mgs: Messages): String = {
    serviceKey match {
      case "HMRC-MTD-IT"     => mgs("tax-service.mdt-it")
      case "HMRC-MTD-VAT"    => mgs("tax-service.vat")
      case "HMRC-CGT-PD"     => mgs("tax-service.cgt")
      case "HMRC-PPT-ORG"    => mgs("tax-service.ppt")
      // TRUST not a service key but value for filter
      case s if s.contains("HMRC-TERS") || s == "TRUST" => mgs("tax-service.trusts")
      // We treat UK and NONUK variants as the same service
      case s if s.contains("HMRC-CBC") => mgs("tax-service.cbc")
      case s => throw new Exception(s"str: '$s' is not a value for a tax service")
    }
  }

  // can only display full taxId if no name
  def displayObfuscatedReference(name: String, taxId: String)(implicit msgs: Messages): String = {
    if(name.isEmpty) taxId else msgs("ending.in", taxId.substring(taxId.length - 4))
  }

  def clientCheckboxLabel(name: String, taxId: String, serviceKey: String)(implicit msgs: Messages):String = {
    if(name.isEmpty) {
      msgs("group.client.list.table.checkbox.label-missing-reference",
        taxId,
        displayTaxServiceFromServiceKey(serviceKey)
      )
    } else {
      msgs("group.client.list.table.checkbox.label",
        name,
        msgs("ending.in", taxId.substring(taxId.length - 4)),
        displayTaxServiceFromServiceKey(serviceKey)
      )
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

  /**
   * Given filter terms (or none), return a string like so:
   * (Some("mySearch"), Some("myFilter") => "for mySearch and myFilter"
   * (Some("mySearch"), None) => "for mySearch"
   * (None, None) => ""
   * This is in order to interpolate the string in the search results summary string, like:
   * "Displaying 10 results for mySearch and myFilter in this group"
   * or similar.
   *
   * This logic was requested as part of the APB-7104 content changes
   */
  def filterReminderSubstring(formSearch: Option[String], formFilter: Option[String])(implicit msgs: Messages): String = {
    val filterTerms = List(
      formSearch.filter(_.nonEmpty).toSeq,
      formFilter.filter(_.nonEmpty).map(displayTaxServiceFromServiceKey).toSeq
    ).flatten.filter(_.trim.nonEmpty).map(term => s"‘$term’")
    val `for` = msgs("paginated.clients.showing.total.filter-preposition")
    val and = msgs("paginated.clients.showing.total.filter-conjunction")
    filterTerms match {
      case Nil => ""
      case terms => (s"${`for`} " + filterTerms.mkString(s" $and ")).trim
    }
  }
}
