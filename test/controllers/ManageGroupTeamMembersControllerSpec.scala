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
import helpers.Css._
import helpers.{BaseSpec, Css}
import models.TeamMember
import org.apache.commons.lang3.RandomStringUtils
import org.jsoup.Jsoup
import org.mongodb.scala.bson.ObjectId
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, await, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services.{GroupService, GroupServiceImpl}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

import java.time.LocalDate

class ManageGroupTeamMembersControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector
    : AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val groupService: GroupService = new GroupServiceImpl(mockAgentUserClientDetailsConnector, sessionCacheRepo, mockAgentPermissionsConnector)

  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)
  val groupId = "xyz"
  private val agentUser: AgentUser =
    AgentUser(RandomStringUtils.random(5), "Rob the Agent")
  val accessGroup: AccessGroup = AccessGroup(new ObjectId(),
                                arn,
                                "Bananas",
                                LocalDate.of(2020, 3, 10).atStartOfDay(),
                                null,
                                agentUser,
                                agentUser,
                                None,
                                None)

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction])
        .toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector))
      bind(classOf[AgentPermissionsConnector])
        .toInstance(mockAgentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepo)
      bind(classOf[GroupService]).toInstance(groupService)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .configure("mongodb.uri" -> mongoUri)
      .build()

  val agentUsers: Set[AgentUser] = (1 to 5).map(i => AgentUser(id = s"John $i", name = s"John $i name")).toSet

  val userDetails: Seq[UserDetails] = (1 to 5)
    .map { i =>
      UserDetails(
        Some(s"John $i"),
        Some("User"),
        Some(s"John $i name"),
        Some(s"john$i@abc.com")
      )
    }

  val teamMembers: Seq[TeamMember] = userDetails.map(TeamMember.fromUserDetails)

  val controller: ManageGroupTeamMembersController = fakeApplication.injector.instanceOf[ManageGroupTeamMembersController]

  s"GET ${routes.ManageGroupTeamMembersController.showExistingGroupTeamMembers(accessGroup._id.toString).url}" should {

    "render correctly the manage EXISTING TEAM MEMBERS page with no filters set" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, accessGroup.groupName))

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup.copy(teamMembers = Some(agentUsers))))
      expectGetTeamMembers(arn)(userDetails)

      //when
      val result = controller.showExistingGroupTeamMembers(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Manage team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Manage team members"

      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")

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
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, accessGroup.groupName))
      implicit val requestWithQueryParams = FakeRequest(GET,
        routes.ManageGroupTeamMembersController.showExistingGroupTeamMembers(accessGroup._id.toString).url +
          "?submit=filter&search=John+1"
      ).withHeaders("Authorization" -> "Bearer XYZ")
      .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup.copy(teamMembers = Some(agentUsers))))
      expectGetTeamMembers(arn)(userDetails)

      //when
      val result = controller.showExistingGroupTeamMembers(accessGroup._id.toString)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Filter results for 'John 1' Manage team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Manage team members"
      html.select(H2).text shouldBe "Filter results for 'John 1'"

      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")

      trs.size() shouldBe 1

      trs.get(0).select("td").get(0).text() shouldBe "John 1 name"
      trs.get(0).select("td").get(1).text() shouldBe "john1@abc.com"
      trs.get(0).select("td").get(2).text() shouldBe "Administrator"

    }

    "render with email searchTerm set" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, accessGroup.groupName))
      implicit val requestWithQueryParams = FakeRequest(GET,
        routes.ManageGroupTeamMembersController.showExistingGroupTeamMembers(accessGroup._id.toString).url +
          "?submit=filter&search=hn2@ab"
      ).withHeaders("Authorization" -> "Bearer XYZ")
      .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup.copy(teamMembers = Some(agentUsers))))
      expectGetTeamMembers(arn)(userDetails)

      //when
      val result = controller.showExistingGroupTeamMembers(accessGroup._id.toString)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Filter results for 'hn2@ab' Manage team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Manage team members"
      html.select(H2).text shouldBe "Filter results for 'hn2@ab'"

      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")

      trs.size() shouldBe 1

      trs.get(0).select("td").get(0).text() shouldBe "John 2 name"
      trs.get(0).select("td").get(1).text() shouldBe "john2@abc.com"
      trs.get(0).select("td").get(2).text() shouldBe "Administrator"
    }

    "render with filter that matches nothing" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_NAME, accessGroup.groupName))
      implicit val requestWithQueryParams = FakeRequest(GET,
        routes.ManageGroupTeamMembersController.showExistingGroupTeamMembers(accessGroup._id.toString).url +
          "?submit=filter&search=hn2@ab"
      ).withHeaders("Authorization" -> "Bearer XYZ")
        .withSession(SessionKeys.sessionId -> "session-x")

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup.copy(teamMembers = None)))
      expectGetTeamMembers(arn)(userDetails)

      //when
      val result = controller.showExistingGroupTeamMembers(accessGroup._id.toString)(requestWithQueryParams)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Filter results for 'hn2@ab' Manage team members - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Manage team members"

      val tableOfTeamMembers = html.select(Css.tableWithId("members"))
      tableOfTeamMembers.isEmpty shouldBe true
      val noClientsFound = html.select("div#members")
      noClientsFound.isEmpty shouldBe false
      noClientsFound.select("h2").text shouldBe "No team members found"
      noClientsFound.select("p").text shouldBe "Update your filters and try again or clear your filters to see all your team members"
    }
  }

  s"GET ${routes.ManageGroupTeamMembersController.showManageGroupTeamMembers(accessGroup._id.toString).url}" should {

    "render correctly the manage TEAM MEMBERS LIST page when no team members are in the group" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
      expectGetTeamMembers(arn)(userDetails)
      expectGetTeamMembers(arn)(userDetails)

      //when
      val result = controller.showManageGroupTeamMembers(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Update team members in this group - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Update team members in this group"

      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")

      trs.size() shouldBe 5

      trs.get(0).select("td").get(1).text() shouldBe "John 1 name"
      trs.get(0).select("td").get(2).text() shouldBe "john1@abc.com"
      trs.get(0).select("td").get(3).text() shouldBe "Administrator"

      trs.get(4).select("td").get(1).text() shouldBe "John 5 name"
      trs.get(4).select("td").get(2).text() shouldBe "john5@abc.com"
      trs.get(4).select("td").get(3).text() shouldBe "Administrator"

      html.select("p#member-count-text").text() shouldBe "Selected 0 team members of 5"
    }

    "render correctly the manage TEAM MEMBERS LIST page filtered results exist" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(FILTERED_TEAM_MEMBERS, teamMembers ))
      await(sessionCacheRepo.putSession(TEAM_MEMBER_SEARCH_INPUT, "John"))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
      expectGetTeamMembers(arn)(userDetails)
      expectGetTeamMembers(arn)(userDetails)

      //when
      val result = controller.showManageGroupTeamMembers(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Filter results for 'John' Update team members in this group - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Update team members in this group"

      html.select(H2).text() shouldBe "Filter results for 'John'"

      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")

      trs.size() shouldBe 5

      trs.get(0).select("td").get(1).text() shouldBe "John 1 name"
      trs.get(0).select("td").get(2).text() shouldBe "john1@abc.com"
      trs.get(0).select("td").get(3).text() shouldBe "Administrator"

      trs.get(4).select("td").get(1).text() shouldBe "John 5 name"
      trs.get(4).select("td").get(2).text() shouldBe "john5@abc.com"
      trs.get(4).select("td").get(3).text() shouldBe "Administrator"
    }

    "render correctly the manage TEAM MEMBERS LIST page" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup.copy(teamMembers = Some(Set(AgentUser("id1", "John"))))))
      expectGetTeamMembers(arn)(userDetails)
      expectGetTeamMembers(arn)(userDetails)

      //when
      val result = controller.showManageGroupTeamMembers(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Update team members in this group - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Update team members in this group"

      val trs =
        html.select(Css.tableWithId("sortable-table")).select("tbody tr")

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

  s"POST ${routes.ManageGroupTeamMembersController.submitManageGroupTeamMembers(accessGroup._id.toString).url}" should {

    "save selected team members to session" when {

      s"button is Continue and redirect to ${routes.ManageGroupTeamMembersController.showReviewSelectedTeamMembers(accessGroup._id.toString).url}" in {

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", routes.ManageGroupTeamMembersController.submitManageGroupTeamMembers(accessGroup._id.toString).url)
            .withFormUrlEncodedBody(
              "hasAlreadySelected" -> "false",
              "members[0]" -> teamMembers.head.id,
              "members[1]" -> teamMembers.last.id,
              "search" -> "",
              "submit" -> "continue"
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        await(sessionCacheRepo.putSession(FILTERED_TEAM_MEMBERS, teamMembers.take(1)))
        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
        expectGetTeamMembers(arn)(userDetails)


        val result =
          controller.submitManageGroupTeamMembers(accessGroup._id.toString)(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe
          routes.ManageGroupTeamMembersController.showReviewSelectedTeamMembers(accessGroup._id.toString).url

        await(sessionCacheRepo.getFromSession(FILTERED_TEAM_MEMBERS)) shouldBe Option.empty
        await(sessionCacheRepo.getFromSession(HIDDEN_TEAM_MEMBERS_EXIST)) shouldBe Option.empty
      }

      "button is Continue with no filters or hidden members AND none selected display error" in {
        // given
        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", routes.ManageGroupTeamMembersController.submitManageGroupTeamMembers(accessGroup._id.toString).url)
            .withFormUrlEncodedBody(
              "hasAlreadySelected" -> "false",
              "members" -> "",
              "search" -> "",
              "submit" -> "continue"
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
        expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
        expectGetTeamMembers(accessGroup.arn)(userDetails)

        // when
        val result = controller.submitManageGroupTeamMembers(accessGroup._id.toString)(request)

        status(result) shouldBe OK
        val html = Jsoup.parse(contentAsString(result))

        // then
        html.title() shouldBe "Error: Select team members - Agent services account - GOV.UK"
        html.select(Css.H1).text() shouldBe "Select team members"
        html
          .select(Css.errorSummaryForField("members"))
        await(sessionCacheRepo.getFromSession(SELECTED_TEAM_MEMBERS)).isDefined shouldBe false
      }

      "when filter button is pushed with no search value, no error will be shown" in {
        // given
        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", routes.ManageGroupTeamMembersController.submitManageGroupTeamMembers(accessGroup._id.toString).url)
            .withFormUrlEncodedBody(
              "hasAlreadySelected" -> "false",
              "members" -> "",
              "search" -> "",
              "submit" -> "filter"
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
        expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
        await(sessionCacheRepo.putSession(FILTERED_TEAM_MEMBERS, teamMembers))

        // when
        val result = controller.submitManageGroupTeamMembers(accessGroup._id.toString)(request)

        status(result) shouldBe SEE_OTHER

      }


      s"Filter clicked redirect to ${routes.ManageGroupTeamMembersController.showManageGroupTeamMembers(accessGroup._id.toString).url}" in {

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", routes.ManageGroupTeamMembersController.submitManageGroupTeamMembers(accessGroup._id.toString).url)
            .withFormUrlEncodedBody(
              "hasAlreadySelected" -> "false",
              "members" -> "",
              "search" -> "1",
              "submit" -> "filter"
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(allowed = true)
        expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))
        expectGetTeamMembers(arn)(userDetails)


        // when
        val result = controller.submitManageGroupTeamMembers(accessGroup._id.toString)(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe
          routes.ManageGroupTeamMembersController.showManageGroupTeamMembers(accessGroup._id.toString).url
      }
    }
  }

  s"GET ${routes.ManageGroupTeamMembersController.showReviewSelectedTeamMembers(accessGroup._id.toString).url}" should {


    "redirect if no team members selected in session" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      //when
      val result = controller.showReviewSelectedTeamMembers(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe
        routes.ManageGroupTeamMembersController.showManageGroupTeamMembers(accessGroup._id.toString).url
    }

    "render correctly the manage group REVIEW SELECTED page" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_TEAM_MEMBERS, teamMembers))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      //when
      val result = controller.showReviewSelectedTeamMembers(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Review selected team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 5 team members"
      html.select(Css.tableWithId("sortable-table")).select("tbody tr").size() shouldBe 5
      html.select("form .govuk-fieldset__legend").text() shouldBe "Do you need to add or remove selected team members?"
      val answerRadios = html.select(Css.radioButtonsField("answer-radios"))
      answerRadios
        .select("label[for=answer]")
        .text() shouldBe "Yes, add or remove team members"
      answerRadios
        .select("label[for=answer-no]")
        .text() shouldBe "No, continue to next section"
      html.select(Css.submitButton).text() shouldBe "Save and continue"

    }
  }

  s"POST ${routes.ManageGroupTeamMembersController.submitReviewSelectedTeamMembers(accessGroup._id.toString).url}" should {

    s"redirect to '${routes.ManageGroupTeamMembersController.showGroupTeamMembersUpdatedConfirmation(accessGroup._id.toString)}' page with answer 'false'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers(accessGroup._id.toString)}")
          .withFormUrlEncodedBody("answer" -> "false")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(SELECTED_TEAM_MEMBERS, teamMembers))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      val result = controller.submitReviewSelectedTeamMembers(accessGroup._id.toString)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.ManageGroupTeamMembersController
        .showGroupTeamMembersUpdatedConfirmation(accessGroup._id.toString).url
    }

    s"redirect to '${routes.ManageGroupTeamMembersController.showManageGroupTeamMembers(accessGroup._id.toString)}'" +
      s" page with answer 'true'" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers(accessGroup._id.toString)}")
          .withFormUrlEncodedBody("answer" -> "true")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(SELECTED_TEAM_MEMBERS, teamMembers))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      val result = controller.submitReviewSelectedTeamMembers(accessGroup._id.toString)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe routes.ManageGroupTeamMembersController
        .showManageGroupTeamMembers(accessGroup._id.toString).url
    }

    s"render errors when no radio button selected" in {

      implicit val request =
        FakeRequest(
          "POST",
          s"${controller.submitReviewSelectedTeamMembers(accessGroup._id.toString)}")
          .withFormUrlEncodedBody("NOTHING" -> "SELECTED")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(SELECTED_TEAM_MEMBERS, teamMembers))
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      val result = controller.submitReviewSelectedTeamMembers(accessGroup._id.toString)(request)

      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Review selected team members - Agent services account - GOV.UK"
      html.select(H1).text() shouldBe "You have selected 5 team members"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you need to add or remove selected team members"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you need to add or remove selected team members"

    }
  }

  s"GET ${routes.ManageGroupTeamMembersController.showGroupTeamMembersUpdatedConfirmation(accessGroup._id.toString).url}" should {

    "redirect if no team members selected in session" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      //when
      val result = controller.showGroupTeamMembersUpdatedConfirmation(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe
        routes.ManageGroupTeamMembersController.showManageGroupTeamMembers(accessGroup._id.toString).url
    }

    "render correctly the manage TEAM MEMBERS UPDATED page" in {
      //given
      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(SELECTED_TEAM_MEMBERS, teamMembers))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(allowed = true)
      expectGetGroupSuccess(accessGroup._id.toString, Some(accessGroup))

      //when
      val result = controller.showGroupTeamMembersUpdatedConfirmation(accessGroup._id.toString)(request)

      //then
      status(result) shouldBe OK
      // selected team members should be cleared from session
      await(sessionCacheRepo.getFromSession(SELECTED_TEAM_MEMBERS)) shouldBe None

      val html = Jsoup.parse(contentAsString(result))
      html.title shouldBe "Bananas access group team members updated - Agent services account - GOV.UK"
      html
        .select(Css.confirmationPanelH1)
        .text() shouldBe "Bananas access group team members updated"
      html
        .select("a#returnToDashboard")
        .text() shouldBe "Return to manage access groups"
      html
        .select("a#returnToDashboard")
        .attr("href") shouldBe routes.ManageGroupController.showManageGroups.url
      html.select(Css.backLink).size() shouldBe 0
    }

  }

}
