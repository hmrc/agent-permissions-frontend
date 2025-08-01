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
import connectors.{AddMembersToAccessGroupRequest, AgentAssuranceConnector, AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.actions.AuthAction
import forms.AddGroupsToClientForm
import helpers.{BaseSpec, Css}
import models.{DisplayClient, GroupId}
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, redirectLocation}
import services.{ClientService, GroupService, SessionCacheService}
import uk.gov.hmrc.agents.accessgroups.{Client, GroupSummary}
import uk.gov.hmrc.agents.accessgroups.optin._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

class AddClientToGroupsControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector =
    mock[AgentUserClientDetailsConnector]
  implicit lazy val mockAgentAssuranceConnector: AgentAssuranceConnector =
    mock[AgentAssuranceConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]
  implicit val mockClientService: ClientService = mock[ClientService]
  implicit val mockSessionCacheService: SessionCacheService = mock[SessionCacheService]

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(
        new AuthAction(
          mockAuthConnector,
          env,
          conf,
          mockAgentPermissionsConnector,
          mockAgentAssuranceConnector,
          mockSessionCacheService
        )
      )
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[GroupService]).toInstance(mockGroupService)
      bind(classOf[ClientService]).toInstance(mockClientService)
      bind(classOf[SessionCacheService]).toInstance(mockSessionCacheService)
    }
  }

  private val controller = fakeApplication.injector.instanceOf[AddClientToGroupsController]

  override implicit lazy val fakeApplication: Application =
    appBuilder.configure("mongodb.uri" -> mongoUri).build()

  val fakeClients: Seq[Client] =
    List.tabulate(3)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"Client $i"))

  val displayClients: Seq[DisplayClient] = fakeClients.map(DisplayClient.fromClient(_))
  private val client: DisplayClient = displayClients.head
  private val ctrlRoute: ReverseAddClientToGroupsController = routes.AddClientToGroupsController
  private val submitUrl: String = routes.AddClientToGroupsController.submitSelectGroupsForClient(client.id).url

  def AuthOkWithClient(client: DisplayClient = client): Unit = {
    expectAuthorisationGrantsAccess(mockedAuthResponse)
    expectGetSessionItem(SUSPENSION_STATUS, false)
    expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
    expectIsArnAllowed(allowed = true)
    expectLookupClient(arn)(client)
  }

  s"GET ${ctrlRoute.showSelectGroupsForClient(client.id).url}" should {

    "render correctly the html" in {
      // given
      val groupSummaries = (1 to 5).map(i => GroupSummary(GroupId.random(), s"Group $i", Some(i * 3), i * 4)) ++ Seq(
        GroupSummary(GroupId.random(), "VAT", None, 8, Some("HMRC-MTD-VAT"))
      )
      val groupsAlreadyAssociatedToClient = groupSummaries.take(2)

      AuthOkWithClient()
      expectDeleteSessionItem(GROUP_IDS_ADDED_TO)
      expectGetGroupsForArn(arn)(groupSummaries)
      expectGetGroupSummariesForClient(arn)(client)(groupsAlreadyAssociatedToClient)

      // when
      val result = controller.showSelectGroupsForClient(client.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Which custom access groups would you like to add Client 0 to? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Which custom access groups would you like to add Client 0 to?"
      html.select(Css.paragraphs).get(0).text() shouldBe "Client is currently in these access groups:"
      html.select(Css.li("already-in-groups")).get(0).text() shouldBe "Group 1"
      html.select(Css.li("already-in-groups")).get(1).text() shouldBe "Group 2"
      val form = html.select(Css.form)
      form.attr("action").shouldBe(submitUrl)

      val fieldset = form.select("fieldset.govuk-fieldset") // fieldset must exist and have a legend
      fieldset.select(Css.legend).text() shouldBe "Which custom access groups would you like to add Client 0 to?"
      fieldset
        .select("#groups-hint")
        .text() shouldBe "You can only add clients to custom groups manually. Select all that apply."
      val checkboxes = fieldset.select(".govuk-checkboxes#groups input[name=groups[]]")
      checkboxes.size() shouldBe 4
      val checkboxLabels = form.select("label.govuk-checkboxes__label")
      checkboxLabels.get(0).text() shouldBe "Group 3"
      checkboxLabels.get(1).text() shouldBe "Group 4"
      checkboxLabels.get(2).text() shouldBe "Group 5"
      checkboxLabels.get(3).text() shouldBe "No access groups"
      form.select("#__none__-item-hint").get(0).text() shouldBe "This will return you to the Manage account page"
      html.select(Css.linkStyledAsButton).text() shouldBe "Cancel"
      html.select(Css.linkStyledAsButton).hasClass("govuk-button--secondary")
      html.select(Css.linkStyledAsButton).hasClass("govuk-!-margin-right-3")
      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }

    "render correctly when client not in any groups yet" in {
      // given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(GroupId.random(), s"Group $i", Some(i * 3), i * 4))
      val groupsAlreadyAssociatedToClient = Seq.empty

      AuthOkWithClient()
      expectGetGroupsForArn(arn)(groupSummaries)
      expectDeleteSessionItem(GROUP_IDS_ADDED_TO)
      expectGetGroupSummariesForClient(arn)(client)(groupsAlreadyAssociatedToClient)

      // when
      val result = controller.showSelectGroupsForClient(client.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Which custom access groups would you like to add Client 0 to? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Which custom access groups would you like to add Client 0 to?"
      html.select(Css.paragraphs).get(0).text() shouldBe "Client is currently not in any access groups"
      html.select(Css.li("already-in-groups")).isEmpty shouldBe true
      val form = html.select(Css.form)
      form.attr("action").shouldBe(submitUrl)

      val fieldset = form.select("fieldset.govuk-fieldset") // fieldset must exist and have a legend
      fieldset.size() shouldBe 1
      fieldset.select(Css.legend).text() shouldBe "Which custom access groups would you like to add Client 0 to?"
      val checkboxes = fieldset.select(".govuk-checkboxes#groups input[name=groups[]]")
      checkboxes.size() shouldBe 6
      val checkboxLabels = form.select("label.govuk-checkboxes__label")
      checkboxLabels.get(0).text() shouldBe "Group 1"
      checkboxLabels.get(1).text() shouldBe "Group 2"
      checkboxLabels.get(2).text() shouldBe "Group 3"
      checkboxLabels.get(5).text() shouldBe "No access groups"
      form.select("#__none__-item-hint").get(0).text() shouldBe "This will return you to the Manage account page"
      html.select(Css.linkStyledAsButton).text() shouldBe "Cancel"
      html.select(Css.linkStyledAsButton).hasClass("govuk-button--secondary")
      html.select(Css.linkStyledAsButton).hasClass("govuk-!-margin-right-3")
      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }

    "render correctly when no available groups" in {
      // given
      val groupSummaries = (1 to 2)
        .map(i => GroupSummary(GroupId.random(), s"Group $i", Some(i * 3), i * 4))
      val groupsAlreadyAssociatedToClient = groupSummaries

      AuthOkWithClient()
      expectDeleteSessionItem(GROUP_IDS_ADDED_TO)
      expectGetGroupsForArn(arn)(groupSummaries)
      expectGetGroupSummariesForClient(arn)(client)(groupsAlreadyAssociatedToClient)

      // when
      val result = controller.showSelectGroupsForClient(client.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "There are no available groups to add Client 0 to - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "There are no available groups to add Client 0 to"
      html.select(Css.paragraphs).get(0).text() shouldBe "Client is currently in these access groups:"
      html.select(Css.li("already-in-groups")).get(0).text() shouldBe "Group 1"
      html.select(Css.li("already-in-groups")).get(1).text() shouldBe "Group 2"
      val form = html.select(Css.form)
      form.size() shouldBe 0
      val backLink = html.select("main a#back-to-client-details")
      backLink.text() shouldBe "Back to Client 0"
      backLink.attr("href") shouldBe routes.ManageClientController.showClientDetails(client.id).url
    }

  }

  s"POST to $submitUrl" should {

    "add client to the selected groups and redirect" when {

      s"At least 1 checkbox is checked for the group to add to" in {
        // given
        val groupSummaries = (1 to 5)
          .map(i => GroupSummary(GroupId.random(), s"Group $i", Some(i * 3), i * 4))

        val expectedAddRequest1 =
          AddMembersToAccessGroupRequest(clients = Some(Set(Client(client.enrolmentKey, client.name))))
        expectAddMembersToGroup(groupSummaries(3).groupId, expectedAddRequest1)

        val expectedAddRequest2 =
          AddMembersToAccessGroupRequest(clients = Some(Set(Client(client.enrolmentKey, client.name))))
        expectAddMembersToGroup(groupSummaries(4).groupId, expectedAddRequest2)

        implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
          FakeRequest("POST", submitUrl)
            .withFormUrlEncodedBody(
              "groups[0]" -> groupSummaries(3).groupId.toString,
              "groups[1]" -> groupSummaries(4).groupId.toString,
              "submit"    -> CONTINUE_BUTTON
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        AuthOkWithClient()
        expectPutSessionItem(GROUP_IDS_ADDED_TO, Seq(groupSummaries(3).groupId, groupSummaries(4).groupId))

        val result = controller.submitSelectGroupsForClient(client.id)(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get
          .shouldBe(routes.AddClientToGroupsController.showConfirmClientAddedToGroups(client.id).url)

      }
    }

    "redirect to manage account if 'none of the above' is selected" in {
      // given
      AuthOkWithClient()

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", submitUrl)
          .withFormUrlEncodedBody(
            "groups[0]" -> s"${AddGroupsToClientForm.NoneValue}",
            "submit"    -> CONTINUE_BUTTON
          )
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitSelectGroupsForClient(client.id)(request)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result).get shouldBe controller.appConfig.agentServicesAccountManageAccountUrl
    }

    "display error when no groups are selected" in {
      // given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(GroupId.random(), s"Group $i", Some(i * 3), i * 4))
      val groupsAlreadyAssociatedToClient = groupSummaries.take(2)

      AuthOkWithClient()
      expectGetGroupsForArn(arn)(groupSummaries)
      expectGetGroupSummariesForClient(arn)(client)(groupsAlreadyAssociatedToClient)

      implicit val request: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", submitUrl)
          .withFormUrlEncodedBody("submit" -> CONTINUE_BUTTON)
          .withSession(SessionKeys.sessionId -> "session-x")

      val result = controller.submitSelectGroupsForClient(client.id)(request)

      status(result) shouldBe OK
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Which custom access groups would you like to add Client 0 to? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Which custom access groups would you like to add Client 0 to?"
      // a11y: error should link to first group in the checkboxes
      html
        .select(Css.errorSummaryForField(groupSummaries(2).groupId.toString))
        .text() shouldBe "You must select at least one group"
      html.select(Css.errorForField("groups")).text() shouldBe "Error: You must select at least one group"
    }
  }

  s"GET ${ctrlRoute.showConfirmClientAddedToGroups(client.id).url}" should {

    "render correctly the html" in {
      // given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(GroupId.random(), s"Group $i", Some(i * 3), i * 4))

      AuthOkWithClient()
      expectGetSessionItem(GROUP_IDS_ADDED_TO, groupSummaries.take(2).map(_.groupId))
      expectGetGroupsForArn(arn)(groupSummaries)

      // when
      val result = controller.showConfirmClientAddedToGroups(client.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Client Client 0 added to access groups Group 1,Group 2 - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Client added to access groups"
      html.select(Css.H2).text() shouldBe "What happens next"
      html.select(Css.li("groups-added-to")).get(0).text shouldBe "Group 1"
      html.select(Css.li("groups-added-to")).get(1).text shouldBe "Group 2"
      html.select(Css.paragraphs).get(0).text() shouldBe "You have added Client 0 to the following groups:"
      html
        .select(Css.paragraphs)
        .get(1)
        .text() shouldBe "The team members in these access groups can now view and manage the tax affairs of the client you added."
      html.select("a#back-to-manage").text() shouldBe "Back to manage clients page"

    }

  }

}
