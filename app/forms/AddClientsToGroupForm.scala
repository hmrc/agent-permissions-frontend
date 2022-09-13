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
import models.{AddClientsToGroup, ButtonSelect}
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}

object AddClientsToGroupForm {

  def form(buttonPressed: ButtonSelect = Continue): Form[AddClientsToGroup] =
    buttonPressed match {
      case Continue =>
         Form(addClientsToGroupMapping.verifying(emptyClientConstraint))
      case Filter =>
        Form(
          addClientsToGroupFilterMapping
            .verifying(error = "error.search-filter.empty",
              form => form.filter.isDefined || form.search.isDefined)
        )
      case Clear =>
        Form(addClientsToGroupMapping)
      case e => throw new RuntimeException(s"invalid button $e")
    }

  private val emptyClientConstraint: Constraint[AddClientsToGroup] =
    Constraint { formData =>
      if (!formData.hasSelectedClients && formData.clients.isEmpty)
        Invalid(ValidationError("error.select-clients.empty"))
      else Valid
    }

  private val addClientsToGroupFilterMapping = mapping(
    "hasSelectedClients" -> boolean,
    "search" -> optional(text.verifying("error.search-filter.invalid", s => !(s.contains('<') || s.contains('>')))),
    "filter" -> optional(text),
    "clients" -> optional(list(text))
  )(AddClientsToGroup.apply)(AddClientsToGroup.unapply)

  private val addClientsToGroupMapping = mapping(
    "hasSelectedClients" -> boolean,
    "search" -> optional(text),
    "filter" -> optional(text),
    "clients" -> optional(list(text))
  )(AddClientsToGroup.apply)(AddClientsToGroup.unapply)

}
