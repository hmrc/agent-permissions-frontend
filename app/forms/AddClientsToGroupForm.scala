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

import models.DisplayClient
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.Json.{parse, toJson}
import uk.gov.voa.play.form.Condition
import uk.gov.voa.play.form.ConditionalMappings._

import java.util.Base64.{getDecoder, getEncoder}



case class AddClientsToGroup(hiddenClients: Boolean, search: Option[String], filter: Option[String], clients: Option[List[DisplayClient]])

object AddClientsToGroupForm {


  private def fieldIsEmpty(fieldName: String): Condition = _.get(fieldName).exists(v => v.isEmpty)

  def form(buttonPressed: String = "continue"): Form[AddClientsToGroup] = {
    buttonPressed match {
      case "continue" =>
        Form(
          mapping(
            "hiddenClients" -> boolean,
            "search" -> optional(text),
            "filter" -> optional(text),
            "clients" -> mandatoryIfFalse("hiddenClients", list(text)
              .transform[List[DisplayClient]](
                _.map(str => parse(new String(getDecoder.decode(str.replaceAll("'", "")))).as[DisplayClient]),
                _.map(dc => getEncoder.encodeToString(toJson[DisplayClient](dc).toString().getBytes))
              ).verifying("must select a client", _.nonEmpty)
            )
          )(AddClientsToGroup.apply)(AddClientsToGroup.unapply _)
        )
      case "filter" =>
        Form(
          mapping(
            "hiddenClients" -> boolean,
            "search" -> mandatoryIf(fieldIsEmpty("filter"), text.verifying("you must enter search", _.nonEmpty)),
            "filter" -> mandatoryIf(fieldIsEmpty("search"), text.verifying("enter a filter term", _.nonEmpty)),
            "clients" -> optional(list(text)).transform[Option[List[DisplayClient]]](
              maybeStrList => maybeStrList.map(strList => strList.map(str => parse(new String(getDecoder.decode(str.replaceAll("'", "")))).as[DisplayClient])),
              maybeDisplayClientList => maybeDisplayClientList.map(displayClientList => displayClientList.map(
                dc => getEncoder.encodeToString(toJson[DisplayClient](dc).toString().getBytes)
              ))
            )
          )(AddClientsToGroup.apply)(AddClientsToGroup.unapply _)
        )
      case "clear" =>
      Form(
        mapping(
          "hiddenClients" -> boolean,
          "search" -> optional(text),
          "filter" -> optional(text),
          "clients" -> optional(list(text)).transform[Option[List[DisplayClient]]](
            maybeStrList => maybeStrList.map(strList => strList.map(str => parse(new String(getDecoder.decode(str.replaceAll("'", "")))).as[DisplayClient])),
            maybeDisplayClientList => maybeDisplayClientList.map(displayClientList => displayClientList.map(
              dc => getEncoder.encodeToString(toJson[DisplayClient](dc).toString().getBytes)
            ))
          )
        )(AddClientsToGroup.apply)(AddClientsToGroup.unapply _)
      )
      case e => throw new RuntimeException(s"invalid button $e")
    }
  }
}
