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

package controllers

import com.google.inject.AbstractModule
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector, UpdateTaxServiceGroupRequest}
import controllers.GroupType.TAX_SERVICE
import controllers.actions.AuthAction
import helpers.Css._
import helpers.{BaseSpec, Css}
import models.{AddTeamMembersToGroup, TeamMember}
import org.apache.commons.lang3.RandomStringUtils.random
import org.jsoup.Jsoup
import org.mongodb.scala.bson.ObjectId
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, redirectLocation}
import services.{GroupService, SessionCacheService, TaxGroupService, TeamMemberService}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

import java.time.LocalDate

class ManageTaxGroupTeamMembersControllerSpec extends BaseSpec {


  implicit lazy val authConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val agentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val agentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit lazy val sessionCacheService: SessionCacheService = mock[SessionCacheService]
  implicit val groupService: GroupService = mock[GroupService]
  implicit val taxGroupService: TaxGroupService = mock[TaxGroupService]
  implicit val teamMemberService: TeamMemberService = mock[TeamMemberService]

  val groupId = "xyz"
  private val agentUser: AgentUser = AgentUser(random(5), "Rob the Agent")
  val taxGroup: TaxGroup = new TaxGroup(
    new ObjectId(),
    arn,
    "Bananas",
    LocalDate.of(2020, 3, 10).atStartOfDay(),
    null,
    agentUser,
    agentUser,
    Some(Set(agentUser)),
    "VAT",
    false,
    None
  )

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(authConnector, env, conf, agentPermissionsConnector))
      bind(classOf[AgentPermissionsConnector]).toInstance(agentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(agentUserClientDetailsConnector)
      bind(classOf[GroupService]).toInstance(groupService)
      bind(classOf[SessionCacheService]).toInstance(sessionCacheService)
      bind(classOf[TeamMemberService]).toInstance(teamMemberService)
      bind(classOf[TaxGroupService]).toInstance(taxGroupService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val agentUsers: Set[AgentUser] = (1 to 5).map(i => AgentUser(id = s"John $i", name = s"John $i name")).toSet

  val userDetails: Seq[UserDetails] = (1 to 5)
    .map { i =>
      UserDetails(Some(s"John $i"), Some("User"), Some(s"John $i name"), Some(s"john$i@abc.com"))
    }

  val userDetails2: Seq[UserDetails] = (6 to 14)
    .map { i =>
      UserDetails(Some(s"John $i"), Some("User"), Some(s"John $i name"), Some(s"john$i@abc.com"))
    }

  val teamMembers: Seq[TeamMember] = userDetails.map(TeamMember.fromUserDetails)
  val teamMembers2: Seq[TeamMember] = userDetails2.map(TeamMember.fromUserDetails)

  val controller: ManageGroupTeamMembersController = fakeApplication.injector.instanceOf[ManageGroupTeamMembersController]
  private val ctrlRoute: ReverseManageGroupTeamMembersController = routes.ManageGroupTeamMembersController

  s"GET ${ctrlRoute.showExistingGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url}" should {

    "render correctly the manage EXISTING TEAM MEMBERS page with no filters set" in {
      //given
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup.copy(teamMembers = Some(agentUsers))))
      expectGetTeamMembersFromGroup(arn)(teamMembers)

      //when
      val result = controller.showExistingGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Manage team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage team members"

      val trs = html.select(Css.tableWithId("members")).select("tbody tr")

      trs.size() shouldBe 5

      trs.get(0).select("td").get(0).text() shouldBe "John 1 name"
      trs.get(0).select("td").get(1).text() shouldBe "john1@abc.com"
      trs.get(0).select("td").get(2).text() shouldBe "Administrator"

      trs.get(4).select("td").get(0).text() shouldBe "John 5 name"
      trs.get(4).select("td").get(1).text() shouldBe "john5@abc.com"
      trs.get(4).select("td").get(2).text() shouldBe "Administrator"
      html.select("a#update-team-members-button").text() shouldBe "Update team members"
    }

    "render with name/email searchTerm set" in {
      //given

      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showExistingGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url +
          "?submit=filter&search=John+1"
      ).withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup.copy(teamMembers = Some(agentUsers))))
      expectGetTeamMembersFromGroup(arn)(teamMembers)


      //when
      val result = controller.showExistingGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Filter results for 'John 1' Manage team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage team members"
      html.select(paragraphs).get(0).text shouldBe "Showing 1 to 1 of 1 team members for the search term ‘John 1’"

      val trs = html.select(Css.tableWithId("members")).select("tbody tr")

      trs.size() shouldBe 1

      trs.get(0).select("td").get(0).text() shouldBe "John 1 name"
      trs.get(0).select("td").get(1).text() shouldBe "john1@abc.com"
      trs.get(0).select("td").get(2).text() shouldBe "Administrator"

    }

    "render with email searchTerm set" in {
      //given
      implicit val requestWithQueryParams = FakeRequest(
        GET,
        ctrlRoute.showExistingGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url + "?submit=filter&search=hn2@ab"
      ).withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup.copy(teamMembers = Some(agentUsers))))
      expectGetTeamMembersFromGroup(arn)(teamMembers)

      //when
      val result = controller.showExistingGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Filter results for 'hn2@ab' Manage team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage team members"
      html.select(paragraphs).get(0).text shouldBe "Showing 1 to 1 of 1 team members for the search term ‘hn2@ab’"

      val trs =
        html.select(Css.tableWithId("members")).select("tbody tr")

      trs.size() shouldBe 1

      trs.get(0).select("td").get(0).text() shouldBe "John 2 name"
      trs.get(0).select("td").get(1).text() shouldBe "john2@abc.com"
      trs.get(0).select("td").get(2).text() shouldBe "Administrator"
    }

    "render with filter that matches nothing" in {
      //given
      implicit val requestWithQueryParams = FakeRequest(
        GET,
        ctrlRoute.showExistingGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url + s"?submit=$FILTER_BUTTON&search=hn2@ab"
      ).withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup.copy(teamMembers = Some(agentUsers))))
      expectGetTeamMembersFromGroup(arn)(Seq.empty)

      //when
      val result = controller.showExistingGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Filter results for 'hn2@ab' Manage team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage team members"

      val tableOfTeamMembers = html.select(Css.tableWithId("members"))
      tableOfTeamMembers.isEmpty shouldBe true
      val noClientsFound = html.select("div#members")
      noClientsFound.isEmpty shouldBe false
      noClientsFound.select("h2").text shouldBe "No team members found"
      noClientsFound.select("p").text shouldBe "Update your filters and try again or clear your filters to see all your team members"
    }

    "render with CLEAR_BUTTON" in {
      //given
      implicit val requestWithQueryParams = FakeRequest(
        GET,
        ctrlRoute.showExistingGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url + s"?submit=$CLEAR_BUTTON&search=hn2@ab"
      ).withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup.copy(teamMembers = Some(agentUsers))))
      expectGetTeamMembersFromGroup(arn)(Seq.empty)

      //when
      val result = controller.showExistingGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE)(requestWithQueryParams)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(ctrlRoute.showExistingGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url)
    }
  }

  s"GET ${ctrlRoute.showManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url}" should {

    "render correctly the manage TEAM MEMBERS LIST page when no team members are in the group" in {
      //given
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(SELECTED_TEAM_MEMBERS)
      expectGetSessionItemNone(TEAM_MEMBER_SEARCH_INPUT)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup.copy(teamMembers = None)))
      expectGetTeamMembersFromGroup(arn)(Seq.empty)
      expectGetPageOfTeamMembers(arn)(teamMembers)
      expectPutSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty)

      //when
      val result = controller.showManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Update team members in this group - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Update team members in this group"

      val trs = html.select(Css.tableWithId("multi-select-table")).select("tbody tr")

      trs.size() shouldBe 5

      trs.get(0).select("td").get(1).text() shouldBe "John 1 name"
      trs.get(0).select("td").get(2).text() shouldBe "john1@abc.com"
      trs.get(0).select("td").get(3).text() shouldBe "Administrator"

      trs.get(4).select("td").get(1).text() shouldBe "John 5 name"
      trs.get(4).select("td").get(2).text() shouldBe "john5@abc.com"
      trs.get(4).select("td").get(3).text() shouldBe "Administrator"

      html.select("p#member-count-text").text() shouldBe "0 team members selected across all searches"
    }

    "render correctly the manage TEAM MEMBERS LIST page filtered results exist" in {
      //given
      expectIsArnAllowed(allowed = true)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(SELECTED_TEAM_MEMBERS)
      expectGetSessionItem(TEAM_MEMBER_SEARCH_INPUT, "John")
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      val membersInGroup = teamMembers.take(4)
      expectGetTeamMembersFromGroup(arn)(membersInGroup)
      expectGetPageOfTeamMembers(arn)(membersInGroup)
      expectPutSessionItem(SELECTED_TEAM_MEMBERS, membersInGroup)

      //when
      val result = controller.showManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Filter results for 'John' Update team members in this group - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Update team members in this group"

      html.select(H2).text() shouldBe "Filter results for 'John'"

      val trs = html.select(Css.tableWithId("multi-select-table")).select("tbody tr")

      trs.size() shouldBe 4

      trs.get(0).select("td").get(1).text() shouldBe teamMembers.head.name
      trs.get(0).select("td").get(2).text() shouldBe teamMembers.head.email
      trs.get(0).select("td").get(3).text() shouldBe "Administrator"

      trs.get(1).select("td").get(1).text() shouldBe teamMembers(1).name
      trs.get(1).select("td").get(2).text() shouldBe teamMembers(1).email
      trs.get(1).select("td").get(3).text() shouldBe "Administrator"

      trs.get(3).select("td").get(1).text() shouldBe teamMembers(3).name
      trs.get(3).select("td").get(2).text() shouldBe teamMembers(3).email
      trs.get(3).select("td").get(3).text() shouldBe "Administrator"
    }

    "render correctly the manage TEAM MEMBERS LIST page" in {
      //given
      expectIsArnAllowed(allowed = true)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(SELECTED_TEAM_MEMBERS)
      expectGetSessionItemNone(TEAM_MEMBER_SEARCH_INPUT)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      expectGetPageOfTeamMembers(arn)(teamMembers)
      val membersInGroup = teamMembers.take(4)
      expectGetTeamMembersFromGroup(arn)(membersInGroup)
      expectPutSessionItem(SELECTED_TEAM_MEMBERS, membersInGroup)


      //when
      val result = controller.showManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Update team members in this group - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Update team members in this group"

      val trs = html.select(Css.tableWithId("multi-select-table")).select("tbody tr")

      trs.size() shouldBe 5

      trs.get(0).select("td").get(1).text() shouldBe "John 1 name"
      trs.get(0).select("td").get(2).text() shouldBe "john1@abc.com"
      trs.get(0).select("td").get(3).text() shouldBe "Administrator"

      trs.get(4).select("td").get(1).text() shouldBe "John 5 name"
      trs.get(4).select("td").get(2).text() shouldBe "john5@abc.com"
      trs.get(4).select("td").get(3).text() shouldBe "Administrator"
      html.select(Css.submitButton).text() shouldBe "Continue"
    }
  }

  s"POST to ${ctrlRoute.submitManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE).url}" should {

    s"successfully post redirect to ${ctrlRoute.showReviewSelectedTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url}" in {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE).url)
          .withFormUrlEncodedBody(
            "members[0]" -> teamMembers.head.id,
            "members[1]" -> teamMembers.last.id,
            "search" -> "",
            "submit" -> CONTINUE_BUTTON
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty) // with no preselected
      val expectedFormData = AddTeamMembersToGroup(None, Some(List(teamMembers.head.id, teamMembers.last.id)), CONTINUE_BUTTON)
      expectSavePageOfTeamMembers(expectedFormData, teamMembers)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)

      val result = controller.submitManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showReviewSelectedTeamMembers(taxGroup._id.toString, TAX_SERVICE, None)
        .url

    }

    "display error when none selected and CONTINUE button pressed" in {
      // given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE).url)
          .withFormUrlEncodedBody(
            "members" -> "",
            "search" -> "",
            "submit" -> CONTINUE_BUTTON
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty) // with no preselected
      expectGetFilteredTeamMembersElseAll(arn)(teamMembers)

      // when
      val result = controller.submitManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE)(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Select team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Select team members"
      html
        .select(Css.errorSummaryForField("members"))
    }

    "NOT display error when filter button is pushed with no search value" in {
      // given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE).url)
          .withFormUrlEncodedBody(
            "members" -> "",
            "search" -> "",
            "submit" -> FILTER_BUTTON
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      expectIsArnAllowed(allowed = true)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty) // doesn't matter
      val expectedFormData = AddTeamMembersToGroup(None, None, FILTER_BUTTON)
      expectSavePageOfTeamMembers(expectedFormData, teamMembers)

      // when
      val result = controller.submitManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE)(request)

      status(result) shouldBe SEE_OTHER

    }

    s"go to next page when $PAGINATION_BUTTON is pushed" in {
      // given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE).url)
          .withFormUrlEncodedBody(
            "members" -> "",
            "search" -> "",
            "submit" -> s"${PAGINATION_BUTTON}_2"
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      expectIsArnAllowed(allowed = true)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty) // doesn't matter
      val expectedFormData = AddTeamMembersToGroup(None, None, s"${PAGINATION_BUTTON}_2")
      expectSavePageOfTeamMembers(expectedFormData, teamMembers)

      // when
      val result = controller.submitManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(ctrlRoute.showManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, Option(2)).url)

    }

    s"redirect to ${ctrlRoute.showManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url} when the Filter is clicked " in {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE).url)
          .withFormUrlEncodedBody(
            "members" -> "",
            "search" -> "1",
            "submit" -> FILTER_BUTTON
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty) // doesn't matter
      val expectedFormData = AddTeamMembersToGroup(Some("1"), None, FILTER_BUTTON)
      expectSavePageOfTeamMembers(expectedFormData, teamMembers)

      // when
      val result = controller.submitManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE)(request)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe
        ctrlRoute.showManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url
    }
  }

  s"GET ${ctrlRoute.showReviewSelectedTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url}" should {


    "redirect if no team members selected in session" in {
      //given
      expectIsArnAllowed(allowed = true)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      expectGetSessionItemNone(SELECTED_TEAM_MEMBERS)

      //when
      val result = controller.showReviewSelectedTeamMembers(taxGroup._id.toString, TAX_SERVICE)(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe
        ctrlRoute.showManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url
    }

    "render correctly the manage group REVIEW SELECTED page" in {
      //given
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers ++ teamMembers2)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))

      //when
      val result = controller.showReviewSelectedTeamMembers(taxGroup._id.toString, TAX_SERVICE, Option(1))(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      //and
      html.title() shouldBe "Review selected team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 14 team members"

      html.select(Css.tableWithId("members")).select("tbody tr").size() shouldBe 10

      val paginationListItems = html.select(Css.pagination_li)
      paginationListItems.size() shouldBe 2
      paginationListItems.get(0).hasClass("govuk-pagination__item--current")
      paginationListItems.get(0).text() shouldBe "1"
      paginationListItems.get(1).text() shouldBe "2"
      paginationListItems.get(1).select("a")
        .attr("href") shouldBe ctrlRoute.showReviewSelectedTeamMembers(taxGroup._id.toString, TAX_SERVICE, Option(2)).url + "&pageSize=10"

      html.select("form .govuk-fieldset__legend").text() shouldBe "Do you need to select more team members?"
      val answerRadios = html.select(Css.radioButtonsField("answer-radios"))
      answerRadios
        .select("label[for=answer]")
        .text() shouldBe "Yes, select more team members"
      answerRadios
        .select("label[for=answer-no]")
        .text() shouldBe "No, continue"
      html.select(Css.submitButton).text() shouldBe "Save and continue"

    }
  }

  s"POST ${ctrlRoute.submitReviewSelectedTeamMembers(taxGroup._id.toString, TAX_SERVICE).url}" should {

    s"redirect to '${ctrlRoute.showGroupTeamMembersUpdatedConfirmation(taxGroup._id.toString, TAX_SERVICE)}' page with answer 'false'" in {

      implicit val request = FakeRequest("POST",
        s"${controller.submitReviewSelectedTeamMembers(taxGroup._id.toString, TAX_SERVICE)}")
        .withFormUrlEncodedBody("answer" -> "false")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      expectUpdateTaxGroup(taxGroup._id.toString, UpdateTaxServiceGroupRequest(teamMembers = Some(agentUsers)))

      val result = controller.submitReviewSelectedTeamMembers(taxGroup._id.toString, TAX_SERVICE)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute
        .showGroupTeamMembersUpdatedConfirmation(taxGroup._id.toString, TAX_SERVICE).url
    }

    s"redirect to '${ctrlRoute.showManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None)}'" +
      s" page with answer 'true'" in {

      implicit val request = FakeRequest("POST",
        s"${controller.submitReviewSelectedTeamMembers(taxGroup._id.toString, TAX_SERVICE)}")
        .withFormUrlEncodedBody("answer" -> "true")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))

      val result = controller.submitReviewSelectedTeamMembers(taxGroup._id.toString, TAX_SERVICE)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url
    }

    s"render errors when no radio button selected" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers(taxGroup._id.toString, TAX_SERVICE)}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))

      val result = controller.submitReviewSelectedTeamMembers(taxGroup._id.toString, TAX_SERVICE)(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Review selected team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 5 team members"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you need to select more team members"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you need to select more team members"

    }

    s"redirect to '${ctrlRoute.showExistingGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url}' when no SELECTED_TEAM_MEMBERS in session" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers(taxGroup._id.toString, TAX_SERVICE)}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      expectGetSessionItemNone(SELECTED_TEAM_MEMBERS)

      val result = controller.submitReviewSelectedTeamMembers(taxGroup._id.toString, TAX_SERVICE)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(ctrlRoute.showExistingGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url)

    }
  }

  s"GET ${ctrlRoute.showGroupTeamMembersUpdatedConfirmation(taxGroup._id.toString, TAX_SERVICE).url}" should {

    "redirect if no team members selected in session" in {
      //given
      expectIsArnAllowed(allowed = true)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      expectGetSessionItemNone(SELECTED_TEAM_MEMBERS)
      expectDeleteSessionItem(SELECTED_TEAM_MEMBERS)

      //when
      val result = controller.showGroupTeamMembersUpdatedConfirmation(taxGroup._id.toString, TAX_SERVICE)(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe
        ctrlRoute.showManageGroupTeamMembers(taxGroup._id.toString, TAX_SERVICE, None).url
    }

    "render correctly the manage TEAM MEMBERS UPDATED page" in {
      //given
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetTaxGroupById(taxGroup._id.toString, Some(taxGroup))
      expectDeleteSessionItem(SELECTED_TEAM_MEMBERS)

      //when
      val result = controller.showGroupTeamMembersUpdatedConfirmation(taxGroup._id.toString, TAX_SERVICE)(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Bananas access group team members updated - Agent services account - GOV.UK"
      html.select(Css.confirmationPanelH1).text() shouldBe "Bananas access group team members updated"
      html.select("a#returnToDashboard").text() shouldBe "Return to manage access groups"
      html.select("a#returnToDashboard").attr("href") shouldBe routes.ManageGroupController.showManageGroups(None, None).url
      html.select(Css.backLink).size() shouldBe 0
    }

  }

}
