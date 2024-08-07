/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.agents.accessgroups.{AgentUser, Client, UserDetails}
import utils.EncryptionUtil

import scala.util.hashing.MurmurHash3

sealed trait Selectable {
  val selected: Boolean
  val id: String
}

case class TeamMember(
  name: String,
  email: String,
  userId: Option[String] = None,
  credentialRole: Option[String] = None,
  selected: Boolean = false,
  alreadyInGroup: Boolean = false
) extends Selectable {
  private val hashKey = s"$name$email${userId.getOrElse(throw new RuntimeException("userId missing from TeamMember"))}"
  val id: String = MurmurHash3.stringHash(hashKey).toString

}

case object TeamMember {
  implicit val format: OFormat[TeamMember] = Json.format[TeamMember]

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

// There's a copy of this in agent-permissions BE
case class DisplayClient(
  hmrcRef: String,
  name: String,
  taxService: String,
  enrolmentKeyExtra: String, // not used for display - very hacky!!
  selected: Boolean = false,
  alreadyInGroup: Boolean = false
) extends Selectable {
  // TODO problematic assumption about where the 'key' identifier (hmrcRef) is in an enrolmentKey
  val enrolmentKey: String = if (taxService == "HMRC-CBC-ORG") {
    s"$taxService~cbcId~$hmrcRef~$enrolmentKeyExtra"
  } else { s"$taxService~$enrolmentKeyExtra~$hmrcRef" }
  val id: String = EncryptionUtil.encryptEnrolmentKey(enrolmentKey)
}

// There's a copy of this in agent-permissions BE
case object DisplayClient {

  implicit val format: OFormat[DisplayClient] = Json.format[DisplayClient]

  // TODO problematic assumption about where the 'key' identifier (hmrcRef) is in an enrolmentKey
  def fromClient(client: Client, selected: Boolean = false): DisplayClient = {
    val keyElements = client.enrolmentKey.split('~')
    val taxService = keyElements.head
    // very hacky!!
    val enrolmentKeyExtra = if (keyElements.head.contains("HMRC-CBC-ORG")) {
      s"${keyElements(3)}~${keyElements(4)}" // saves the UTR for later
    } else keyElements(1)
    val hmrcRef = if (keyElements.head.contains("HMRC-CBC-ORG")) {
      keyElements(2) // cbcId not UTR
    } else keyElements.last

    DisplayClient(
      hmrcRef,
      Option(client.friendlyName).getOrElse(""), // to avoid null pointers and avoid getOrElse constantly !!
      taxService,
      enrolmentKeyExtra,
      selected
    )
  }

  def toClient(dc: DisplayClient): Client = Client(dc.enrolmentKey, dc.name)
}
