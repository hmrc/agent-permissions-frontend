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

package forms

import models.ConfirmGroup
import play.api.data.Form
import play.api.data.Forms._

object ConfirmCreateGroupForm {

  val groupNameRegex = "^[!%*^()_+\\-={}:;@~#,.?\\[\\]/A-Za-z0-9 ]$"

  def form(errorMessageKey: String): Form[ConfirmGroup] = Form(
    mapping(
      "name" ->
        text
          .verifying("group.name.required", s => s.trim.nonEmpty)
          .verifying("group.name.max.length", s => s.trim.length < 32)
          .verifying("group.name.invalid", _.matches(groupNameRegex)),
      "answer" -> optional(boolean)
        .verifying(errorMessageKey, _.isDefined)
//        .transform(_.get, (b: Boolean) => Option(b))
    )(ConfirmGroup.apply)(ConfirmGroup.unapply)
  )

}
