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
import connectors.{AddMembersToTaxServiceGroupRequest, AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.GroupType.TAX_SERVICE
import controllers.actions.AuthAction
import helpers.Css._
import helpers.{BaseSpec, Css}
import models.TeamMember.toAgentUser
import models.{AddTeamMembersToGroup, GroupId, TeamMember}
import org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, redirectLocation}
import services.{GroupService, SessionCacheService, TaxGroupService, TeamMemberService}
import uk.gov.hmrc.agents.accessgroups.optin.OptedInReady
import uk.gov.hmrc.agents.accessgroups.{AgentUser, GroupSummary, TaxGroup, UserDetails}
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

  val groupId = GroupId.random()
  private val agentUser: AgentUser = AgentUser(randomAlphanumeric(5), "Rob the Agent")
  val taxGroup: TaxGroup = new TaxGroup(
    groupId,
    arn,
    "Bananas",
    LocalDate.of(2020, 3, 10).atStartOfDay(),
    null,
    agentUser,
    agentUser,
    Set(agentUser),
    "VAT",
    false,
    Set.empty
  )

  val taxGroupSummary = GroupSummary.of(taxGroup)

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


  def expectAuthOkOptedInReady(): Unit = {
    expectAuthorisationGrantsAccess(mockedAuthResponse)
    expectIsArnAllowed(allowed = true)
    expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
  }

  s"GET ${ctrlRoute.showExistingGroupTeamMembers(groupId, TAX_SERVICE, None).url}" should {

    "render correctly the manage EXISTING TEAM MEMBERS page with no filters set" in {
      //given
      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup.copy(teamMembers = agentUsers)))
      expectGetTeamMembersFromGroup(arn)(teamMembers)

      //when
      val result = controller.showExistingGroupTeamMembers(groupId, TAX_SERVICE)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Manage team members in this group - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage team members in this group"

      val trs = html.select(Css.tableWithId("members")).select("tbody tr")

      trs.size() shouldBe 5

      trs.get(0).select("td").get(0).text() shouldBe "John 1 name"
      trs.get(0).select("td").get(1).text() shouldBe "john1@abc.com"

      trs.get(4).select("td").get(0).text() shouldBe "John 5 name"
      trs.get(4).select("td").get(1).text() shouldBe "john5@abc.com"
      html.select("a#update-team-members-button").text() shouldBe "Add more team members"
    }

    "render with name/email searchTerm set" in {
      //given

      implicit val requestWithQueryParams = FakeRequest(GET,
        ctrlRoute.showExistingGroupTeamMembers(groupId, TAX_SERVICE, None).url +
          "?submit=filter&search=John+1"
      ).withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup.copy(teamMembers = agentUsers)))
      expectGetTeamMembersFromGroup(arn)(teamMembers)

      //when
      val result = controller.showExistingGroupTeamMembers(groupId, TAX_SERVICE)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Filter results for 'John 1' Manage team members in this group - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage team members in this group"
      html.select(paragraphs).get(0).text shouldBe "Showing 1 team members for ‘John 1’ in this group"

      val trs = html.select(Css.tableWithId("members")).select("tbody tr")

      trs.size() shouldBe 1

      trs.get(0).select("td").get(0).text() shouldBe "John 1 name"
      trs.get(0).select("td").get(1).text() shouldBe "john1@abc.com"
    }

    "render with email searchTerm set" in {
      //given
      implicit val requestWithQueryParams = FakeRequest(
        GET,
        ctrlRoute.showExistingGroupTeamMembers(groupId, TAX_SERVICE, None).url + "?submit=filter&search=hn2@ab"
      ).withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup.copy(teamMembers = agentUsers)))
      expectGetTeamMembersFromGroup(arn)(teamMembers)

      //when
      val result = controller.showExistingGroupTeamMembers(groupId, TAX_SERVICE)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Filter results for 'hn2@ab' Manage team members in this group - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage team members in this group"
      html.select(paragraphs).get(0).text shouldBe "Showing 1 team members for ‘hn2@ab’ in this group"

      val trs =
        html.select(Css.tableWithId("members")).select("tbody tr")

      trs.size() shouldBe 1

      trs.get(0).select("td").get(0).text() shouldBe "John 2 name"
      trs.get(0).select("td").get(1).text() shouldBe "john2@abc.com"
    }

    "render with filter that matches nothing" in {
      //given
      implicit val requestWithQueryParams = FakeRequest(
        GET,
        ctrlRoute.showExistingGroupTeamMembers(groupId, TAX_SERVICE, None).url + s"?submit=$FILTER_BUTTON&search=hn2@ab"
      ).withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup.copy(teamMembers = agentUsers)))
      expectGetTeamMembersFromGroup(arn)(Seq.empty)

      //when
      val result = controller.showExistingGroupTeamMembers(groupId, TAX_SERVICE)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Filter results for 'hn2@ab' Manage team members in this group - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Manage team members in this group"

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
        ctrlRoute.showExistingGroupTeamMembers(groupId, TAX_SERVICE, None).url + s"?submit=$CLEAR_BUTTON&search=hn2@ab"
      ).withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup.copy(teamMembers = agentUsers)))
      expectGetTeamMembersFromGroup(arn)(Seq.empty)

      //when
      val result = controller.showExistingGroupTeamMembers(groupId, TAX_SERVICE)(requestWithQueryParams)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(ctrlRoute.showExistingGroupTeamMembers(groupId, TAX_SERVICE, None).url)
    }
  }

  s"GET ${ctrlRoute.showAddTeamMembers(TAX_SERVICE, groupId, None).url}" should {

    "render correctly the manage TEAM MEMBERS LIST page when no team members are in the group" in {
      //given
      expectAuthOkOptedInReady()
      expectGetSessionItemNone(TEAM_MEMBER_SEARCH_INPUT)
      expectGetTaxGroupById(groupId, Some(taxGroup.copy(teamMembers = Set.empty)))
      expectGetTeamMembersFromGroup(arn)(Seq.empty)
      expectGetPageOfTeamMembers(arn)(teamMembers)

      //when
      val result = controller.showAddTeamMembers(TAX_SERVICE, groupId, None)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Select team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Select team members"

      val trs = html.select(Css.tableWithId("multi-select-table")).select("tbody tr")

      trs.size() shouldBe 5

      trs.get(0).select("td").get(1).text() shouldBe "John 1 name"
      trs.get(0).select("td").get(2).text() shouldBe "john1@abc.com"

      trs.get(4).select("td").get(1).text() shouldBe "John 5 name"
      trs.get(4).select("td").get(2).text() shouldBe "john5@abc.com"

      html.select("p#member-count-text").text() shouldBe "Selected 0 team members across all searches"
    }

    "render correctly the manage TEAM MEMBERS LIST page filtered results exist" in {
      //given
      expectAuthOkOptedInReady()
      expectGetSessionItem(TEAM_MEMBER_SEARCH_INPUT, "John")
      expectGetTaxGroupById(groupId, Some(taxGroup))
      val membersInGroup = teamMembers.take(4)
      expectGetTeamMembersFromGroup(arn)(membersInGroup)
      expectGetPageOfTeamMembers(arn)(membersInGroup)

      //when
      val result = controller.showAddTeamMembers(TAX_SERVICE, groupId, None)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Filter results for 'John' Select team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Select team members"

      html.select(H2).text() shouldBe "Showing 4 team members for ‘John’"

      val trs = html.select(Css.tableWithId("multi-select-table")).select("tbody tr")

      trs.size() shouldBe 4

      trs.get(0).select("td").get(1).text() shouldBe teamMembers.head.name
      trs.get(0).select("td").get(2).text() shouldBe teamMembers.head.email

      trs.get(1).select("td").get(1).text() shouldBe teamMembers(1).name
      trs.get(1).select("td").get(2).text() shouldBe teamMembers(1).email

      trs.get(3).select("td").get(1).text() shouldBe teamMembers(3).name
      trs.get(3).select("td").get(2).text() shouldBe teamMembers(3).email
    }

    "render correctly the manage TEAM MEMBERS LIST page" in {
      //given
      expectAuthOkOptedInReady()
      expectGetSessionItemNone(TEAM_MEMBER_SEARCH_INPUT)
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetPageOfTeamMembers(arn)(teamMembers)
      val membersInGroup = teamMembers.take(4)
      expectGetTeamMembersFromGroup(arn)(membersInGroup)

      //when
      val result = controller.showAddTeamMembers(TAX_SERVICE, groupId, None)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Select team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Select team members"

      val trs = html.select(Css.tableWithId("multi-select-table")).select("tbody tr")

      trs.size() shouldBe 5

      trs.get(0).select("td").get(1).text() shouldBe "John 1 name"
      trs.get(0).select("td").get(2).text() shouldBe "john1@abc.com"

      trs.get(4).select("td").get(1).text() shouldBe "John 5 name"
      trs.get(4).select("td").get(2).text() shouldBe "john5@abc.com"
      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }
  }

  s"POST to ${ctrlRoute.submitAddTeamMembers(TAX_SERVICE, groupId).url}" should {

    s"successfully post redirect to ${ctrlRoute.showReviewTeamMembersToAdd(TAX_SERVICE, groupId, None, None).url}" in {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitAddTeamMembers(TAX_SERVICE, groupId).url)
          .withFormUrlEncodedBody(
            "members[0]" -> teamMembers.head.id,
            "members[1]" -> teamMembers.last.id,
            "search" -> "",
            "submit" -> CONTINUE_BUTTON
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty) // with no preselected
      val expectedFormData = AddTeamMembersToGroup(None, Some(List(teamMembers.head.id, teamMembers.last.id)), CONTINUE_BUTTON) // checks formData =>
      expectSavePageOfTeamMembers(expectedFormData, teamMembers) // checks .savePageOfTeamMembers(formData)

      val result = controller.submitAddTeamMembers(TAX_SERVICE, groupId)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showReviewTeamMembersToAdd(TAX_SERVICE, groupId, None, None).url

    }

    "display error when none selected and CONTINUE button pressed" in {
      // given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitAddTeamMembers(TAX_SERVICE, groupId).url)
          .withFormUrlEncodedBody(
            "members" -> "",
            "search" -> "",
            "submit" -> CONTINUE_BUTTON
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty) // with no preselected
      expectGetPageOfTeamMembers(arn)(teamMembers)

      // when
      val result = controller.submitAddTeamMembers(TAX_SERVICE, groupId)(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then - check page content
      html.title() shouldBe "Error: Select team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "Select team members"
      html
        .select(Css.errorSummaryForField("members"))
    }

    "NOT display error when filter button is pushed with no search value" in {
      // given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitAddTeamMembers(TAX_SERVICE, groupId).url)
          .withFormUrlEncodedBody(
            "members" -> "",
            "search" -> "",
            "submit" -> FILTER_BUTTON
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty) // doesn't matter
      val expectedFormData = AddTeamMembersToGroup(None, None, FILTER_BUTTON)
      expectSavePageOfTeamMembers(expectedFormData, teamMembers)

      // when
      val result = controller.submitAddTeamMembers(TAX_SERVICE, groupId)(request)

      status(result) shouldBe SEE_OTHER

    }

    s"go to next page when $PAGINATION_BUTTON is pushed" in {
      // given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitAddTeamMembers(TAX_SERVICE, groupId).url)
          .withFormUrlEncodedBody(
            "members" -> "",
            "search" -> "",
            "submit" -> s"${PAGINATION_BUTTON}_2"
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty) // doesn't matter
      val expectedFormData = AddTeamMembersToGroup(None, None, s"${PAGINATION_BUTTON}_2")
      expectSavePageOfTeamMembers(expectedFormData, teamMembers)

      // when
      val result = controller.submitAddTeamMembers(TAX_SERVICE, groupId)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(ctrlRoute.showAddTeamMembers(TAX_SERVICE, groupId, Option(2)).url)

    }

    s"redirect to ${ctrlRoute.showAddTeamMembers(TAX_SERVICE, groupId, None).url} when the Filter is clicked " in {

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", ctrlRoute.submitAddTeamMembers(TAX_SERVICE, groupId).url)
          .withFormUrlEncodedBody(
            "members" -> "",
            "search" -> "1",
            "submit" -> FILTER_BUTTON
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty) // doesn't matter
      val expectedFormData = AddTeamMembersToGroup(Some("1"), None, FILTER_BUTTON)
      expectSavePageOfTeamMembers(expectedFormData, teamMembers)

      // when
      val result = controller.submitAddTeamMembers(TAX_SERVICE, groupId)(request)
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe
        ctrlRoute.showAddTeamMembers(TAX_SERVICE, groupId, None).url
    }
  }

  s"GET ${ctrlRoute.showReviewTeamMembersToAdd(TAX_SERVICE, groupId, None, None).url}" should {

    "redirect if no team members selected in session" in {
      //given
      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetSessionItemNone(SELECTED_TEAM_MEMBERS)

      //when
      val result = controller.showReviewTeamMembersToAdd(TAX_SERVICE, groupId, None, None)(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showExistingGroupTeamMembers(groupId, TAX_SERVICE, None).url
    }

    "render correctly the manage group REVIEW SELECTED page" in {
      //given
      expectAuthOkOptedInReady()
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers ++ teamMembers2)
      expectGetTaxGroupById(groupId, Some(taxGroup))

      //when
      val result = controller.showReviewTeamMembersToAdd(TAX_SERVICE, groupId, Option(1), None)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      //and
      html.title() shouldBe "Review selected team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 14 team members to add to the group"

      html.select(Css.tableWithId("members")).select("tbody tr").size() shouldBe 10

      val paginationListItems = html.select(Css.pagination_li)
      paginationListItems.size() shouldBe 2
      paginationListItems.get(0).hasClass("govuk-pagination__item--current")
      paginationListItems.get(0).text() shouldBe "1"
      paginationListItems.get(1).text() shouldBe "2"
      paginationListItems.get(1).select("a")
        .attr("href") shouldBe ctrlRoute.showReviewTeamMembersToAdd(TAX_SERVICE, groupId, Option(2), None).url + "&pageSize=10"

      html.select("form .govuk-fieldset__legend").text() shouldBe "Do you need to select more team members?"
      val answerRadios = html.select(Css.radioButtonsField("answer-radios"))
      answerRadios
        .select("label[for=answer]")
        .text() shouldBe "Yes, select more team members"
      answerRadios
        .select("label[for=answer-no]")
        .text() shouldBe "No"
      html.select(Css.submitButton).text() shouldBe "Save and continue"

    }

    "render correctly when only 1 team member selected (you can't remove the last member)" in {
      //given
      expectAuthOkOptedInReady()
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers.take(1))
      expectGetTaxGroupById(groupId, Some(taxGroup))

      //when
      val result = controller.showReviewTeamMembersToAdd(TAX_SERVICE, groupId, Option(1), None)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      //and
      html.title() shouldBe "Review selected team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 1 team members to add to the group"

      val tableOfSelectedMembers = html.select(Css.tableWithId("members"))
      //only 2 table columns as REMOVE link (i.e. normally the last table column) should not be present
      tableOfSelectedMembers.select("tbody tr").size() shouldBe 1
      val row1Cells = tableOfSelectedMembers.select("tbody tr td")
      row1Cells.size() shouldBe 2

      //no pagination as only 1 member selected
      val paginationListItems = html.select(Css.pagination_li)
      paginationListItems.size() shouldBe 0

    }
  }

  s"POST ${ctrlRoute.submitReviewTeamMembersToAdd(TAX_SERVICE, groupId).url}" should {

    s"redirect to '${ctrlRoute.showExistingGroupTeamMembers(groupId, TAX_SERVICE, None)}' page with answer 'false'" in {

      val endpoint = s"${controller.submitReviewTeamMembersToAdd(TAX_SERVICE, groupId)}"
      implicit val request = FakeRequest("POST", endpoint)
        .withFormUrlEncodedBody("answer" -> "false")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      expectGetTaxGroupById(groupId, Some(taxGroup))
      val expectedUsersToAdd = teamMembers.map(tm => toAgentUser(tm)).toSet
      expectAddMembersToTaxGroup(groupId, AddMembersToTaxServiceGroupRequest(teamMembers = Some(expectedUsersToAdd)))

      expectDeleteSessionItem(SELECTED_TEAM_MEMBERS)

      val result = controller.submitReviewTeamMembersToAdd(TAX_SERVICE, groupId)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showExistingGroupTeamMembers(groupId, TAX_SERVICE, None).url
    }

    s"redirect to '${ctrlRoute.showAddTeamMembers(TAX_SERVICE, groupId, None)}' page with answer 'true'" in {

      implicit val request = FakeRequest("POST",
        s"${controller.submitReviewTeamMembersToAdd(TAX_SERVICE, groupId)}")
        .withFormUrlEncodedBody("answer" -> "true")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      expectGetTaxGroupById(groupId, Some(taxGroup))

      val result = controller.submitReviewTeamMembersToAdd(TAX_SERVICE, groupId)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showExistingGroupTeamMembers(groupId, TAX_SERVICE, None).url
    }

    s"render errors when no radio button selected" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewTeamMembersToAdd(TAX_SERVICE, groupId)}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      expectGetTaxGroupById(groupId, Some(taxGroup))

      val result = controller.submitReviewTeamMembersToAdd(TAX_SERVICE, groupId)(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Review selected team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 5 team members to add to the group"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you need to select more team members"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you need to select more team members"

    }

    s"redirect to '${ctrlRoute.showExistingGroupTeamMembers(groupId, TAX_SERVICE, None).url}' when no SELECTED_TEAM_MEMBERS in session" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewTeamMembersToAdd(TAX_SERVICE, groupId)}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetSessionItemNone(SELECTED_TEAM_MEMBERS)

      val result = controller.submitReviewTeamMembersToAdd(TAX_SERVICE, groupId)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(ctrlRoute.showExistingGroupTeamMembers(groupId, TAX_SERVICE, None).url)

    }
  }

  val memberToRemove = teamMembers.head

  s"GET ${ctrlRoute.showConfirmRemoveTeamMember(groupId, GroupType.TAX_SERVICE, memberToRemove.id).url}" should {

    "render the confirm remove team member page" in {

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectLookupTeamMember(arn)(memberToRemove)
      expectPutSessionItem(MEMBER_TO_REMOVE, memberToRemove)

      val result = controller.showConfirmRemoveTeamMember(groupId, GroupType.TAX_SERVICE, memberToRemove.id)(request)
      // then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Remove John 1 name from this access group? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Remove John 1 name from this access group?"
      html
        .select(Css.backLink)
        .attr("href") shouldBe ctrlRoute.showExistingGroupTeamMembers(groupId, TAX_SERVICE, None).url


      html.select(Css.form).attr("action") shouldBe ctrlRoute.submitConfirmRemoveTeamMember(groupId, TAX_SERVICE).url
      html.select("label[for=answer]").text() shouldBe "Yes"
      html.select("label[for=answer-no]").text() shouldBe "No"
      html.select(Css.form + " input[name=answer]").size() shouldBe 2
      html.select(Css.submitButton).text() shouldBe "Save and continue"

    }

  }

  s"POST confirm remove team member at:${ctrlRoute.submitConfirmRemoveTeamMember(groupId, TAX_SERVICE).url}" should {

    " remove from group and redirect to group team members list when 'yes' selected" in {
      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetSessionItem(MEMBER_TO_REMOVE, memberToRemove)
      expectRemoveTeamMemberFromGroup(groupId, memberToRemove, false)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitConfirmRemoveTeamMember(groupId, TAX_SERVICE)}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")


      val result = controller.submitConfirmRemoveTeamMember(groupId, TAX_SERVICE)(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showExistingGroupTeamMembers(groupId, TAX_SERVICE, None).url
    }

    "redirects to group members list when  'no' selected " in {
      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetSessionItem(MEMBER_TO_REMOVE, memberToRemove)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest(
          "POST", s"${controller.submitConfirmRemoveTeamMember(groupId, TAX_SERVICE)}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")


      val result = controller.submitConfirmRemoveTeamMember(groupId, TAX_SERVICE)(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showExistingGroupTeamMembers(groupId, TAX_SERVICE, None).url
    }

    "render errors when no selections of yes/no made" in {

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetSessionItem(MEMBER_TO_REMOVE, memberToRemove)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitConfirmRemoveTeamMember(groupId, TAX_SERVICE)}")
          .withFormUrlEncodedBody("ohai" -> "blah")
          .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.submitConfirmRemoveTeamMember(groupId, TAX_SERVICE)(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Remove John 1 name from this access group? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Remove John 1 name from this access group?"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you need to remove this team member from the access group"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you need to remove this team member from the access group"

    }
  }

  s"GET show confirm remove from team members to add ${ctrlRoute.showConfirmRemoveFromTeamMembersToAdd(TAX_SERVICE, groupId, memberToRemove.id).url}" should {

    "render the confirm remove team member page" in {

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetSessionItem(MEMBER_TO_REMOVE, memberToRemove)

      val result = controller.showConfirmRemoveFromTeamMembersToAdd(TAX_SERVICE, groupId, memberToRemove.id)(request)
      // then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Remove John 1 name from selected team members? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Remove John 1 name from selected team members?"
      html.select(Css.paragraphs).isEmpty() shouldBe true
      html
        .select(Css.backLink)
        .attr("href") shouldBe ctrlRoute.showReviewTeamMembersToAdd(TAX_SERVICE, groupId, None, None).url


      html.select(Css.form).attr("action") shouldBe ctrlRoute.submitConfirmRemoveFromTeamMembersToAdd(TAX_SERVICE, groupId, memberToRemove.id).url
      html.select("label[for=answer]").text() shouldBe "Yes"
      html.select("label[for=answer-no]").text() shouldBe "No"
      html.select(Css.form + " input[name=answer]").size() shouldBe 2
      html.select(Css.submitButton).text() shouldBe "Save and continue"

    }

    "redirect when no team member found" in {

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetSessionItemNone(MEMBER_TO_REMOVE)

      val result = controller.showConfirmRemoveFromTeamMembersToAdd(TAX_SERVICE, groupId, memberToRemove.id)(request)
      // then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showReviewTeamMembersToAdd(TAX_SERVICE, groupId, None, None).url

    }

  }

  //  actual GroupService.getCustomSummary(09ddd6b7-390b-4361-981c-51fe04d452c3, HeaderCarrier(None,None,Some(SessionId(session-x)),None,RequestChain(ee9c),783259514121333,List(),None,None,None,None,None,None,List((Host,localhost), (path,Action(parser=<function1>))))
  //  expected GroupService.getCustomSummary(2230467a-02f7-4863-b8cc-79ae1a539527, *, *)
  s"POST submitConfirmRemoveFromTeamMembersToAdd ${ctrlRoute.submitConfirmRemoveFromTeamMembersToAdd(TAX_SERVICE, groupId, memberToRemove.id).url}" should {

    "confirm remove client 'yes' removes  from group and redirect to group clients list" in {

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetSessionItem(MEMBER_TO_REMOVE, memberToRemove)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      expectPutSessionItem(SELECTED_TEAM_MEMBERS, teamMembers.filterNot(_.id == memberToRemove.id))

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitConfirmRemoveFromTeamMembersToAdd(TAX_SERVICE, groupId, memberToRemove.id)}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")


      val result = controller.submitConfirmRemoveFromTeamMembersToAdd(TAX_SERVICE, groupId, memberToRemove.id)(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showReviewTeamMembersToAdd(TAX_SERVICE, groupId, None, None).url
    }

    "confirm remove client 'no' redirects to group clients list" in {

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetSessionItem(MEMBER_TO_REMOVE, memberToRemove)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitConfirmRemoveFromTeamMembersToAdd(TAX_SERVICE, groupId, memberToRemove.id)}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")


      val result = controller.submitConfirmRemoveFromTeamMembersToAdd(TAX_SERVICE, groupId, memberToRemove.id)(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe ctrlRoute.showReviewTeamMembersToAdd(TAX_SERVICE, groupId, None, None).url
    }

    "render errors when no selections of yes/no made" in {

      expectAuthOkOptedInReady()
      expectGetTaxGroupById(groupId, Some(taxGroup))
      expectGetSessionItem(MEMBER_TO_REMOVE, memberToRemove)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", s"${controller.submitConfirmRemoveFromTeamMembersToAdd(TAX_SERVICE, groupId, memberToRemove.id)}")
          .withFormUrlEncodedBody("ohai" -> "blah")
          .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.submitConfirmRemoveFromTeamMembersToAdd(TAX_SERVICE, groupId, memberToRemove.id)(request)

      //then
      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Remove John 1 name from selected team members? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Remove John 1 name from selected team members?"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you no longer want to add this team member to the access group"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you no longer want to add this team member to the access group"

    }
  }
}
