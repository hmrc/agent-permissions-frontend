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

import models.{DisplayClient, GroupId, TeamMember}
import play.api.data.Form
import uk.gov.hmrc.agents.accessgroups.optin._
import uk.gov.hmrc.agents.accessgroups.{Client, GroupSummary}
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.Future
import scala.util.matching.Regex

package object controllers {

  implicit class ToFuture[T](t: T) {
    def toFuture: Future[T] = Future successful t
  }

  implicit class BooleanFold(boolean: Boolean) {
    def fold[B](l: B)(r: B => B): B = if (boolean) r(l) else l
    def foldAny(l: Any)(r: Any => Any): Any = if (boolean) r(l) else l
  }

  def formWithFilledValue[A](form: Form[A], mChoice: Option[A]): Form[A] =
    mChoice.fold(form)(form.fill)

  object GroupType {
    val CUSTOM = "custom"
    val TAX_SERVICE = "tax"
    def isCustom(groupType: String): Boolean = CUSTOM.equalsIgnoreCase(groupType)
  }

  final val CONTINUE_BUTTON: String = "continue"
  final val CLEAR_BUTTON: String = "clear"
  final val FILTER_BUTTON: String = "filter"
  final val PAGINATION_BUTTON: String = "pagination"
  final val MAX_PAGES_WITHOUT_ELLIPSIS: Int = 16
  final val CUSTOM_GROUP: String = "custom group"
  final val TAX_SERVICE_GROUP: String = "tax service group"
  val PAGINATION_REGEX: Regex = "(pagination_)(\\d+)".r

  val isEligibleToOptIn: OptinStatus => Boolean = status => status == OptedOutEligible
  val optedInStatii = Seq(OptedInReady, OptedInNotReady, OptedInSingleUser)
  val optedOutStatii = Seq(OptedOutEligible, OptedOutSingleUser, OptedOutWrongClientCount)
  val isOptedIn: OptinStatus => Boolean = status => optedInStatii.contains(status)
  val isOptedOut: OptinStatus => Boolean = status => optedOutStatii.contains(status)
  val isOptedInComplete: OptinStatus => Boolean = status => status == OptedInReady

  val OPT_IN_STATUS: DataKey[OptinStatus] = DataKey("optinStatus")
  val SUSPENSION_STATUS: DataKey[Boolean] = DataKey("SUSPENSION_STATUS")

  val GROUP_NAME: DataKey[String] = DataKey("groupName")
  val GROUP_NAME_CONFIRMED: DataKey[Boolean] = DataKey("groupNameConfirmed")

  val SELECTED_CLIENTS: DataKey[Seq[DisplayClient]] = DataKey("SELECTED_CLIENTS")
  val CURRENT_PAGE_CLIENTS: DataKey[Seq[DisplayClient]] = DataKey("CURRENT_PAGE_CLIENTS")
  val FORM_ERRORS: DataKey[Form[Any]] = DataKey("FORM_ERRORS")
  val CLIENT_FILTER_INPUT: DataKey[String] = DataKey("ClientFilterInputValue")
  val CLIENT_SEARCH_INPUT: DataKey[String] = DataKey("ClientSearchInputValue")
  val CLIENT_TO_REMOVE: DataKey[DisplayClient] = DataKey("CLIENT_TO_REMOVE")
  val MEMBER_TO_REMOVE: DataKey[TeamMember] = DataKey("MEMBER_TO_REMOVE")

  val GROUP_SEARCH_INPUT: DataKey[String] = DataKey("GROUP_SEARCH_INPUT")

  // TODO remove?
  val FILTERED_TEAM_MEMBERS: DataKey[Seq[TeamMember]] = DataKey("filteredTeamMembers")
  val FILTERED_CLIENTS: DataKey[Seq[DisplayClient]] = DataKey("filteredClients") // the filtered result

  val CURRENT_PAGE_TEAM_MEMBERS: DataKey[Seq[TeamMember]] = DataKey("CURRENT_PAGE_TEAM_MEMBERS")
  val SELECTED_TEAM_MEMBERS: DataKey[Seq[TeamMember]] = DataKey("groupTeamMembersSelected")
  val TEAM_MEMBER_SEARCH_INPUT: DataKey[String] = DataKey("TeamMemberSearchInputValue")
  val GROUP_SERVICE_TYPE: DataKey[String] = DataKey("GROUP_SERVICE_TYPE")
  val GROUP_TYPE: DataKey[String] = DataKey("GROUP_TYPE")

  val GROUP_CLIENTS: DataKey[Seq[Client]] = DataKey("groupClients")

  val NAME_OF_GROUP_CREATED: DataKey[String] = DataKey("nameOfGroupCreated")
  val GROUP_RENAMED_FROM: DataKey[String] = DataKey("groupRenamedFrom")
  val GROUP_DELETED_NAME: DataKey[String] = DataKey("groupDeletedName")
  val RETURN_URL: DataKey[String] = DataKey("returnUrl")

  val GROUPS_FOR_UNASSIGNED_CLIENTS: DataKey[Seq[String]] = DataKey("groupsThatUnassignedClientsHaveBeenAddedTo")

  val FILTERED_GROUP_SUMMARIES: DataKey[Seq[GroupSummary]] = DataKey("filteredGroupSummaries")
  val FILTERED_GROUPS_INPUT: DataKey[String] = DataKey("filteredGroupsInputValue")

  val CLIENT_REFERENCE: DataKey[String] = DataKey("clientRef")
  val GROUP_IDS_ADDED_TO: DataKey[Seq[GroupId]] = DataKey("groupIdsAddedTo")

  val CONFIRM_CLIENTS_SELECTED: DataKey[Boolean] = DataKey("confirmClientsSelected")
  val CONFIRM_TEAM_MEMBERS_SELECTED: DataKey[Boolean] = DataKey("confirmTeamMembersSelected")

  val clientFilteringKeys =
    List(
      FILTERED_CLIENTS,
      CLIENT_FILTER_INPUT,
      CLIENT_SEARCH_INPUT,
      CONFIRM_CLIENTS_SELECTED
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
      CONFIRM_CLIENTS_SELECTED,
      SELECTED_TEAM_MEMBERS,
      CONFIRM_TEAM_MEMBERS_SELECTED,
      GROUP_SERVICE_TYPE,
      RETURN_URL
    )

  val managingGroupKeys = (
    clientFilteringKeys ++
      teamMemberFilteringKeys ++
      List(
        SELECTED_CLIENTS,
        SELECTED_TEAM_MEMBERS
      )
  ).distinct

  val sessionKeys = (clientFilteringKeys ++
    teamMemberFilteringKeys ++
    creatingGroupKeys ++
    List(
      OPT_IN_STATUS,
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
      RETURN_URL
    )).distinct

}
