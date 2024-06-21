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

// TODO rename? also used for team members
object AddGroupsToClientForm {
  val NoneValue: String = "__none__"
  /* A valid form will contain ONE of the following:
    - a list of groups excluding the special 'none' value OR
    - a list of groups containing ONLY the special 'none' value
   */

  def form(): Form[List[String]] =
    Form(
      single(
        "groups" -> list(text)
          // empty error
          .verifying("error.select.groups.empty", groups => groups.nonEmpty)
          // Selected 'none of the above' AND one or more groups (can happen with JS disabled)
          .verifying(
            "unassigned.client.assign.invalid-selection.error",
            groups => if (groups.contains(NoneValue)) groups.length == 1 else true
          )
      )
    )
}
