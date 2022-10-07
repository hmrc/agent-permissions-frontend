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

import akka.Done
import com.google.inject.AbstractModule
import connectors.{AddMembersToAccessGroupRequest, AgentPermissionsConnector, AgentUserClientDetailsConnector, GroupSummary}
import helpers.{BaseSpec, Css}
import models.DisplayClient
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, contentAsString, defaultAwaitTimeout, redirectLocation}
import repository.SessionCacheRepository
import services.{ClientService, GroupService}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}

import scala.concurrent.{ExecutionContext, Future}

class AddClientToGroupsControllerSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]
  implicit lazy val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector = mock[AgentUserClientDetailsConnector]
  implicit val mockGroupService: GroupService = mock[GroupService]
  implicit val mockClientService: ClientService = mock[ClientService]
  lazy val sessionCacheRepo: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)

  override def moduleWithOverrides = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf, mockAgentPermissionsConnector))
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
      bind(classOf[AgentUserClientDetailsConnector]).toInstance(mockAgentUserClientDetailsConnector)
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepo)
      bind(classOf[GroupService]).toInstance(mockGroupService)
      bind(classOf[ClientService]).toInstance(mockClientService)
    }
  }

  private val controller = fakeApplication.injector.instanceOf[AddClientToGroupsController]

  override implicit lazy val fakeApplication: Application =
    appBuilder.configure("mongodb.uri" -> mongoUri).build()

  val fakeClients: Seq[Client] =
    List.tabulate(3)(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"Client $i"))

  val displayClients: Seq[DisplayClient] = fakeClients.map(DisplayClient.fromClient(_))
  private val client: DisplayClient = displayClients(0)


  s"GET ${routes.AddClientToGroupsController.showSelectGroupsForClient(client.id).url}" should {

    "render correctly the html" in {
      //given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(s"groupId$i", s"Group $i", i * 3, i * 4))
      val summaries = (groupSummaries, Seq.empty)
      val groupsAlreadyAssociatedToClient = groupSummaries.take(2)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      (mockClientService
        .lookupClient(_: Arn)(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, client.id, *, *)
        .returning(Future successful Some(client))

      (mockGroupService.groupSummaries(_: Arn)(_: Request[_], _: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *, *)
        .returning(Future.successful(summaries))

      (mockGroupService
        .groupSummariesForClient(_: Arn, _: DisplayClient)(_: Request[_], _: ExecutionContext, _: HeaderCarrier))
        .expects(arn, client, *, *, *)
        .returning(Future.successful(groupsAlreadyAssociatedToClient))

      //when
      val result = controller.showSelectGroupsForClient(client.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Which access groups would you like to add Client 0 to? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Which access groups would you like to add Client 0 to?"
      html.select(Css.paragraphs).get(0).text() shouldBe "Client is currently in these access groups:"
      html.select(Css.li("already-in-groups")).get(0).text() shouldBe "Group 1"
      html.select(Css.li("already-in-groups")).get(1).text() shouldBe "Group 2"
      val form = html.select(Css.form)
      form.attr("action").shouldBe(submitUrl)
      val checkboxes = form.select(".govuk-checkboxes#groups input[name=groups[]]")
      checkboxes size() shouldBe 3
      val checkboxLabels = form.select("label.govuk-checkboxes__label")
      checkboxLabels.get(0).text() shouldBe "Group 3"
      checkboxLabels.get(1).text() shouldBe "Group 4"
      checkboxLabels.get(2).text() shouldBe "Group 5"
      html.select(Css.linkStyledAsButton).text() shouldBe "Cancel"
      html.select(Css.linkStyledAsButton).hasClass("govuk-button--secondary")
      html.select(Css.linkStyledAsButton).hasClass("govuk-!-margin-right-3")
      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }

    "render correctly when client not in any groups yet" in {
      //given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(s"groupId$i", s"Group $i", i * 3, i * 4))
      val summaries = (groupSummaries, Seq.empty)
      val groupsAlreadyAssociatedToClient = Seq.empty

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      (mockClientService
        .lookupClient(_: Arn)(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, client.id, *, *)
        .returning(Future successful Some(client))

      (mockGroupService.groupSummaries(_: Arn)(_: Request[_], _: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *, *)
        .returning(Future.successful(summaries))

      (mockGroupService
        .groupSummariesForClient(_: Arn, _: DisplayClient)(_: Request[_], _: ExecutionContext, _: HeaderCarrier))
        .expects(arn, client, *, *, *)
        .returning(Future.successful(groupsAlreadyAssociatedToClient))

      //when
      val result = controller.showSelectGroupsForClient(client.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Which access groups would you like to add Client 0 to? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Which access groups would you like to add Client 0 to?"
      html.select(Css.paragraphs).get(0).text() shouldBe "Client is currently not in any access groups"
      html.select(Css.li("already-in-groups")).isEmpty shouldBe true
      val form = html.select(Css.form)
      form.attr("action").shouldBe(submitUrl)
      val checkboxes = form.select(".govuk-checkboxes#groups input[name=groups[]]")
      checkboxes size() shouldBe 5
      val checkboxLabels = form.select("label.govuk-checkboxes__label")
      checkboxLabels.get(0).text() shouldBe "Group 1"
      checkboxLabels.get(1).text() shouldBe "Group 2"
      checkboxLabels.get(2).text() shouldBe "Group 3"
      html.select(Css.linkStyledAsButton).text() shouldBe "Cancel"
      html.select(Css.linkStyledAsButton).hasClass("govuk-button--secondary")
      html.select(Css.linkStyledAsButton).hasClass("govuk-!-margin-right-3")
      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }

    "render correctly when no available groups" in {
      //given
      val groupSummaries = (1 to 2)
        .map(i => GroupSummary(s"groupId$i", s"Group $i", i * 3, i * 4))
      val summaries = (groupSummaries, Seq.empty)
      val groupsAlreadyAssociatedToClient = groupSummaries

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      (mockClientService
        .lookupClient(_: Arn)(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, client.id, *, *)
        .returning(Future successful Some(client))

      (mockGroupService.groupSummaries(_: Arn)(_: Request[_], _: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *, *)
        .returning(Future.successful(summaries))

      (mockGroupService
        .groupSummariesForClient(_: Arn, _: DisplayClient)(_: Request[_], _: ExecutionContext, _: HeaderCarrier))
        .expects(arn, client, *, *, *)
        .returning(Future.successful(groupsAlreadyAssociatedToClient))

      //when
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

  private val submitUrl: String = routes.AddClientToGroupsController.submitSelectGroupsForClient(client.id).url

  s"POST to $submitUrl" should {

    "add client to the selected groups and redirect" when {

      s"At least 1 checkbox is checked for the group to add to" in {
        //given
        val groupSummaries = (1 to 5)
          .map(i => GroupSummary(s"groupId$i", s"Group $i", i * 3, i * 4))

        expectAuthorisationGrantsAccess(mockedAuthResponse)
        expectIsArnAllowed(true)

        (mockClientService
          .lookupClient(_: Arn)(_: String)(_: HeaderCarrier, _: ExecutionContext))
          .expects(arn, client.id, *, *)
          .returning(Future successful Some(client))

        val expectedAddRequest1 = AddMembersToAccessGroupRequest(clients = Some(Set(Client(client.enrolmentKey, client.name))))
        (mockAgentPermissionsConnector
          .addMembersToGroup(_: String, _: AddMembersToAccessGroupRequest)(_: HeaderCarrier, _: ExecutionContext))
          .expects(groupSummaries(3).groupId, expectedAddRequest1, *, *)
          .returning(Future successful Done)

        val expectedAddRequest2 = AddMembersToAccessGroupRequest(clients = Some(Set(Client(client.enrolmentKey, client.name))))
        (mockAgentPermissionsConnector
          .addMembersToGroup(_: String, _: AddMembersToAccessGroupRequest)(_: HeaderCarrier, _: ExecutionContext))
          .expects(groupSummaries(4).groupId, expectedAddRequest2, *, *)
          .returning(Future successful Done)

        implicit val request =
          FakeRequest("POST", submitUrl)
            .withFormUrlEncodedBody(
              "groups[0]" -> groupSummaries(3).groupId,
              "groups[1]" -> groupSummaries(4).groupId,
              "submit" -> "continue"
            )
            .withSession(SessionKeys.sessionId -> "session-x")

        await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

        val result = controller.submitSelectGroupsForClient(client.id)(request)

        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get
          .shouldBe(routes.AddClientToGroupsController.showConfirmClientAddedToGroups(client.id).url)

      }
    }

    "display error when no groups are selected" in {
      //given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(s"groupId$i", s"Group $i", i * 3, i * 4))
      val summaries = (groupSummaries, Seq.empty)
      val groupsAlreadyAssociatedToClient = groupSummaries.take(2)

      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      (mockClientService
        .lookupClient(_: Arn)(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, client.id, *, *)
        .returning(Future successful Some(client))

      (mockGroupService.groupSummaries(_: Arn)(_: Request[_], _: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *, *)
        .returning(Future.successful(summaries))

      (mockGroupService
        .groupSummariesForClient(_: Arn, _: DisplayClient)(_: Request[_], _: ExecutionContext, _: HeaderCarrier))
        .expects(arn, client, *, *, *)
        .returning(Future.successful(groupsAlreadyAssociatedToClient))

      implicit val request =
        FakeRequest("POST", submitUrl)
          .withFormUrlEncodedBody("submit" -> "continue")
          .withSession(SessionKeys.sessionId -> "session-x")

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))

      val result = controller.submitSelectGroupsForClient(client.id)(request)

      status(result) shouldBe OK
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      // then
      html.title() shouldBe "Error: Which access groups would you like to add Client 0 to? - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Which access groups would you like to add Client 0 to?"
      html.select(Css.errorSummaryForField("groups")).text() shouldBe "You must select at least one group"
      html.select(Css.errorForField("groups")).text() shouldBe "Error: You must select at least one group"
    }
  }

  s"GET ${routes.AddClientToGroupsController.showConfirmClientAddedToGroups(client.id).url}" should {

    "render correctly the html" in {
      //given
      val groupSummaries = (1 to 5)
        .map(i => GroupSummary(s"groupId$i", s"Group $i", i * 3, i * 4))
      val summaries = (groupSummaries, Seq.empty)

      await(sessionCacheRepo.putSession(OPTIN_STATUS, OptedInReady))
      await(sessionCacheRepo.putSession(GROUP_IDS_ADDED_TO, groupSummaries.take(2).map(_.groupId)))
      expectAuthorisationGrantsAccess(mockedAuthResponse)
      expectIsArnAllowed(true)

      (mockClientService
        .lookupClient(_: Arn)(_: String)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, client.id, *, *)
        .returning(Future successful Some(client))

      (mockGroupService.groupSummaries(_: Arn)(_: Request[_], _: ExecutionContext, _: HeaderCarrier))
        .expects(arn, *, *, *)
        .returning(Future.successful(summaries))

      //when
      val result = controller.showConfirmClientAddedToGroups(client.id)(request)

      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Client Client 0 added to access groups Group 1,Group 2 - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Client added to access groups"
      html.select(Css.H2).text() shouldBe "What happens next"
      html.select(Css.li("groups-added-to")).get(0).text shouldBe "Group 1"
      html.select(Css.li("groups-added-to")).get(1).text shouldBe "Group 2"
      html.select(Css.paragraphs).get(0).text() shouldBe "You have added Client 0 to the following groups:"
      html.select(Css.paragraphs).get(1).text() shouldBe "The team members in these access groups can now view and manage the tax affairs of the client you added."
      html.select("a#back-to-manage").text() shouldBe "Back to manage clients page"

    }

  }

}
