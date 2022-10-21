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

import akka.Done
import com.google.inject.AbstractModule
import helpers.{AgentUserClientDetailsConnectorMocks, BaseSpec, HttpClientMocks}
import models.TeamMember
import play.api.Application
import play.api.http.Status.{ACCEPTED, INTERNAL_SERVER_ERROR, NO_CONTENT, OK}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.{Client, UserDetails}
import uk.gov.hmrc.http.{HttpClient, HttpResponse, UpstreamErrorResponse}

class AgentUserClientDetailsConnectorSpec
    extends BaseSpec
    with HttpClientMocks
    with AgentUserClientDetailsConnectorMocks {

  implicit val mockHttpClient: HttpClient = mock[HttpClient]

  override def moduleWithOverrides: AbstractModule = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[HttpClient]).toInstance(mockHttpClient)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder
      .build()

  val connector: AgentUserClientDetailsConnector =
    fakeApplication.injector.instanceOf[AgentUserClientDetailsConnectorImpl]

  "getClientList" should {
    "return a Some[Seq[Client]] when status response is OK" in {
      expectHttpClientGET[HttpResponse](
        HttpResponse.apply(
          OK,
          """[
          |{
          |"enrolmentKey": "HMRC-MTD-IT~MTDITID~XX12345",
          |"friendlyName": "Rapunzel"
          |}
          |]""".stripMargin
        ))

      connector.getClients(arn).futureValue shouldBe
        Seq(Client(enrolmentKey = "HMRC-MTD-IT~MTDITID~XX12345",
                   friendlyName = "Rapunzel"))
    }

    "return None when status response is Accepted" in {

      expectHttpClientGET[HttpResponse](HttpResponse.apply(ACCEPTED, ""))

      connector.getClients(arn).futureValue shouldBe Seq.empty[Client]
    }

    "throw error when status response is 5xx" in {

      expectHttpClientGET[HttpResponse](HttpResponse.apply(503, ""))

      intercept[UpstreamErrorResponse] {
        await(connector.getClients(arn))
      }
    }
  }

  "getTeamMembers" should {
    "return a Some[Seq[UserDetails]] when status response is OK" in {
      expectHttpClientGET[HttpResponse](
        HttpResponse.apply(
          OK,
          """[
          |{
          |"userId": "uid",
          |"credentialRole": "cred-role",
          |"name": "name",
          |"email": "x@y.com"
          |}
          |]""".stripMargin
        ))

      connector.getTeamMembers(arn).futureValue shouldBe
        Seq(UserDetails( Some("uid"),Some("cred-role"),  Some("name"), Some("x@y.com")))
    }

    "return None when status response is Accepted" in {

      expectHttpClientGET[HttpResponse](HttpResponse.apply(ACCEPTED, ""))

      connector.getTeamMembers(arn).futureValue shouldBe Seq.empty[TeamMember]
    }

    "throw error when status response is 5xx" in {

      expectHttpClientGET[HttpResponse](HttpResponse.apply(503, ""))

      intercept[UpstreamErrorResponse] {
        await(connector.getTeamMembers(arn))
      }
    }
  }


  "updateClientReference" should {

    "return Future[Done] when response code is NO_CONTENT" in {

      val clientRequest = Client("HMRC-MTD-VAT~VRN~123456789", "new friendly name")
      val url = s"http://localhost:9449/agent-user-client-details/arn/${arn.value}/update-friendly-name"
      val mockResponse = HttpResponse.apply(NO_CONTENT, "")
      expectHttpClientPUT[Client, HttpResponse](url,
        clientRequest,
        mockResponse)
      connector.updateClientReference(arn, clientRequest).futureValue shouldBe Done
    }

    "throw exception when it fails" in {

      val clientRequest = Client("HMRC-MTD-VAT~VRN~123456789", "new friendly name")
      val url = s"http://localhost:9449/agent-user-client-details/arn/${arn.value}/update-friendly-name"
      val mockResponse = HttpResponse.apply(INTERNAL_SERVER_ERROR, "")
      expectHttpClientPUT[Client, HttpResponse](url,
        clientRequest,
        mockResponse)

      //then
      val caught = intercept[UpstreamErrorResponse] {
        await(connector.updateClientReference(arn, clientRequest))
      }
      caught.statusCode shouldBe INTERNAL_SERVER_ERROR
    }


  }
}
