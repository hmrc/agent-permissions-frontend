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
import helpers.{BaseSpec, Css}
import org.jsoup.Jsoup
import play.api.Application
import play.api.http.Status.{OK, SEE_OTHER}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, redirectLocation}
import uk.gov.hmrc.http.SessionKeys

class CreateGroupSelectGroupTypeControllerSpec extends BaseSpec {

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
    }
  }

  override implicit lazy val fakeApplication: Application = appBuilder.configure("mongodb.uri" -> mongoUri).build()

  val controller: CreateGroupSelectGroupTypeController = fakeApplication.injector.instanceOf[CreateGroupSelectGroupTypeController]
  private val ctrlRoute: ReverseCreateGroupSelectGroupTypeController = routes.CreateGroupSelectGroupTypeController

  "showSelectGroupType" should {
    "render correctly" in {
      //given
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
      implicit val request = FakeRequest("POST",
        ctrlRoute.submitSelectGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody("answer" -> "true")

      //when
      val result = controller.submitSelectGroupType()(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(controllers.routes.CreateGroupController.showGroupName.url)
    }

    "redirect when answer is 'false'/'Access group based on tax service'" in {

      //given
      implicit val request = FakeRequest("POST",
        ctrlRoute.submitSelectGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody("answer" -> "false")

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
      implicit val request = FakeRequest("GET",
        ctrlRoute.showSelectTaxServiceGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")

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
      taxTypeOptions.get(1).text() shouldBe "Making Tax Digital for Income Tax"
      taxTypeOptions.get(2).text() shouldBe "VAT"
      taxTypeOptions.get(3).text() shouldBe "Capital Gains Tax on UK Property account"
      taxTypeOptions.get(4).text() shouldBe "Plastic Packaging Tax"
      taxTypeOptions.get(5).text() shouldBe "Maintain a Trust or Estate"

      form.select("#addAutomatically-hint").text() shouldBe "If you select ‘Yes’ we will add new clients as soon as they authorise you for this tax service."
      val radios = html.select(Css.radioButtonsField("addAutomatically-radios"))
      radios.select("label[for=addAutomatically]").text() shouldBe "Yes"
      radios.select("label[for=answer-no]").text() shouldBe "No"

      html.select(Css.submitButton).text() shouldBe "Continue"
    }

    "render errors when empty form submitted" in {

      //given
      implicit val request = FakeRequest("POST",
        ctrlRoute.submitSelectTaxServiceGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody(
          "taxType" -> ""
        )

      //when
      val result = controller.submitSelectTaxServiceGroupType()(request)

      //then
      status(result) shouldBe OK
      val html = Jsoup.parse(contentAsString(result))
      html.title() shouldBe "Error: Group based on tax service - Agent services account - GOV.UK"
      html.select(Css.H1).text() shouldBe "Group based on tax service"
      html.select(Css.errorSummaryForField("taxType")).text() shouldBe "Select the tax type for this access group"
      html.select(Css.errorForField("taxType")).text() shouldBe "Error: Select the tax type for this access group"
      html.select(Css.errorSummaryForField("addAutomatically")).text() shouldBe "Select if you want clients added automatically to this access group"
      html.select(Css.errorForField("addAutomatically")).text() shouldBe "Error: Select if you want clients added automatically to this access group"
    }

    "redirect to review selected team members page when add automatically selected" in {

      //given
      implicit val request = FakeRequest("POST",
        ctrlRoute.submitSelectTaxServiceGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody(
          "taxType" -> "HMRC-MTD-VAT",
          "addAutomatically" -> "true"
        )

      //when
      val result = controller.submitSelectTaxServiceGroupType()(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(controllers.routes.CreateGroupSelectTeamMembersController.showReviewSelectedTeamMembers(None, None).url)
    }

    "redirect to select team members page when add automatically NOT selected" in {

      //given
      implicit val request = FakeRequest("POST",
        ctrlRoute.submitSelectTaxServiceGroupType.url)
        .withSession(SessionKeys.sessionId -> "session-x")
        .withFormUrlEncodedBody(
          "taxType" -> "HMRC-MTD-VAT",
          "addAutomatically" -> "false"
        )

      //when
      val result = controller.submitSelectTaxServiceGroupType()(request)

      //then
      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(controllers.routes.CreateGroupSelectTeamMembersController.showSelectTeamMembers(None, None)
        .url)
    }
  }
}