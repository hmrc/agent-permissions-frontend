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

package forms

import play.api.data.Form
import play.api.data.Forms.{single, text}

object FilterByGroupNameForm {

  val form: Form[String] =
    Form(
      single(
        "searchGroupByName" ->
          text
            .verifying("error.group.filter.required", _.trim.nonEmpty)
            .verifying("error.group.filter.max.length", _.trim.length < 32)
            .verifying("error.group.filter.invalid", x => !(x.contains('<') || x.contains('>')))
      )
    )

}
