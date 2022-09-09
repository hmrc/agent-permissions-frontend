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

import models.ButtonSelect.{Clear, Continue, Filter}
import models.{AddTeamMembersToGroup, ButtonSelect, TeamMember}
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}

object AddTeamMembersToGroupForm {

  def form(buttonPressed: ButtonSelect = Continue): Form[AddTeamMembersToGroup] = buttonPressed match {
    case Continue =>
      Form(addTeamMembersToGroupMapping.verifying(emptyTeamMemberConstraint))
    case Filter =>
      Form(addTeamMembersToGroupMapping.verifying(emptySearchFieldConstraint))
    case Clear =>
      Form(addTeamMembersToGroupMapping)
    case e => throw new RuntimeException(s"invalid button $e")
  }

  private val emptyTeamMemberConstraint: Constraint[AddTeamMembersToGroup] =
    Constraint { formData: AddTeamMembersToGroup =>
      if (!formData.hasAlreadySelected && formData.members.isEmpty) {
        Invalid(ValidationError("error.select-members.empty"))
      } else Valid
    }

  private val emptySearchFieldConstraint: Constraint[AddTeamMembersToGroup] =
    Constraint { formData =>
      if (formData.search.isEmpty)
        Invalid(ValidationError("error.search-members.empty"))
      else Valid
    }

  private val addTeamMembersToGroupMapping = mapping(
    "hasAlreadySelected" -> boolean,
    "search" -> optional(text.verifying("error.search.teamMembers.invalid", s => !(s.contains('<') || s.contains('>')))),
    "members" -> optional(list(text))
  )(AddTeamMembersToGroup.apply)(AddTeamMembersToGroup.unapply)

}
