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

import models.{DisplayClient, TeamMember}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.Future

package object controllers {

  implicit class ToFuture[T](t: T) {
    def toFuture: Future[T] = Future successful t
  }

  val isEligibleToOptIn: OptinStatus => Boolean = status =>
    status == OptedOutEligible
  val isOptedIn: OptinStatus => Boolean = status =>
    status == OptedInReady || status == OptedInNotReady || status == OptedInSingleUser
  val isOptedOut: OptinStatus => Boolean = status =>
    status == OptedOutEligible || status == OptedOutSingleUser || status == OptedOutWrongClientCount
  val isOptedInComplete: OptinStatus => Boolean = status =>
    status == OptedInReady

  val OPTIN_STATUS: DataKey[OptinStatus] = DataKey("optinStatus")
  val GROUP_NAME: DataKey[String] = DataKey("groupName")
  val GROUP_NAME_CONFIRMED: DataKey[Boolean] = DataKey("groupNameConfirmed")
  val SELECTED_CLIENTS: DataKey[Seq[DisplayClient]] = DataKey("groupClientsSelected")
  val GROUP_CLIENTS: DataKey[Seq[Client]] = DataKey("groupClients")

  val NAME_OF_GROUP_CREATED: DataKey[String] = DataKey("nameOfGroupCreated")
  val GROUP_RENAMED_FROM: DataKey[String] = DataKey("groupRenamedFrom")
  val GROUP_DELETED_NAME: DataKey[String] = DataKey("groupDeletedName")
  val FILTERED_CLIENTS: DataKey[Seq[DisplayClient]] = DataKey("filteredClients") //the filtered result
  val HIDDEN_CLIENTS_EXIST
    : DataKey[Boolean] = DataKey("hiddenClients") // some previously selected clients are not in the filtered result

  val FILTERED_TEAM_MEMBERS: DataKey[Seq[TeamMember]] = DataKey(
    "filteredTeamMembers")
  val HIDDEN_TEAM_MEMBERS_EXIST: DataKey[Boolean] = DataKey("hiddenTeamMembers")
  val SELECTED_TEAM_MEMBERS: DataKey[Seq[TeamMember]] = DataKey(
    "groupTeamMembersSelected")

  val GROUPS_FOR_UNASSIGNED_CLIENTS: DataKey[Seq[String]]
    = DataKey("groupsThatUnassignedClientsHaveBeenAddedTo")

  val sessionKeys =
    List(OPTIN_STATUS, GROUP_NAME, GROUP_NAME_CONFIRMED, GROUP_CLIENTS)

}
