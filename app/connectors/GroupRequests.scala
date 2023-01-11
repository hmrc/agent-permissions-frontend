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

package connectors

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.agentmtdidentifiers.model.{AgentUser, Client}

case class GroupRequest(groupName: String,
                        teamMembers: Option[Seq[AgentUser]],
                        clients: Option[Seq[Client]])

case object GroupRequest {
  implicit val formatCreateAccessGroupRequest: OFormat[GroupRequest] =
    Json.format[GroupRequest]
}

case class UpdateAccessGroupRequest(
                                     groupName: Option[String] = None,
                                     teamMembers: Option[Set[AgentUser]] = None,
                                     clients: Option[Set[Client]] = None
                                   )

object UpdateAccessGroupRequest {
  implicit val format: OFormat[UpdateAccessGroupRequest] =
    Json.format[UpdateAccessGroupRequest]
}

case class AddMembersToAccessGroupRequest(
                                           teamMembers: Option[Set[AgentUser]] = None,
                                           clients: Option[Set[Client]] = None
                                         )

object AddMembersToAccessGroupRequest {
  implicit val format: OFormat[AddMembersToAccessGroupRequest] = Json.format[AddMembersToAccessGroupRequest]
}

case class UpdateTaxServiceGroupRequest(
                                         groupName: Option[String] = None,
                                         teamMembers: Option[Set[AgentUser]] = None,
                                         autoUpdate: Option[Boolean] = None,
                                         excludedClients: Option[Set[Client]] = None
                                       )

object UpdateTaxServiceGroupRequest {
  implicit val format: OFormat[UpdateTaxServiceGroupRequest] = Json.format[UpdateTaxServiceGroupRequest]
}
