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

import akka.stream.Materializer
import akka.stream.testkit.NoMaterializer
import com.google.inject.AbstractModule
import config.AppConfig
import helpers.BaseSpec
import org.scalamock.handlers.CallHandler0
import play.api.Application
import play.api.http.Status.{FORBIDDEN, OK}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.{AuthConnector, InsufficientEnrolments}

class ArnAllowListControllerSpec extends BaseSpec {

  implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit val mockAppConfig: AppConfig = mock[AppConfig]

  implicit val materializer: Materializer = NoMaterializer

  val authAction: AuthAction = fakeApplication.injector.instanceOf[AuthAction]

  override implicit lazy val fakeApplication: Application = appBuilder.build()

  val controller: ArnAllowListController = fakeApplication.injector.instanceOf[ArnAllowListController]

  "Is ARN Allowed" when {

    "Auth action indicates authorization failure" should {
      s"return $FORBIDDEN" in {
        expectAuthorisationFails(InsufficientEnrolments())

        val result = controller.isArnAllowed()(buildRequest)
        status(result) shouldBe FORBIDDEN
      }
    }

    "check for Arn Allow List is false" should {
      s"return $OK" in {
        expectAuthorisationGrantsAccess(mockedAuthResponse)
        mockAppConfigCheckArnAllowList(false)

        val result = controller.isArnAllowed()(buildRequest)
        status(result) shouldBe OK
      }
    }

    "check for Arn Allow List is true" when {

      "arn is not in allowed list" should {
        s"return $FORBIDDEN" in {
          expectAuthorisationGrantsAccess(mockedAuthResponse)
          mockAppConfigCheckArnAllowList(toCheckArnAllowList = true)
          mockAppConfigAllowedArns(Seq.empty)

          val result = controller.isArnAllowed()(buildRequest)
          status(result) shouldBe FORBIDDEN
        }
      }

      "arn is in allowed list" should {
        s"return $OK" in {
          expectAuthorisationGrantsAccess(mockedAuthResponse)
          mockAppConfigCheckArnAllowList(toCheckArnAllowList = true)
          mockAppConfigAllowedArns(Seq(arn.value))

          val result = controller.isArnAllowed()(buildRequest)
          status(result) shouldBe OK
        }
      }
    }
  }

  def buildRequest: FakeRequest[AnyContentAsEmpty.type] = {
    FakeRequest("GET", routes.ArnAllowListController.isArnAllowed.url)
  }

  def mockAppConfigCheckArnAllowList(toCheckArnAllowList: Boolean): CallHandler0[Boolean] =
    (() => mockAppConfig.checkArnAllowList)
      .expects().returning(toCheckArnAllowList)

  def mockAppConfigAllowedArns(allowedArns: Seq[String]): CallHandler0[Seq[String]] =
    (() => mockAppConfig.allowedArns)
      .expects().returning(allowedArns)

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {
    override def configure(): Unit = {
      bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
      bind(classOf[AppConfig]).toInstance(mockAppConfig)
    }
  }

}
