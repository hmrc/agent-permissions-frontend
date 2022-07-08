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
import models.{AddClientsToGroup, ButtonSelect, DisplayClient}
import play.api.data.{Form, FormError}
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.libs.json.Json.{parse, toJson}

import java.util.Base64.{getDecoder, getEncoder}

object AddClientsToGroupForm {

  def form(buttonPressed: ButtonSelect = Continue): Form[AddClientsToGroup] =
    buttonPressed match {
      case Continue =>
        val formWithMaybeGlobalError = Form(
          addClientsToGroupMapping
            .verifying(emptyClientConstraint)
        )
        if (formWithMaybeGlobalError.hasErrors)
          formWithMaybeGlobalError.copy(errors = Seq(FormError("clients", "error.select-clients.empty")))
        else formWithMaybeGlobalError
      case Filter =>
        Form(
          addClientsToGroupMapping
            .verifying(error = "error.search-filter.empty", x => x.filter.isDefined || x.search.isDefined)
        )
      case Clear =>
        Form(
          addClientsToGroupMapping
        )
      case e => throw new RuntimeException(s"invalid button $e")
    }

  private val emptyClientConstraint: Constraint[AddClientsToGroup] =
    Constraint { formData =>
      if (!formData.hasSelectedClients && formData.clients.isEmpty)
        Invalid(ValidationError("error.select-clients.empty"))
      else Valid
    }

  private val addClientsToGroupMapping = mapping(
    "hasSelectedClients" -> boolean,
    "search"             -> optional(text),
    "filter"             -> optional(text),
    "clients" -> optional(list(text))
      .transform[Option[List[DisplayClient]]](
        _.map(_.map { str =>
          parse(new String(getDecoder.decode(str.replaceAll("'", ""))))
            .as[DisplayClient]
        }),
        _.map(_.map(dc => getEncoder.encodeToString(toJson[DisplayClient](dc).toString().getBytes)))
      )
  )(AddClientsToGroup.apply)(AddClientsToGroup.unapply)
}
