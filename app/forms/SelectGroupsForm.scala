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
import play.api.data.Forms.{boolean, list, mapping, optional, text}

case class SelectGroups(groups: Option[List[String]], createNew: Option[Boolean] )

object SelectGroupsForm {

  def form(): Form[SelectGroups] = Form(
      mapping(
        "groups" -> optional(list(text)),
        "createNew" -> optional(boolean)
      )(SelectGroups.apply)(SelectGroups.unapply)
        .verifying("unassigned.client.assign.existing.or.new.error", selectGroups => {
        !(selectGroups.groups.isDefined && selectGroups.createNew.isDefined)
      }).verifying("unassigned.client.assign.nothing.selected.error", selectGroups => {
        !(selectGroups.groups.isEmpty && selectGroups.createNew.isEmpty)
      })
  )
}
