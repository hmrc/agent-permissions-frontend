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

import connectors.GroupSummary
import models.{DisplayClient, TeamMember}
import play.api.{Configuration, Environment, Mode}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.Future

package object controllers {

  implicit class ToFuture[T](t: T) {
    def toFuture: Future[T] = Future successful t
  }

  implicit class EnvironmentOps(private val env: Environment) extends AnyVal {

    def isLocal(implicit config: Configuration): Boolean = {
      env.mode == Mode.Test
    }
  }

  final val CONTINUE_BUTTON: String = "continue"
  final val CLEAR_BUTTON: String = "clear"
  final val FILTER_BUTTON: String = "filter"

  val isEligibleToOptIn: OptinStatus => Boolean = status => status == OptedOutEligible
  val isOptedIn: OptinStatus => Boolean = status =>
    status == OptedInReady || status == OptedInNotReady || status == OptedInSingleUser
  val isOptedOut: OptinStatus => Boolean = status =>
    status == OptedOutEligible || status == OptedOutSingleUser || status == OptedOutWrongClientCount
  val isOptedInComplete: OptinStatus => Boolean = status => status == OptedInReady

  val OPTIN_STATUS: DataKey[OptinStatus] = DataKey("optinStatus")

  val GROUP_NAME: DataKey[String] = DataKey("groupName")
  val GROUP_NAME_CONFIRMED: DataKey[Boolean] = DataKey("groupNameConfirmed")

  val SELECTED_CLIENTS: DataKey[Seq[DisplayClient]] = DataKey("groupClientsSelected")
  val FILTERED_CLIENTS: DataKey[Seq[DisplayClient]] = DataKey("filteredClients") //the filtered result
  val CLIENT_FILTER_INPUT: DataKey[String] = DataKey("ClientFilterInputValue")
  val CLIENT_SEARCH_INPUT: DataKey[String] = DataKey("ClientSearchInputValue")

  val FILTERED_TEAM_MEMBERS: DataKey[Seq[TeamMember]] = DataKey("filteredTeamMembers")
  val SELECTED_TEAM_MEMBERS: DataKey[Seq[TeamMember]] = DataKey("groupTeamMembersSelected")
  val TEAM_MEMBER_SEARCH_INPUT: DataKey[String] = DataKey("TeamMemberSearchInputValue")

  val GROUP_CLIENTS: DataKey[Seq[Client]] = DataKey("groupClients")

  val NAME_OF_GROUP_CREATED: DataKey[String] = DataKey("nameOfGroupCreated")
  val GROUP_RENAMED_FROM: DataKey[String] = DataKey("groupRenamedFrom")
  val GROUP_DELETED_NAME: DataKey[String] = DataKey("groupDeletedName")
  val RETURN_URL: DataKey[String] = DataKey("returnUrl")

  val GROUPS_FOR_UNASSIGNED_CLIENTS: DataKey[Seq[String]]
  = DataKey("groupsThatUnassignedClientsHaveBeenAddedTo")

  val FILTERED_GROUP_SUMMARIES: DataKey[Seq[GroupSummary]] = DataKey("filteredGroupSummaries")
  val FILTERED_GROUPS_INPUT: DataKey[String] = DataKey("filteredGroupsInputValue")

  val CLIENT_REFERENCE: DataKey[String] = DataKey("clientRef")
  val GROUP_IDS_ADDED_TO: DataKey[Seq[String]] = DataKey("groupIdsAddedTo")

  val clientFilteringKeys =
    List(
      FILTERED_CLIENTS,
      CLIENT_FILTER_INPUT,
      CLIENT_SEARCH_INPUT,
    )

  val teamMemberFilteringKeys =
    List(
      FILTERED_TEAM_MEMBERS,
      TEAM_MEMBER_SEARCH_INPUT
    )

  val creatingGroupKeys =
    List(
      GROUP_NAME,
      GROUP_NAME_CONFIRMED,
      SELECTED_CLIENTS,
      SELECTED_TEAM_MEMBERS,
      RETURN_URL,
    )

  val sessionKeys = (
    clientFilteringKeys ++
      teamMemberFilteringKeys ++
      creatingGroupKeys ++
      List(
        OPTIN_STATUS,
        GROUP_NAME,
        GROUP_NAME_CONFIRMED,
        GROUP_CLIENTS,
        NAME_OF_GROUP_CREATED,
        GROUP_RENAMED_FROM,
        GROUP_DELETED_NAME,
        GROUPS_FOR_UNASSIGNED_CLIENTS,
        FILTERED_GROUP_SUMMARIES,
        CLIENT_REFERENCE,
        CLIENT_FILTER_INPUT,
        CLIENT_SEARCH_INPUT,
        TEAM_MEMBER_SEARCH_INPUT,
        RETURN_URL,
      )).distinct

}
