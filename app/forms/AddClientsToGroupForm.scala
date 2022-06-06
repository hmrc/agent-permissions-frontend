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
import play.api.libs.json.Json

import java.util.Base64


object AddClientsToGroupForm {

  def form(): Form[List[DisplayClient]] = {
    Form(
      single(
        "clients" -> list(text).transform[List[DisplayClient]](_.map(str => {
          Json.parse(new String(Base64.getDecoder.decode(str.replaceAll("'", "")))).as[DisplayClient]}),
          _.map(dc =>
            Base64.getEncoder.encodeToString(Json.toJson[DisplayClient](dc).toString().getBytes))
        )
          .verifying("error.client.list.empty", _.nonEmpty)
      )
    )
  }
}
