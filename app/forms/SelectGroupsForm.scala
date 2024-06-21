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
import play.api.data.Forms.{boolean, default, list, optional, text, tuple}
import play.api.data.validation.{Constraint, Invalid, Valid}

sealed trait SelectGroups

object SelectGroups {
  case class SelectedGroups(groups: List[String]) extends SelectGroups
  case object NoneOfTheAbove extends SelectGroups
  case object CreateNew extends SelectGroups
}

object SelectGroupsForm {
  val NoneValue: String = "__none__"
  /* A valid form will contain ONE of the following:
    - the single "createNew" boolean set to true, OR
    - a list of groups excluding the special 'none' value OR
    - a list of groups containing ONLY the special 'none' value
   */
  def form(): Form[SelectGroups] = Form(
    tuple[List[String], Option[Boolean]](
      "groups"    -> default(list(text), List.empty[String]),
      "createNew" -> optional(boolean)
    ).verifying(
      Constraint((tpl: (List[String], Option[Boolean])) =>
        tpl match {
          // Nothing selected
          case (Nil, None | Some(false)) => Invalid("unassigned.client.assign.invalid-selection.error")
          // Selected 'none of the above' AND one or more groups (can happen with JS disabled)
          case (groups, None | Some(false)) if groups.contains(NoneValue) && groups.length > 1 =>
            Invalid("unassigned.client.assign.invalid-selection.error")
          // Selected groups AND 'create a new group' (cannot really happen unless the user manipulates the HTML!)
          case (groups, Some(true)) if groups.nonEmpty => Invalid("unassigned.client.assign.existing.or.new.error")
          case _                                       => Valid
        }
      )
    ).transform[SelectGroups](
      {
        case (Nil, Some(true))                                           => SelectGroups.CreateNew
        case (List(NoneValue), None | Some(false))                       => SelectGroups.NoneOfTheAbove
        case (groups, None | Some(false)) if !groups.contains(NoneValue) => SelectGroups.SelectedGroups(groups)
        case _ =>
          throw new RuntimeException("SelectGroupsForm") /* should never happen, thanks to the validation above */
      },
      {
        case SelectGroups.CreateNew              => (Nil, Some(true))
        case SelectGroups.NoneOfTheAbove         => (List(NoneValue), None)
        case SelectGroups.SelectedGroups(groups) => (groups, None)
      }
    )
  )

}
