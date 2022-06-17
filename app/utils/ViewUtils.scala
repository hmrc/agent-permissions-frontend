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

  def getFiltersByTaxService()(implicit mgs: Messages): Seq[(String, String)] = Seq(
    ("mdt-it", mgs("tax-service.mdt-it")),
    ("vat", mgs("tax-service.vat")),
    ("cgt", mgs("tax-service.cgt")),
    ("ppt", mgs("tax-service.ppt")),
    ("trusts", mgs("tax-service.trusts"))
  )


}
