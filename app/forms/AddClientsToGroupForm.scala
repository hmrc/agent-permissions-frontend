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
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json.{parse, toJson}
import uk.gov.voa.play.form.ConditionalMappings._

import java.util.Base64.{getDecoder, getEncoder}

object AddClientsToGroupForm {

  def form(buttonPressed: ButtonSelect = Continue): Form[AddClientsToGroup] = {
    buttonPressed match {
      case Continue =>
        Form(
          mapping(
            "hasSelectedClients" -> boolean,
            "search" -> optional(text),
            "filter" -> optional(text),
            "clients" -> mandatoryIfFalse("hasSelectedClients", list(text)
              .transform[List[DisplayClient]](
                _.map(str => parse(new String(getDecoder.decode(str.replaceAll("'", "")))).as[DisplayClient]),
                _.map(dc => getEncoder.encodeToString(toJson[DisplayClient](dc).toString().getBytes))
              ).verifying("error.select-clients.empty", _.nonEmpty)
            )
          )(AddClientsToGroup.apply)(AddClientsToGroup.unapply)
        )
      case Filter =>
        Form(
          mapping(
            "hasSelectedClients" -> boolean,
            "search" -> optional(text),
            "filter" -> optional(text),
            "clients" -> optional(list(text)).transform[Option[List[DisplayClient]]](
              _.map(strList => strList.map(str => parse(new String(getDecoder.decode(str.replaceAll("'", "")))).as[DisplayClient])),
              _.map(displayClientList => displayClientList.map(
                dc => getEncoder.encodeToString(toJson[DisplayClient](dc).toString().getBytes)
              ))
            )
          )(AddClientsToGroup.apply)(AddClientsToGroup.unapply)
            .verifying(error="error.search-filter.empty", x => x.filter.isDefined || x.search.isDefined)
        )
      case Clear =>
      Form(
        mapping(
          "hasSelectedClients" -> boolean,
          "search" -> optional(text),
          "filter" -> optional(text),
          "clients" -> optional(list(text)).transform[Option[List[DisplayClient]]](
            _.map(strList => strList.map(str => parse(new String(getDecoder.decode(str.replaceAll("'", "")))).as[DisplayClient])),
            _.map(displayClientList => displayClientList.map(
              dc => getEncoder.encodeToString(toJson[DisplayClient](dc).toString().getBytes)
            ))
          )
        )(AddClientsToGroup.apply)(AddClientsToGroup.unapply)
      )
      case e => throw new RuntimeException(s"invalid button $e")
    }
  }
}
