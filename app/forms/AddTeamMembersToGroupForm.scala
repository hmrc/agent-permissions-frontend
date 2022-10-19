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

import controllers.CONTINUE_BUTTON
import models.AddTeamMembersToGroup
import play.api.data.Form
import play.api.data.Forms._

object AddTeamMembersToGroupForm {

  def form(): Form[AddTeamMembersToGroup] = Form(
    mapping(
    "hasAlreadySelected" -> boolean,
    "search" -> optional(text.verifying("error.search-members.invalid", s => !(s.contains('<') || s.contains('>')))),
    "members" -> optional(list(text)),
    "submit" -> text
  )(AddTeamMembersToGroup.apply)(AddTeamMembersToGroup.unapply)
      .verifying("error.select-members.empty", data => {
        data.submit != CONTINUE_BUTTON ||
          (data.submit == CONTINUE_BUTTON && data.members.getOrElse(Seq.empty).nonEmpty)
      })
  )
}
