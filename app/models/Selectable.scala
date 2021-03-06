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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Client, Enrolment, Identifier, UserDetails}

sealed trait Selectable {
  val selected: Boolean
}

case class TeamMember(
                       name: String,
                       email: String,
                       userId: Option[String] = None,
                       credentialRole: Option[String] = None,
                       selected: Boolean = false
                     ) extends Selectable

case object TeamMember {
  implicit val format = Json.format[TeamMember]

  def fromUserDetails(user: UserDetails): TeamMember =
    TeamMember(
      name = user.name.getOrElse(""),
      email = user.email.getOrElse(""),
      userId = user.userId,
      credentialRole = user.credentialRole
    )

  def toAgentUser(tm: TeamMember): AgentUser =
    AgentUser(tm.userId.get, tm.name)
}

case class DisplayClient(
                          hmrcRef: String,
                          name: String,
                          taxService: String,
                          identifierKey: String,
                          selected: Boolean = false
                        ) extends Selectable

case object DisplayClient {

  implicit val format = Json.format[DisplayClient]

  def fromEnrolments(clients: Option[Set[Enrolment]]): Seq[DisplayClient] =
    clients.map { maybeEnrolments: Set[Enrolment] =>
      maybeEnrolments.toSeq
        .map(Client.fromEnrolment)
        .map(DisplayClient.fromClient(_, selected = true))
    }.getOrElse(Seq.empty[DisplayClient])


  def fromClient(client: Client, selected: Boolean = false): DisplayClient = {
    val keyElements = client.enrolmentKey.split('~')
    val taxService = keyElements.head
    val identifierKey = keyElements(1)
    val hmrcRef = keyElements.last

    DisplayClient(hmrcRef,
      client.friendlyName,
      taxService,
      identifierKey,
      selected)
  }

  def toEnrolment(dc: DisplayClient): Enrolment =
    Enrolment(dc.taxService,
      "Activated",
      dc.name,
      Seq(Identifier(dc.identifierKey, dc.hmrcRef)))
}
