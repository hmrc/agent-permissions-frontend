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
import play.api.libs.json.Json.{parse, toJson}
import uk.gov.voa.play.form.ConditionalMappings.mandatoryIfFalse

import java.util.Base64.{getDecoder, getEncoder}


object AddTeamMembersToGroupForm {

  def form(buttonPressed: ButtonSelect = Continue): Form[AddTeamMembersToGroup] = {
    buttonPressed match {
      case Continue =>
        Form(
          mapping(
            "hasAlreadySelected" -> boolean,
            "search" -> optional(text),
            "members" -> mandatoryIfFalse("hasAlreadySelected", list(text)
              .transform[List[TeamMember]](
                _.map(str => parse(new String(getDecoder.decode(str.replaceAll("'", "")))).as[TeamMember]),
                _.map(dc => getEncoder.encodeToString(toJson[TeamMember](dc).toString().getBytes))
              ).verifying("error.select-members.empty", _.nonEmpty)
            )
          )(AddTeamMembersToGroup.apply)(AddTeamMembersToGroup.unapply)
        )
      case Filter =>
        Form(
          mapping(
            "hasAlreadySelected" -> boolean,
            "search" -> optional(text).verifying("error.search-members.empty", _.nonEmpty),
            "members" -> optional(list(text)).transform[Option[List[TeamMember]]](
              _.map(strList => strList.map(str => parse(new String(getDecoder.decode(str.replaceAll("'", "")))).as[TeamMember])),
              _.map(TeamMemberList => TeamMemberList.map(
                dc => getEncoder.encodeToString(toJson[TeamMember](dc).toString().getBytes)
              ))
            )
          )(AddTeamMembersToGroup.apply)(AddTeamMembersToGroup.unapply)
        )
      case Clear =>
        Form(
          mapping(
            "hasAlreadySelected" -> boolean,
            "search" -> optional(text),
            "members" -> optional(list(text)).transform[Option[List[TeamMember]]](
              _.map(strList => strList.map(str => parse(new String(getDecoder.decode(str.replaceAll("'", "")))).as[TeamMember])),
              _.map(TeamMemberList => TeamMemberList.map(
                dc => getEncoder.encodeToString(toJson[TeamMember](dc).toString().getBytes)
              ))
            )
          )(AddTeamMembersToGroup.apply)(AddTeamMembersToGroup.unapply)
        )
      case e => throw new RuntimeException(s"invalid button $e")
    }
  }

}
