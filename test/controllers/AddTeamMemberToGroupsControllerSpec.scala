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

package controllers

import com.google.inject.AbstractModule
import connectors.{AddMembersToAccessGroupRequest, AgentPermissionsConnector, AgentUserClientDetailsConnector, GroupSummary}
import controllers.actions.AuthAction
import helpers.{BaseSpec, Css}
import models.TeamMember
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, redirectLocation}
import services.{GroupService, SessionCacheService, TeamMemberService}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}

import scala.concurrent.{ExecutionContext, Future}

class AddTeamMemberToGroupsControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserMemberDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]
  implicit val mockTeamMemberService: TeamMemberService = mock[TeamMemberService]
  implicit val mockSessionCacheService: SessionCacheService = mock[SessionCacheService]

  override def moduleWithOverrides = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserMemberDetailsConnector)
      bind(classOf[GroupService]).toInstance(mockGroupService)
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
  val teamMember = teamMembers(0)

  s"GET ${ctrlRoute.showSelectGroupsForTeamMember(teamMember.id).url}" should {

    "render correctly the html" in {
      //given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(s"groupId$i", s"Group $i", i * 3, i * 4))
      val groupsAlreadyAssociatedToMember = groupSummaries.take(2)

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectLookupTeamMember(arn)(teamMember)
      expectDeleteSessionItem(GROUP_IDS_ADDED_TO)
      expectGetGroupsForArn(arn)(groupSummaries)
      expectGetGroupSummariesForTeamMember(arn)(teamMember)(groupsAlreadyAssociatedToMember)

      //when
      val result = controller.showSelectGroupsForTeamMember(teamMember.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Which access groups would you like to add John Smith 1 to? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Which access groups would you like to add John Smith 1 to?"
      html.select(Css.paragraphs).get(0).text() shouldBe "Team member is currently in these access groups:"
      html.select(Css.li("already-in-groups")).get(0).text() shouldBe "Group 1"
      html.select(Css.li("already-in-groups")).get(1).text() shouldBe "Group 2"
      val form = html.select(Css.form)
      form.attr("action").shouldBe(submitUrl)

      val fieldset = form.select("fieldset.govuk-fieldset")
      fieldset.isEmpty shouldBe false // <-- fieldset needed for a11y

      val checkboxes = fieldset.select(".govuk-checkboxes#groups input[name=groups[]]")
      checkboxes size() shouldBe 3
      val checkboxLabels = form.select("label.govuk-checkboxes__label")
      checkboxLabels.get(0).text() shouldBe "Group 3"
      checkboxLabels.get(1).text() shouldBe "Group 4"
      checkboxLabels.get(2).text() shouldBe "Group 5"
      html.select(Css.linkStyledAsButton).text() shouldBe "Cancel"
      html.select(Css.linkStyledAsButton).hasClass("govuk-button--secondary")
      html.select(Css.linkStyledAsButton).hasClass("govuk-!-margin-right-3")
      html.select(Css.submitButton).text() shouldBe "Save and continue"

      html.select(".hmrc-report-technical-issue").text() shouldBe "Is this page not working properly? (opens in new tab)"
      html.select(".hmrc-report-technical-issue").attr("href") startsWith "http://localhost:9250/contact/report-technical-problem?newTab=true&service=AOSS"

    }

    "render correctly when member is not in any groups yet" in {
      //given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(s"groupId$i", s"Group $i", i * 3, i * 4))
      val groupsAlreadyAssociatedToMember = Seq.empty

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectDeleteSessionItem(GROUP_IDS_ADDED_TO)
      expectLookupTeamMember(arn)(teamMember)
      expectGetGroupsForArn(arn)(groupSummaries)
      expectGetGroupSummariesForTeamMember(arn)(teamMember)(groupsAlreadyAssociatedToMember)


      //when
      val result = controller.showSelectGroupsForTeamMember(teamMember.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Which access groups would you like to add John Smith 1 to? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Which access groups would you like to add John Smith 1 to?"
      html.select(Css.paragraphs).get(0).text() shouldBe "Team member is currently not in any access groups"
      html.select(Css.li("already-in-groups")).isEmpty shouldBe true
      val form = html.select(Css.form)
      form.attr("action").shouldBe(submitUrl)
      val checkboxes = form.select(".govuk-checkboxes#groups input[name=groups[]]")
      checkboxes size() shouldBe 5
      val checkboxLabels = form.select("label.govuk-checkboxes__label")
      checkboxLabels.get(0).text() shouldBe "Group 1"
      checkboxLabels.get(1).text() shouldBe "Group 2"
      checkboxLabels.get(2).text() shouldBe "Group 3"
      checkboxLabels.get(4).text() shouldBe "Group 5"
      html.select(Css.linkStyledAsButton).text() shouldBe "Cancel"
      html.select(Css.linkStyledAsButton).hasClass("govuk-button--secondary")
      html.select(Css.linkStyledAsButton).hasClass("govuk-!-margin-right-3")
      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }

    "render correctly when no available groups" in {
      //given
      val groupSummaries = (1 to 2)
        .map(i => GroupSummary(s"groupId$i", s"Group $i", i * 3, i * 4))
      val groupsAlreadyAssociatedToMember = groupSummaries

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectDeleteSessionItem(GROUP_IDS_ADDED_TO)
      expectLookupTeamMember(arn)(teamMember)
      expectGetGroupsForArn(arn)(groupSummaries)
      expectGetGroupSummariesForTeamMember(arn)(teamMember)(groupsAlreadyAssociatedToMember)

      //when
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

  private val submitUrl: String = ctrlRoute.submitSelectGroupsForTeamMember(teamMember.id).url

  s"POST to $submitUrl" should {

    "add team member to the selected groups and redirect" when {

      s"At least 1 checkbox is checked for the group to add to" in {
        //given
        val groupSummaries = (1 to 5)
          .map(i => GroupSummary(s"groupId$i", s"Group $i", i * 3, i * 4))

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(true)

        (mockTeamMemberService
          .lookupTeamMember(_: Arn)(_: String)(_: HeaderCarrier, _: ExecutionContext))
          .expects(arn, teamMember.id, *, *)
          .returning(Future successful Some(teamMember))

        val expectedAddRequest1 = AddMembersToAccessGroupRequest(
          teamMembers = Some(Set(TeamMember.toAgentUser(teamMember)))
        )
        val expectedAddRequest2 = AddMembersToAccessGroupRequest(
          teamMembers = Some(Set(TeamMember.toAgentUser(teamMember)))
        )

        expectAddMembersToGroup(groupSummaries(3).groupId, expectedAddRequest1)
        expectAddMembersToGroup(groupSummaries(4).groupId, expectedAddRequest2)

        implicit val request =
          FakeRequest("POST", submitUrl)
            .withFormUrlEncodedBody(
              "groups[0]" -> groupSummaries(3).groupId,
              "groups[1]" -> groupSummaries(4).groupId,
              "submit" -> CONTINUE_BUTTON
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
        expectPutSessionItem(
          GROUP_IDS_ADDED_TO,
          Seq(groupSummaries(3).groupId, groupSummaries(4).groupId)
        )

        val result = controller.submitSelectGroupsForTeamMember(teamMember.id)(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get
          .shouldBe(ctrlRoute.showConfirmTeamMemberAddedToGroups(teamMember.id).url)

      }
    }

    "display error when no groups are selected" in {
      //given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(s"groupId$i", s"Group $i", i * 3, i * 4))
      val groupsAlreadyAssociatedToMember = groupSummaries.take(2)

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectLookupTeamMember(arn)(teamMember)
      expectGetGroupsForArn(arn)(groupSummaries)
      expectGetGroupSummariesForTeamMember(arn)(teamMember)(groupsAlreadyAssociatedToMember)


      implicit val request =
        FakeRequest("POST", submitUrl)
          .withFormUrlEncodedBody("submit" -> CONTINUE_BUTTON)
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)

      val result = controller.submitSelectGroupsForTeamMember(teamMember.id)(request)

      status(result) shouldBe OK
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Which access groups would you like to add John Smith 1 to? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Which access groups would you like to add John Smith 1 to?"
      html.select(Css.errorSummaryForField("groupId3")).text() shouldBe "You must select at least one group"
      html.select(Css.errorForField("groups")).text() shouldBe "Error: You must select at least one group"
    }
  }

  s"GET ${ctrlRoute.showConfirmTeamMemberAddedToGroups(teamMember.id).url}" should {

    "render correctly the html" in {
      //given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(s"groupId$i", s"Group $i", i * 3, i * 4))

      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_IDS_ADDED_TO, groupSummaries.take(2).map(_.groupId))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)
      expectLookupTeamMember(arn)(teamMember)
      expectGetGroupsForArn(arn)(groupSummaries)

      //when
      val result = controller.showConfirmTeamMemberAddedToGroups(teamMember.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Team member John Smith 1 added to access groups Group 1,Group 2 - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Team member added to access groups"
      html.select(Css.H2).text() shouldBe "What happens next"
      html.select(Css.li("groups-added-to")).get(0).text shouldBe "Group 1"
      html.select(Css.li("groups-added-to")).get(1).text shouldBe "Group 2"
      html.select(Css.paragraphs).get(0).text() shouldBe "You have added John Smith 1 to the following groups:"
      html.select(Css.paragraphs).get(1).text()
        .shouldBe("John Smith 1 can now view and manage the tax affairs of the clients in these access groups.")
      html.select("a#back-to-manage").text() shouldBe "Back to manage team members page"

    }

  }

}
