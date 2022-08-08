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
import uk.gov.hmrc.agentmtdidentifiers.model.AccessGroup

case class DisplayGroup(
    _id: String,
    name: String,
    clients: Seq[DisplayClient] = Seq.empty,
    teamMembers: Seq[TeamMember] = Seq.empty
)

object DisplayGroup {
  implicit val format = Json.format[DisplayGroup]

  def fromAccessGroup(accessGroup: AccessGroup) : DisplayGroup = {
     DisplayGroup(
       _id = accessGroup._id.toString,
       name = accessGroup.groupName,
      clients =  DisplayClient.fromEnrolments(accessGroup.clients)
     )
  }
}
