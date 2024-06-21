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
import play.api.data.Forms._

object GroupNameForm {

  val groupNameRegex = "^[!%*^()_+\\-={}:;@~#,.?\\[\\]A-Za-z0-9 ]{0,}$"

  def form(): Form[String] =
    Form(
      single(
        "name" ->
          text
            .verifying("group.name.required", _.trim.nonEmpty)
            .verifying("group.name.max.length", _.trim.length < 50)
            .verifying("group.name.invalid", _.matches(groupNameRegex))
      )
    )

}
