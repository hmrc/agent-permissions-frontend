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

package helpers

object Css {


  val legend = "legend.govuk-fieldset__legend"
  val ERROR_SUMMARY_TITLE = "#error-summary-title"
  val ERROR_SUMMARY_LINK = ".govuk-list.govuk-error-summary__list li a"
  val errorSummaryLinkWithHref  = (href: String)  => s".govuk-list.govuk-error-summary__list li a[href=$href]"
  val H1 = "main h1"
  val H2 = "main h2"
  val PRE_H1 = "main .govuk-caption-l"
  val paragraphs = "main p"
  val insetText = "div.govuk-inset-text"
  val form : String = s"main form[method=POST]"
  def errorSummaryForField(id: String): String ={s".govuk-error-summary__body li a[href=#${id}]"}
  def errorForField(id: String): String = s"p#${id}-error.govuk-error-message"
  def labelFor(id: String): String = s"label[for=${id}]"
  def radioButtonsField(id: String): String = s"form .govuk-radios#$id"
  def tableWithId(id: String) = s"table.govuk-table#$id"
  def tabPanelWithIdOf(id: String) = s".govuk-tabs__panel#$id"
  val submitButton = "main form button#continue[type=submit]"
  val linkStyledAsButton = "a.govuk-button"
  val currentLanguage = "ul.hmrc-language-select__list li.hmrc-language-select__list-item span[aria-current=true]";
  val alternateLanguage = ".hmrc-language-select__list .hmrc-language-select__list-item a.govuk-link";
  val checkYourAnswersListRows = "dl.govuk-summary-list .govuk-summary-list__row"
  val backLink = "a.govuk-back-link"
  val confirmationPanelH1 = ".govuk-panel--confirmation h1.govuk-panel__title"
  val confirmationPanelBody = ".govuk-panel--confirmation .govuk-panel__body"
}
