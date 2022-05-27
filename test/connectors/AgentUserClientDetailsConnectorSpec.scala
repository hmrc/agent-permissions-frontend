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

package connectors

import com.google.inject.AbstractModule
import helpers.{AgentUserClientDetailsConnectorMocks, BaseSpec, HttpClientMocks}
import play.api.Application
import play.api.http.Status.{ACCEPTED, OK}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.{Enrolment, Identifier}
import uk.gov.hmrc.http.{HttpClient, HttpResponse, UpstreamErrorResponse}

class AgentUserClientDetailsConnectorSpec extends BaseSpec with HttpClientMocks with AgentUserClientDetailsConnectorMocks {

  implicit val mockHttpClient: HttpClient = mock[HttpClient]

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[HttpClient]).toInstance(mockHttpClient)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .build()

  val connector: AgentUserClientDetailsConnector = fakeApplication.injector.instanceOf[AgentUserClientDetailsConnectorImpl]

  "getClientList" should {
    "return a Some[Seq[Enrolment]] when status response is OK" in {
      mockHttpGet[HttpResponse](HttpResponse.apply(OK,
      """[
          |{
          |"service": "HMRC-MTD-IT",
          |"state": "Active",
          |"friendlyName": "Rapunzel",
          |"identifiers": [{
          |"key": "MTDITID",
          |"value": "XX12345"
          |}]
          |}]""".stripMargin
      )
      )

      connector.getClientList(arn).futureValue shouldBe Some(
        Seq(
        Enrolment(
          service = "HMRC-MTD-IT",
          state = "Active",
          friendlyName = "Rapunzel",
          identifiers = List(Identifier(key = "MTDITID", value = "XX12345")))))
    }

    "return None when status response is Accepted" in {

      mockHttpGet[HttpResponse](HttpResponse.apply(ACCEPTED,""))

      connector.getClientList(arn).futureValue shouldBe None
    }

    "throw error when status reponse is 5xx" in {

      mockHttpGet[HttpResponse](HttpResponse.apply(503,""))

      intercept[UpstreamErrorResponse]{
        await(connector.getClientList(arn))
      }
    }
  }

}
