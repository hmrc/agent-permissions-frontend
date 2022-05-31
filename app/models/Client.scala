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

package models

import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.Client


case class DisplayClient(hmrcRef: String, name: String, taxService: String)


object DisplayClient{
  implicit val format = Json.format[DisplayClient]

  def fromClient(client: Client): DisplayClient = {
    val keyElements = client.enrolmentKey.split('~')
    val hmrcRef = keyElements.last
    val taxService = keyElements.head
    DisplayClient(hmrcRef, client.friendlyName, taxService)
  }
}
