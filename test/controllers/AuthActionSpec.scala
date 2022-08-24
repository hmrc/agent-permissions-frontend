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
import config.AppConfig
import helpers.BaseSpec
import org.scalamock.handlers.CallHandler0
import play.api.Application
import play.api.http.Status.{FORBIDDEN, OK, SEE_OTHER}
import play.api.mvc.Results.Ok
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation}
import uk.gov.hmrc.auth.core.{AuthConnector, InsufficientEnrolments, MissingBearerToken, UnsupportedAuthProvider}

import scala.concurrent.Future

class AuthActionSpec extends BaseSpec {

  implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]

  implicit lazy val mockAppConfig: AppConfig = mock[AppConfig]

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {
    override def configure(): Unit = {
      bind(classOf[AuthConnector]).toInstance(mockAuthConnector)
      bind(classOf[AppConfig]).toInstance(mockAppConfig)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .build()

  val authAction: AuthAction = fakeApplication.injector.instanceOf[AuthAction]

  "Auth Action" when {
    "the user hasn't logged in" should {
      "redirect the user to log in " in {

        expectAuthorisationFails(MissingBearerToken())
        mockAppConfigBasGatewayUrl("someUrl")
        mockAppConfigLoginContinueUrl("someUrl")
        mockAppConfigAppName("someName")

        val result =
          authAction.isAuthorisedAgent((arn) => Future.successful(Ok("")))
        status(result) shouldBe SEE_OTHER
        redirectLocation(result).get shouldBe
          "someUrl/bas-gateway/sign-in?continue_url=someUrl%2F&origin=someName"
      }
    }

    "the user has InsufficentEnrolments" should {
      "return FORBIDDEN" in {

        expectAuthorisationFails(InsufficientEnrolments())

        val result =
          authAction.isAuthorisedAgent((arn) => Future.successful(Ok("")))
        status(result) shouldBe FORBIDDEN
      }
    }

    "the user has UnsupportedAuthProvider" should {
      "return FORBIDDEN" in {
        expectAuthorisationFails(UnsupportedAuthProvider())

        val result =
          authAction.isAuthorisedAgent((arn) => Future.successful(Ok("")))
        status(result) shouldBe FORBIDDEN
      }
    }

    "the user has authorization" when {

      "check for Arn Allow List is false" should {
        s"return $OK" in {
          expectAuthorisationGrantsAccess(mockedAuthResponse)
          mockAppConfigCheckArnAllowList(false)

          val result = authAction.isAuthorisedAgent((arn) => Future.successful(Ok("")))
          status(result) shouldBe OK
        }
      }

      "check for Arn Allow List is true" when {

        "arn is not in allowed list" should {
          s"return $FORBIDDEN" in {
            expectAuthorisationGrantsAccess(mockedAuthResponse)
            mockAppConfigCheckArnAllowList(toCheckArnAllowList = true)
            mockAppConfigAllowedArns(Seq.empty)

            val result = authAction.isAuthorisedAgent((arn) => Future.successful(Ok("")))
            status(result) shouldBe FORBIDDEN
          }
        }

        "arn is in allowed list" should {
          s"return $OK" in {
            expectAuthorisationGrantsAccess(mockedAuthResponse)
            mockAppConfigCheckArnAllowList(toCheckArnAllowList = true)
            mockAppConfigAllowedArns(Seq(arn.value))

            val result = authAction.isAuthorisedAgent((arn) => Future.successful(Ok("")))
            status(result) shouldBe OK
          }
        }
      }
    }
  }

  def mockAppConfigBasGatewayUrl(url: String): CallHandler0[String] =
    (() => mockAppConfig.basGatewayUrl)
      .expects().returning(url)

  def mockAppConfigLoginContinueUrl(url: String): CallHandler0[String] =
    (() => mockAppConfig.loginContinueUrl)
      .expects().returning(url)

  def mockAppConfigAppName(name: String): CallHandler0[String] =
    (() => mockAppConfig.appName)
      .expects().returning(name)

  def mockAppConfigCheckArnAllowList(toCheckArnAllowList: Boolean): CallHandler0[Boolean] =
    (() => mockAppConfig.checkArnAllowList)
      .expects().returning(toCheckArnAllowList)

  def mockAppConfigAllowedArns(allowedArns: Seq[String]): CallHandler0[Seq[String]] =
    (() => mockAppConfig.allowedArns)
      .expects().returning(allowedArns)
}
