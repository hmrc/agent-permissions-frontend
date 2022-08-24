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

package filters

import akka.stream.Materializer
import akka.stream.testkit.NoMaterializer
import com.google.inject.AbstractModule
import config.AppConfig
import controllers.AuthAction
import helpers.BaseSpec
import org.scalamock.handlers.CallHandler0
import play.api.Application
import play.api.mvc.Results.{Forbidden, Ok}
import play.api.mvc.{RequestHeader, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.{AuthConnector, InsufficientEnrolments}

import scala.concurrent.Future

class ArnAllowListFilterSpec extends BaseSpec {

  implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit val mockAppConfig: AppConfig = mock[AppConfig]

  implicit val materializer: Materializer = NoMaterializer

  val authAction: AuthAction = fakeApplication.injector.instanceOf[AuthAction]

  val filter: ArnAllowListFilter = new ArnAllowListFilter(authAction, mockAppConfig)

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

  override implicit lazy val fakeApplication: Application = appBuilder.build()

  val nextFilter: RequestHeader => Future[Result] = _ => Future successful Ok

  "Filter" when {

    "requests having URIs that do not need to be checked" should {
      "be allowed through" in {
        ArnAllowListFilter.noCheckUriPatterns.map { noCheckUriPattern =>
          filter(nextFilter)(FakeRequest("GET", noCheckUriPattern)).futureValue shouldBe Ok
        }
      }
    }
  }

  "requests having URIs that need to be checked" when {

    "feature flag to check for allowed list of ARNs set to false" should {
      "be allowed through" in {
        mockAppConfigCheckArnAllowList(toCheckArnAllowList = false)

        filter(nextFilter)(FakeRequest("GET", "/agent-permissions/do-you-want-to-opt-in")).futureValue shouldBe Ok
      }
    }

    "feature flag to check for allowed list of ARNs set to true" when {

      "auth action indicates an exception" should {
        "not be allowed through" in {
          mockAppConfigCheckArnAllowList(toCheckArnAllowList = true)
          expectAuthorisationFails(InsufficientEnrolments())

          filter(nextFilter)(FakeRequest("GET", "/agent-permissions/do-you-want-to-opt-in")).futureValue shouldBe Forbidden
        }
      }

      "allowed list of ARNs contains provided ARN" should {
        "be allowed through" in {

          mockAppConfigCheckArnAllowList(toCheckArnAllowList = true)
          expectAuthorisationGrantsAccess(mockedAuthResponse)
          mockAppConfigAllowedArns(Seq(arn.value))

          filter(nextFilter)(FakeRequest("GET", "/agent-permissions/do-you-want-to-opt-in")).futureValue shouldBe Ok
        }
      }

      "allowed list of ARNs does not contain provided ARN" should {
        "not be allowed through" in {

          mockAppConfigCheckArnAllowList(toCheckArnAllowList = true)
          expectAuthorisationGrantsAccess(mockedAuthResponse)
          mockAppConfigAllowedArns(Seq.empty)

          filter(nextFilter)(FakeRequest("GET", "/agent-permissions/do-you-want-to-opt-in")).futureValue shouldBe Forbidden
        }
      }
    }
  }

}
