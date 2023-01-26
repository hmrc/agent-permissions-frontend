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
import connectors.AgentPermissionsConnector
import helpers.{BaseSpec, Css}
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, redirectLocation}
import services.{ClientService, GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.OptedInReady
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.SessionKeys

class CreateGroupSelectGroupTypeControllerSpec extends BaseSpec {

  implicit lazy val sessionCacheService: SessionCacheService = mock[SessionCacheService]
  implicit lazy val clientService: ClientService = mock[ClientService]
  implicit lazy val groupService: GroupService = mock[GroupService]
  implicit lazy val authConnector: AuthConnector = mock[AuthConnector]
  implicit lazy val agentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[SessionCacheService]).toInstance(sessionCacheService)
      bind(classOf[ClientService]).toInstance(clientService)
      bind(classOf[AuthConnector]).toInstance(authConnector)
      bind(classOf[GroupService]).toInstance(groupService)
      bind(classOf[AgentPermissionsConnector]).toInstance(agentPermissionsConnector)
    }
  }

  override implicit lazy val fakeApplication: Application = appBuilder.configure("mongodb.uri" -> mongoUri).build()

  val controller: CreateGroupSelectGroupTypeController = fakeApplication.injector.instanceOf[CreateGroupSelectGroupTypeController]
  private val ctrlRoute: ReverseCreateGroupSelectGroupTypeController = routes.CreateGroupSelectGroupTypeController
  private val VAT = "HMRC-MTD-VAT"

  def expectAuthOkArnAllowedOptedInReady(): Unit = {
    expectAuthorisationGrantsAccess(mockedAuthResponse)
    expectIsArnAllowed(allowed = true)
    expectGetSessionItem(OPT_IN_STATUS, OptedInReady)
  }


  "showSelectGroupType" should {
    "render correctly and clear existing session keys" in {
      //given
      expectAuthOkArnAllowedOptedInReady()

      expectDeleteSessionItems(sessionKeys)

      implicit val request = FakeRequest("GET",
        ctrlRoute.showSelectGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showSelectGroupType()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Create an access group - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Create an access group"

      val form = html.select("form")
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe ctrlRoute.submitSelectGroupType.url

      val radios = html.select(Css.radioButtonsField("answer-radios"))
      radios.select("label[for=answer]").text() shouldBe "Custom access group"
      radios.select("#answer-item-hint").text() shouldBe "Select clients based on any criteria."
      radios.select("#answer-no-item-hint")
        .text() shouldBe "Select all clients for one tax service. These groups can be set to update automatically when you get new clients."
      radios.select("label[for=answer-no]").text() shouldBe "Access group based on tax service"

      html.select(Css.submitButton).text() shouldBe "Continue"
    }

    "render errors when no radio selected" in {

      //given
      expectAuthOkArnAllowedOptedInReady()

      implicit val request = FakeRequest("POST",
        ctrlRoute.submitSelectGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody(
        )

      //when
      val result = controller.submitSelectGroupType()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Create an access group - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Create an access group"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select the type of access group you want to create"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select the type of access group you want to create"
    }

    "redirect when answer is 'true'/'Custom access group'" in {

      //given
      expectAuthOkArnAllowedOptedInReady()

      implicit val request = FakeRequest("POST",
        ctrlRoute.submitSelectGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody("answer" -> "true")
      expectPutSessionItem(GROUP_TYPE, CUSTOM_GROUP)

      //when
      val result = controller.submitSelectGroupType()(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(controllers.routes.CreateGroupSelectNameController.showGroupName.url)
    }

    "redirect when answer is 'false'/'Access group based on tax service'" in {

      //given
      expectAuthOkArnAllowedOptedInReady()

      implicit val request = FakeRequest("POST",
        ctrlRoute.submitSelectGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody("answer" -> "false")
      expectPutSessionItem(GROUP_TYPE, TAX_SERVICE_GROUP)

      //when
      val result = controller.submitSelectGroupType()(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(controllers.routes.CreateGroupSelectGroupTypeController.showSelectTaxServiceGroupType.url)
    }
  }

  "showSelectTaxServiceGroupType" should {

    "render correctly" in {
      //given
      expectAuthOkArnAllowedOptedInReady()
      expectGetSessionItem(GROUP_TYPE, TAX_SERVICE_GROUP)

      implicit val request = FakeRequest("GET",
        ctrlRoute.showSelectTaxServiceGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")

      expectGetAvailableTaxServiceClientCount(arn)(List(13, 85, 38, 22, 108))

      //when
      val result = controller.showSelectTaxServiceGroupType()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      //      println(html.select("main"))
      html.title() shouldBe "Group based on tax service - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Group based on tax service"

      val form = html.select("form")
      form.attr("method") shouldBe "POST"
      form.attr("action") shouldBe ctrlRoute.submitSelectTaxServiceGroupType.url

      form.select("label[for=taxType]").text() shouldBe "Select clients by tax service"
      val taxTypeOptions = form.select("select#taxType option")
      taxTypeOptions.get(0).text() shouldBe "Select tax service"
      taxTypeOptions.get(1).text() shouldBe "Capital Gains Tax on UK Property account (38)"
      taxTypeOptions.get(2).text() shouldBe "Maintain a Trust or Estate (108)"
      taxTypeOptions.get(3).text() shouldBe "Making Tax Digital for Income Tax (13)"
      taxTypeOptions.get(4).text() shouldBe "Plastic Packaging Tax (22)"
      taxTypeOptions.get(5).text() shouldBe "VAT (85)"

      html.select(Css.submitButton).text() shouldBe "Continue"
    }

    "render error page if info is empty" in {

      expectAuthOkArnAllowedOptedInReady()
      expectGetSessionItem(GROUP_TYPE, TAX_SERVICE_GROUP)

      implicit val request = FakeRequest("GET",
        ctrlRoute.showSelectTaxServiceGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")

      expectGetAvailableTaxServiceClientCount(arn)(List(0,0,0,0,0))

      //when
      val result = controller.showSelectTaxServiceGroupType()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      //      println(html.select("main"))
      html.title() should include( "All access groups have been created")
      html.select(Css.H1).text() shouldBe "You have already created all the tax service access groups"
      html.select(Css.linkStyledAsButton).text() shouldBe "Create custom access group"
    }

  }

  "submitSelectTaxServiceGroupType" should {

    "render errors when empty form submitted" in {

      //given
      expectAuthOkArnAllowedOptedInReady()
      expectGetSessionItem(GROUP_TYPE, TAX_SERVICE_GROUP)
      expectGetAvailableTaxServiceClientCount(arn)(List(13, 85, 38, 22, 108))

      implicit val request = FakeRequest("POST",
        ctrlRoute.submitSelectTaxServiceGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody("taxType" -> "")

      //when
      val result = controller.submitSelectTaxServiceGroupType()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Group based on tax service - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Group based on tax service"
      html.select(Css.errorSummaryForField("taxType")).text() shouldBe "Select the tax type for this access group"
      html.select(Css.errorForField("taxType")).text() shouldBe "Error: Select the tax type for this access group"

    }

    "redirect to review tax service group type page" in {

      //given
      implicit val request = FakeRequest("POST",
        ctrlRoute.submitSelectTaxServiceGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody("taxType" -> VAT)

      expectAuthOkArnAllowedOptedInReady()
      expectGetSessionItem(GROUP_TYPE, TAX_SERVICE_GROUP)
      expectPutSessionItem(GROUP_SERVICE_TYPE, VAT)
      expectGroupNameCheckOK(arn, "VAT")
      expectPutSessionItem(GROUP_NAME, "VAT")
      expectPutSessionItem(GROUP_NAME_CONFIRMED, false)


      //when
      val result = controller.submitSelectTaxServiceGroupType()(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(ctrlRoute.showReviewTaxServiceGroupType.url)
    }

  }

  "showReviewTaxServiceGroupType" should {

    "render correctly" in {
      //given
      expectAuthOkArnAllowedOptedInReady()
      expectGetSessionItem(GROUP_TYPE, TAX_SERVICE_GROUP)
      expectGetSessionItem(GROUP_SERVICE_TYPE, "HMRC-CGT-PD")

      implicit val request = FakeRequest("GET",
        ctrlRoute.showReviewTaxServiceGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.showReviewTaxServiceGroupType()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "You have selected all “Capital Gains Tax on UK Property account” clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "You have selected all “Capital Gains Tax on UK Property account” clients"

      val form = html.select("form")
      form.attr("method") should include("POST")
      form.attr("action") shouldBe ctrlRoute.submitReviewTaxServiceGroupType.url
      form.select(Css.legend).text() shouldBe "Continue with selected clients?"

      val radios = html.select(Css.radioButtonsField("answer-radios"))
      radios.select("label[for=answer]").text() shouldBe "Yes, continue to naming group"
      radios.select("label[for=answer-no]").text() shouldBe "No, start again"

      html.select(Css.submitButton).text() shouldBe "Save and continue"
    }
  }

  "submitReviewTaxServiceGroupType" should {

    "render errors when empty form submitted" in {
      //given
      expectAuthOkArnAllowedOptedInReady()
      expectGetSessionItem(GROUP_TYPE, TAX_SERVICE_GROUP)
      expectGetSessionItem(GROUP_SERVICE_TYPE, VAT)

      implicit val request = FakeRequest("POST",
        ctrlRoute.submitReviewTaxServiceGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")

      //when
      val result = controller.submitReviewTaxServiceGroupType()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))

      html.title() shouldBe "Error: You have selected all “VAT” clients - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "You have selected all “VAT” clients"
      html.select(Css.errorSummaryForField("answer")).text() shouldBe "Select yes if you would like to create a group of this type"
      html.select(Css.errorForField("answer")).text() shouldBe "Error: Select yes if you would like to create a group of this type"
     }

    "redirect to select group name page when 'yes' selected" in {

      //given
      expectAuthOkArnAllowedOptedInReady()
      expectGetSessionItem(GROUP_TYPE, TAX_SERVICE_GROUP)

      implicit val request = FakeRequest("POST",
        ctrlRoute.submitReviewTaxServiceGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody("answer" -> "true"
        )

      //when
      val result = controller.submitReviewTaxServiceGroupType()(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(controllers.routes.CreateGroupSelectNameController.showGroupName.url)
    }

    "redirect to select group type when 'no' selected" in {
      //given
      expectAuthOkArnAllowedOptedInReady()
      expectGetSessionItem(GROUP_TYPE, TAX_SERVICE_GROUP)

      implicit val request = FakeRequest("POST",
        ctrlRoute.submitReviewTaxServiceGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody("answer" -> "false")

      //when
      val result = controller.submitReviewTaxServiceGroupType()(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(ctrlRoute.showSelectGroupType.url)
    }
  }
}
