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
import connectors.{AddOneTeamMemberToGroupRequest, AgentAssuranceConnector, AgentPermissionsConnector}
import controllers.actions.AuthAction
import forms.AddGroupsToClientForm
import helpers.{BaseSpec, Css}
import models.{GroupId, TeamMember}
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, redirectLocation}
import services.{GroupService, SessionCacheService, TaxGroupService, TeamMemberService}
import uk.gov.hmrc.agents.accessgroups.{GroupSummary, UserDetails}
import uk.gov.hmrc.agents.accessgroups.optin._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

class AddTeamMemberToGroupsControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentClientAuthConnector: AgentAssuranceConnector =
    mock[AgentAssuranceConnector]
  implicit val groupService: GroupService = mock[GroupService]
  implicit val taxGroupService: TaxGroupService = mock[TaxGroupService]
  implicit val mockTeamMemberService: TeamMemberService = mock[TeamMemberService]
  implicit val mockSessionCacheService: SessionCacheService = mock[SessionCacheService]

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(
        new AuthAction(
          mockAuthConnector,
          env,
          conf,
          mockAgentPermissionsConnector,
          mockAgentClientAuthConnector,
          mockSessionCacheService
        )
      )
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[TaxGroupService]).toInstance(taxGroupService)
      bind(classOf[GroupService]).toInstance(groupService)
      bind(classOf[TeamMemberService]).toInstance(mockTeamMemberService)
      bind(classOf[SessionCacheService]).toInstance(mockSessionCacheService)
    }
  }

  private val controller = fakeApplication.injector.instanceOf[AddTeamMemberToGroupsController]
  private val ctrlRoute: ReverseAddTeamMemberToGroupsController = routes.AddTeamMemberToGroupsController

  override implicit lazy val fakeApplication: Application =
    appBuilder.configure("mongodb.uri" -> mongoUri).build()

  val userDetails: Seq[UserDetails] = (1 to 5)
    .map { i =>
      UserDetails(
        Some(s"john.smith$i"),
        Some("User"),
        Some(s"John Smith $i"),
        Some(s"john$i@abc.com")
      )
    }

  val teamMembers: Seq[TeamMember] = userDetails.map(TeamMember.fromUserDetails)
  val teamMember: TeamMember = teamMembers.head

  def AuthOkWithTeamMember(teamMember: TeamMember = teamMember): Unit = {
    expectAuthorisationGrantsAccess(mockedAuthResponse)
    expectGetSessionItem(SUSPENSION_STATUS, false)
    expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
    expectIsArnAllowed(allowed = true)
    expectLookupTeamMember(arn)(teamMember)
  }

  private val submitUrl: String = ctrlRoute.submitSelectGroupsForTeamMember(teamMember.id).url

  s"GET ${ctrlRoute.showSelectGroupsForTeamMember(teamMember.id).url}" should {

    "render correctly when the member is in some existing groups that share the same group ID" in {
      // given
      val sharedGroupId = GroupId.random()
      val groupSummaries = Seq(
        GroupSummary(sharedGroupId, "Group ABC", Some(1), 4),
        GroupSummary(GroupId.random(), "Group XYZ", Some(1), 4)
      )
      val groupsAlreadyAssociatedToMember = Seq(
        GroupSummary(sharedGroupId, "Group ABC", None, 1)
      )

      AuthOkWithTeamMember()
      expectDeleteSessionItem(GROUP_IDS_ADDED_TO)
      expectGetGroupsForArn(arn)(groupSummaries)
      expectGetGroupSummariesForTeamMember(arn)(teamMember)(groupsAlreadyAssociatedToMember)

      // when
      val result = controller.showSelectGroupsForTeamMember(teamMember.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      html
        .title() shouldBe "Which access groups would you like to add John Smith 1 to? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Which access groups would you like to add John Smith 1 to?"
      html.select(Css.paragraphs).get(0).text() shouldBe "Team member is currently in these access groups:"
      html.select(Css.li("already-in-groups")).get(0).text() shouldBe "Group ABC"
      val form = html.select(Css.form)
      form.attr("action").shouldBe(submitUrl)

      val fieldset = form.select("fieldset.govuk-fieldset") // fieldset must exist and have a legend
      fieldset.select(Css.legend).text() shouldBe "Which access groups would you like to add John Smith 1 to?"
      fieldset.select("#groups-hint").text() shouldBe "Select all that apply"
      val checkboxes = fieldset.select(".govuk-checkboxes#groups input[name=groups[]]")
      checkboxes.size() shouldBe 2
      val checkboxLabels = form.select("label.govuk-checkboxes__label")
      checkboxLabels.get(0).text() shouldBe "Group XYZ"
      checkboxLabels.get(1).text() shouldBe "No access groups"
      form.select("#__none__-item-hint").get(0).text() shouldBe "This will return you to the Manage account page"
      html.select(Css.linkStyledAsButton).text() shouldBe "Cancel"
      html.select(Css.linkStyledAsButton).hasClass("govuk-button--secondary")
      html.select(Css.linkStyledAsButton).hasClass("govuk-!-margin-right-3")
      html.select(Css.submitButton).text() shouldBe "Save and continue"

      html
        .select(".hmrc-report-technical-issue")
        .text() shouldBe "Is this page not working properly? (opens in new tab)"
      html
        .select(".hmrc-report-technical-issue")
        .attr("href") startsWith "http://localhost:9250/contact/report-technical-problem?newTab=true&service=AOSS"

    }

    "render correctly when the member is not in any existing groups" in {
      // given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(GroupId.random(), s"Group $i", Some(i * 3), i * 4))
      val groupsAlreadyAssociatedToMember = Seq.empty

      AuthOkWithTeamMember()
      expectDeleteSessionItem(GROUP_IDS_ADDED_TO)
      expectGetGroupsForArn(arn)(groupSummaries)
      expectGetGroupSummariesForTeamMember(arn)(teamMember)(groupsAlreadyAssociatedToMember)

      // when
      val result = controller.showSelectGroupsForTeamMember(teamMember.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      html
        .title() shouldBe "Which access groups would you like to add John Smith 1 to? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Which access groups would you like to add John Smith 1 to?"
      html.select(Css.paragraphs).get(0).text() shouldBe "Team member is currently not in any access groups"
      html.select(Css.li("already-in-groups")).isEmpty shouldBe true
      val form = html.select(Css.form)
      form.attr("action").shouldBe(submitUrl)
      val checkboxes = form.select(".govuk-checkboxes#groups input[name=groups[]]")
      checkboxes.size() shouldBe 6
      val checkboxLabels = form.select("label.govuk-checkboxes__label")
      checkboxLabels.get(0).text() shouldBe "Group 1"
      checkboxLabels.get(1).text() shouldBe "Group 2"
      checkboxLabels.get(2).text() shouldBe "Group 3"
      checkboxLabels.get(3).text() shouldBe "Group 4"
      checkboxLabels.get(4).text() shouldBe "Group 5"
      checkboxLabels.get(5).text() shouldBe "No access groups"
      form.select("#__none__-item-hint").get(0).text() shouldBe "This will return you to the Manage account page"
      html.select(Css.linkStyledAsButton).text() shouldBe "Cancel"
      html.select(Css.linkStyledAsButton).hasClass("govuk-button--secondary")
      html.select(Css.linkStyledAsButton).hasClass("govuk-!-margin-right-3")
      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }

    "render correctly when there are no available groups" in {
      // given
      val groupSummaries = (1 to 2)
        .map(i => GroupSummary(GroupId.random(), s"Group $i", Some(i * 3), i * 4))
      val groupsAlreadyAssociatedToMember = groupSummaries

      AuthOkWithTeamMember()
      expectDeleteSessionItem(GROUP_IDS_ADDED_TO)
      expectGetGroupsForArn(arn)(groupSummaries)
      expectGetGroupSummariesForTeamMember(arn)(teamMember)(groupsAlreadyAssociatedToMember)

      // when
      val result = controller.showSelectGroupsForTeamMember(teamMember.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "There are no available groups to add John Smith 1 to - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "There are no available groups to add John Smith 1 to"
      html.select(Css.paragraphs).get(0).text() shouldBe "Team member is currently in these access groups:"
      html.select(Css.li("already-in-groups")).get(0).text() shouldBe "Group 1"
      html.select(Css.li("already-in-groups")).get(1).text() shouldBe "Group 2"
      val form = html.select(Css.form)
      form.size() shouldBe 0
      val backLink = html.select("main a#back-to-member-details")
      backLink.text() shouldBe "Back to John Smith 1"
      backLink.attr("href") shouldBe routes.ManageTeamMemberController.showTeamMemberDetails(teamMember.id).url
    }

  }

  s"POST to $submitUrl" should {

    "add team member to the selected groups and redirect" when {

      s"At least 1 checkbox is checked for the group to add to" in {
        // given
        AuthOkWithTeamMember()

        val groupSummaries = (1 to 5).map(i => GroupSummary(GroupId.random(), s"Group $i", Some(i * 3), i * 4))
        val expectedAddRequest1 = AddOneTeamMemberToGroupRequest(TeamMember.toAgentUser(teamMembers.head))
        val expectedAddRequest2 = AddOneTeamMemberToGroupRequest(TeamMember.toAgentUser(teamMembers.head))

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", submitUrl)
            .withFormUrlEncodedBody(
              "groups[0]" -> s"${GroupType.CUSTOM}_${groupSummaries(3).groupId}",
              "groups[1]" -> s"${GroupType.TAX_SERVICE}_${groupSummaries(4).groupId}",
              "submit"    -> CONTINUE_BUTTON
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        expectAddOneMemberToGroup(groupSummaries(3).groupId, expectedAddRequest1)
        expectAddOneMemberToTaxGroup(groupSummaries(4).groupId, expectedAddRequest2)
        expectPutSessionItem(GROUP_IDS_ADDED_TO, Seq(groupSummaries(3).groupId, groupSummaries(4).groupId))

        val result = controller.submitSelectGroupsForTeamMember(teamMember.id)(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get
          .shouldBe(ctrlRoute.showConfirmTeamMemberAddedToGroups(teamMember.id).url)

      }
    }

    "redirect to manage account if 'none of the above' is selected" in {
      // given
      AuthOkWithTeamMember()

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", submitUrl)
          .withFormUrlEncodedBody(
            "groups[0]" -> s"${AddGroupsToClientForm.NoneValue}",
            "submit"    -> CONTINUE_BUTTON
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitSelectGroupsForTeamMember(teamMember.id)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe controller.appConfig.agentServicesAccountManageAccountUrl
    }

    "display error when no groups are selected" in {
      // given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(GroupId.random(), s"Group $i", Some(i * 3), i * 4))
      val groupsAlreadyAssociatedToMember = groupSummaries.take(2)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", submitUrl)
          .withFormUrlEncodedBody("submit" -> CONTINUE_BUTTON)
          .withSession(SessionKeys.sessionId -> "session-x")

      AuthOkWithTeamMember()
      expectGetGroupsForArn(arn)(groupSummaries)
      expectGetGroupSummariesForTeamMember(arn)(teamMember)(groupsAlreadyAssociatedToMember)

      val result = controller.submitSelectGroupsForTeamMember(teamMember.id)(request)

      status(result) shouldBe OK
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Which access groups would you like to add John Smith 1 to? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Which access groups would you like to add John Smith 1 to?"
      html
        .select(Css.errorSummaryForField(groupSummaries(2).groupId.toString))
        .text() shouldBe "You must select at least one group"
      html.select(Css.errorForField("groups")).text() shouldBe "Error: You must select at least one group"
    }
  }

  s"GET ${ctrlRoute.showConfirmTeamMemberAddedToGroups(teamMember.id).url}" should {

    "render correctly the html" in {
      // given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(GroupId.random(), s"Group $i", Some(i * 3), i * 4))

      AuthOkWithTeamMember()
      expectGetSessionItem(GROUP_IDS_ADDED_TO, groupSummaries.take(2).map(_.groupId))
      expectGetGroupsForArn(arn)(groupSummaries)

      // when
      val result = controller.showConfirmTeamMemberAddedToGroups(teamMember.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Team member John Smith 1 added to access groups Group 1,Group 2 - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "John Smith 1 added to access groups"
      html.select(Css.H2).text() shouldBe "What happens next"
      html.select(Css.li("groups-added-to")).get(0).text shouldBe "Group 1"
      html.select(Css.li("groups-added-to")).get(1).text shouldBe "Group 2"
      html.select(Css.paragraphs).get(0).text() shouldBe "You have added John Smith 1 to the following groups:"
      html
        .select(Css.paragraphs)
        .get(1)
        .text()
        .shouldBe("John Smith 1 can now view and manage the tax affairs of the clients in these access groups.")
      html.select("a#back-to-manage").text() shouldBe "Return to manage team members page"

    }

  }

}
