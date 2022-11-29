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
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.actions.AuthAction
import helpers.Css.{H1, H2, paragraphs}
import helpers.{BaseSpec, Css}
import models.{AddTeamMembersToGroup, TeamMember}
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, redirectLocation}
import play.api.test.{FakeRequest, Helpers}
import repository.SessionCacheRepository
import services.{ClientService, GroupService, SessionCacheService, TeamMemberService}
import uk.gov.hmrc.agentmtdidentifiers.model.{OptedInReady, UserDetails}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

class CreateGroupSelectTeamMembersControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]
  implicit val mockClientService: ClientService = mock[ClientService]
  implicit val mockTeamService: TeamMemberService = mock[TeamMemberService]
  implicit val mockSessionCacheService: SessionCacheService = mock[SessionCacheService]
  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)
  private val groupName = "XYZ"

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[ClientService]).toInstance(mockClientService)
      bind(classOf[TeamMemberService]).toInstance(mockTeamService)
      bind(classOf[GroupService]).toInstance(mockGroupService)
      bind(classOf[SessionCacheService]).toInstance(mockSessionCacheService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder.configure("mongodb.uri" -> mongoUri).build()
    
  val controller: CreateGroupSelectTeamMembersController = fakeApplication.injector.instanceOf[CreateGroupSelectTeamMembersController]

  val users: Seq[UserDetails] = (1 to 11)
    .map { i =>
      UserDetails(
        Some(s"John $i"),
        Some("User"),
        Some("John"),
        Some(s"john$i@abc.com")
      )
    }

  val teamMembers: Seq[TeamMember] = users.map(TeamMember.fromUserDetails)

  val teamMembersIds: Seq[String] = teamMembers.map(_.id)

  private val ctrlRoute: ReverseCreateGroupSelectTeamMembersController = routes.CreateGroupSelectTeamMembersController

  s"GET ${ctrlRoute.showSelectTeamMembers(None, None).url}" should {

    val fakeTeamMembers = (1 to 10)
      .map { i =>
        UserDetails(
          Some(s"John $i"),
          Some("User"),
          Some("John"),
          Some(s"john$i@abc.com")
        )
      }

    val teamMembers = fakeTeamMembers.map(TeamMember.fromUserDetails)

    "render team members when filter is not applied" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetSessionItemNone(TEAM_MEMBER_SEARCH_INPUT)
      expectGetSessionItemNone(RETURN_URL)
      expectGetPageOfTeamMembers(arn)(teamMembers)

      val result = controller.showSelectTeamMembers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"
      html
        .select(Css.backLink)
        .attr("href") shouldBe routes.CreateGroupSelectClientsController.showReviewSelectedClients(None, None).url

      val th = html.select(Css.tableWithId("multi-select-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Name"
      th.get(2).text() shouldBe "Email"
      th.get(3).text() shouldBe "Role"
      val trs =
        html.select(Css.tableWithId("multi-select-table")).select("tbody tr")
      trs.size() shouldBe 10
      // first row
      trs.get(0).select("td").get(1).text() shouldBe "John"
      trs.get(0).select("td").get(2).text() shouldBe "john1@abc.com"
      trs.get(0).select("td").get(3).text() shouldBe "Administrator"

      // last row
      trs.get(9).select("td").get(1).text() shouldBe "John"
      trs.get(9).select("td").get(2).text() shouldBe "john10@abc.com"
      trs.get(9).select("td").get(3).text() shouldBe "Administrator"
      html.select(Css.submitButton).text() shouldBe "Continue"
    }

    "render with filtered team members held in session when a filter was applied" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      expectGetSessionItem(TEAM_MEMBER_SEARCH_INPUT, "John")
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetSessionItemNone(RETURN_URL)
      expectGetPageOfTeamMembers(arn)(teamMembers)

      val result = controller.showSelectTeamMembers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Filter results for 'John' Select team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"

      html.select(H2).text() shouldBe "Filter results for 'John'"

      val th = html.select(Css.tableWithId("multi-select-table")).select("thead th")
      th.size() shouldBe 4
      th.get(1).text() shouldBe "Name"
      th.get(2).text() shouldBe "Email"
      th.get(3).text() shouldBe "Role"

      val trs = html.select(Css.tableWithId("multi-select-table")).select("tbody tr")

      trs.size() shouldBe 10
      // first row
      trs.get(0).select("td").get(1).text() shouldBe "John"
      trs.get(0).select("td").get(2).text() shouldBe "john1@abc.com"
      trs.get(0).select("td").get(3).text() shouldBe "Administrator"
      // last row
      trs.get(4).select("td").get(1).text() shouldBe "John"
      trs.get(4).select("td").get(2).text() shouldBe "john5@abc.com"
      trs.get(4).select("td").get(3).text() shouldBe "Administrator"
    }

    "render with NO Team Members" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItemNone(TEAM_MEMBER_SEARCH_INPUT)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetSessionItemNone(RETURN_URL)
      expectGetPageOfTeamMembers(arn)(Seq.empty) // <- no team members returned from session

      val result = controller.showSelectTeamMembers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"

      // No table
      val th = html.select(Css.tableWithId("multi-select-table")).select("thead th")
      th.size() shouldBe 0
      val trs =
        html.select(Css.tableWithId("multi-select-table")).select("tbody tr")
      trs.size() shouldBe 0

      // Not found content
      html.select(Css.H2).text() shouldBe "No team members found"
      html.select(paragraphs).get(1).text() shouldBe "Update your filters and try again or clear your filters to see all your team members"

    }

    "render correct back link when coming from check you answers page" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItemNone(TEAM_MEMBER_SEARCH_INPUT)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetPageOfTeamMembers(arn)(teamMembers)
      // <-- we expect RETURN_URL to be the backLink url
      expectGetSessionItem(RETURN_URL, routes.CreateGroupController.showCheckYourAnswers.url)

      val result = controller.showSelectTeamMembers()(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Select team members - Agent services account - GOV.UK"
      html.select(Css.backLink).attr("href") shouldBe routes.CreateGroupController.showCheckYourAnswers.url

    }

    "redirect when no group name is in session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME) // <- NO GROUP NAME IN SESSION

      val result = controller.showSelectTeamMembers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.CreateGroupController.showGroupName.url
    }
  }

  s"POST to ${ctrlRoute.submitSelectedTeamMembers.url}" should {

    "save selected team members to session" when {

      s"button is Continue and redirect to ${ctrlRoute.showReviewSelectedTeamMembers(None, None).url}" in {

        implicit val request = FakeRequest("POST",
            ctrlRoute.submitSelectedTeamMembers.url)
            .withSession(SessionKeys.sessionId -> "session-x")
            .withFormUrlEncodedBody(
              "members[]" -> teamMembersIds.head,
              "members[]" -> teamMembersIds.last,
              "submit" -> CONTINUE_BUTTON
            )
        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
        expectGetSessionItem(GROUP_NAME, "XYZ")
        expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty) // with no preselected
        val formData = AddTeamMembersToGroup(
          members = Some(List(teamMembersIds.head, teamMembersIds.last)),
          submit = CONTINUE_BUTTON
        )
        expectSavePageOfTeamMembers(formData, teamMembers)

        val result = controller.submitSelectedTeamMembers()(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe ctrlRoute.showReviewSelectedTeamMembers(None, None).url
      }

      s"button is Filter and redirect to ${ctrlRoute.showSelectTeamMembers(None, None).url}" in {

        implicit val request =
          FakeRequest("POST",
            ctrlRoute.submitSelectedTeamMembers.url)
            .withSession(SessionKeys.sessionId -> "session-x")
            .withFormUrlEncodedBody(
              "members[]" -> teamMembersIds.head,
              "members[]" -> teamMembersIds.last,
              "search" -> "10",
              "submit" -> FILTER_BUTTON
            )

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
        expectGetSessionItem(GROUP_NAME, "XYZ")
        expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty)
        val form = AddTeamMembersToGroup(
          search = Some("10"),
          members = Some(List(teamMembersIds.head, teamMembersIds.last)),
          submit = FILTER_BUTTON
        )
        expectSavePageOfTeamMembers(form, teamMembers)

        val result = controller.submitSelectedTeamMembers()(request)

        status(await(result)) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe ctrlRoute.showSelectTeamMembers(None, None).url

      }


    }

    "display error when button is Continue, no team members were selected" in {

      // given
      implicit val request = FakeRequest("POST", ctrlRoute.submitSelectedTeamMembers.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody(
          "members" -> "",
          "search" -> "",
          "submit" -> CONTINUE_BUTTON
        )

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, "XYZ")
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, Seq.empty)
      expectGetSessionItem(RETURN_URL, routes.CreateGroupSelectClientsController.showReviewSelectedClients(None, None).url)
      expectGetPageOfTeamMembers(arn)(teamMembers)

      // when
      val result = controller.submitSelectedTeamMembers()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Select team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"
      html.select(Css.errorSummaryLinkWithHref("#members")).text() shouldBe "You must select at least one team member"

    }

    "display error when button is Continue and DESELECTION mean that nothing is now selected" in {
      // given
      implicit val request = FakeRequest("POST", ctrlRoute.submitSelectedTeamMembers.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody(
          "members" -> "",
          "search" -> "",
          "submit" -> CONTINUE_BUTTON
        )

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, "XYZ")
      //this currently selected team member will be unselected as part of the post
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers.take(1))
      expectGetSessionItem(RETURN_URL, routes.CreateGroupSelectClientsController.showReviewSelectedClients(None, None).url)
      val emptyForm = AddTeamMembersToGroup(submit = CONTINUE_BUTTON)
      //now no selected members
      expectSavePageOfTeamMembers(emptyForm, Seq.empty[TeamMember])
      expectGetPageOfTeamMembers(arn)(teamMembers)

      // when
      val result = controller.submitSelectedTeamMembers()(request)

      // then
      status(result) shouldBe OK

      //and
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Select team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Select team members"
      html.select(Css.errorSummaryLinkWithHref("#members")).text() shouldBe "You must select at least one team member"

    }

    "not show any errors when button is Filter and no filter term was provided" in {

      // given
      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest("POST", ctrlRoute.submitSelectedTeamMembers.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody(
          "members" -> "",
          "search" -> "",
          "submit" -> FILTER_BUTTON
        )

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, "XYZ")
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      val form = AddTeamMembersToGroup(submit = FILTER_BUTTON)
      expectSavePageOfTeamMembers(form, teamMembers)

      // when
      val result = controller.submitSelectedTeamMembers()(request)

      // then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(ctrlRoute.showSelectTeamMembers(None, None).url)
    }

    "redirect to createGroup when POSTED without groupName in Session" in {

      // given
      implicit val request = FakeRequest(
        "POST",
        ctrlRoute.submitSelectedTeamMembers.url
      ).withFormUrlEncodedBody("submit" -> CONTINUE_BUTTON)
        .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME)

      // when
      val result = controller.submitSelectedTeamMembers()(request)

      // then
      status(result) shouldBe SEE_OTHER
      Helpers.redirectLocation(result) shouldBe Some(routes.CreateGroupController.showGroupName.url)
    }
  }

  s"GET ${ctrlRoute.showReviewSelectedTeamMembers(None, None).url}" should {

    "render with selected team members" in {

      val selectedTeamMembers = (1 to 5)
        .map { i =>
          TeamMember(
            s"team member $i",
            s"x$i@xyz.com",
            Some(s"1234 $i"),
            Some("User"),
            selected = true
          )
        }

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetSessionItem(SELECTED_TEAM_MEMBERS, selectedTeamMembers)

      val result = controller.showReviewSelectedTeamMembers(None, None)(request)

      status(result) shouldBe OK

      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe s"Review selected team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe s"You have selected 5 team members"
      html.select(Css.backLink).attr("href") shouldBe ctrlRoute.showSelectTeamMembers(None, None).url

      val table = html.select(Css.tableWithId("selected-team-members"))
      val th = table.select("thead th")
      th.size() shouldBe 3
      th.get(0).text() shouldBe "Name"
      th.get(1).text() shouldBe "Email"
      th.get(2).text() shouldBe "Role"
      val trs = table.select("tbody tr")
      trs.size() shouldBe 5
      // first row
      trs.get(0).select("td").get(0).text() shouldBe "team member 1"
      trs.get(0).select("td").get(1).text() shouldBe "x1@xyz.com"
      trs.get(0).select("td").get(2).text() shouldBe "Administrator"

      // last row
      trs.get(4).select("td").get(0).text() shouldBe "team member 5"
      trs.get(4).select("td").get(1).text() shouldBe "x5@xyz.com"
      trs.get(4).select("td").get(2).text() shouldBe "Administrator"

      val answerRadios = html.select(Css.radioButtonsField("answer-radios"))
      answerRadios.select("label[for=answer]").text() shouldBe "Yes, add or remove team members"
      answerRadios.select("label[for=answer-no]").text() shouldBe "No, continue to next section"
    }

    "redirect when no selected team members in session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectGetSessionItemNone(SELECTED_TEAM_MEMBERS)

      val result = controller.showReviewSelectedTeamMembers(None, None)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSelectTeamMembers(None, None).url

    }

    "redirect when no group name is in session" in {

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItemNone(GROUP_NAME) // <- NO GROUP NAME IN SESSION

      val result = controller.showReviewSelectedTeamMembers(None, None)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.CreateGroupController.showGroupName.url
    }
  }

  s"POST ${routes.CreateGroupController.submitReviewSelectedTeamMembers.url}" should {

    s"redirect to '${routes.CreateGroupController.showCheckYourAnswers.url}' page with answer 'false'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers()}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      val result = controller.submitReviewSelectedTeamMembers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.CreateGroupController.showCheckYourAnswers.url
    }

    s"redirect to '${ctrlRoute.showSelectTeamMembers(None, None).url}' page with answer 'true'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers()}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      val result = controller.submitReviewSelectedTeamMembers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSelectTeamMembers(None, None).url
    }

    s"redirect to '${ctrlRoute.showSelectTeamMembers(None, None).url}' with no SELECTED in session" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers()}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItemNone(SELECTED_TEAM_MEMBERS)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      val result = controller.submitReviewSelectedTeamMembers()(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe ctrlRoute.showSelectTeamMembers(None, None).url
    }

    s"render errors when no radio button selected" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers()}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      expectGetSessionItem(SELECTED_TEAM_MEMBERS, teamMembers)
      expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
      expectGetSessionItem(GROUP_NAME, groupName)
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)

      val result = controller.submitReviewSelectedTeamMembers()(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Review selected team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 11 team members"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you need to add or remove selected team members"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you need to add or remove selected team members"

    }
  }

}
