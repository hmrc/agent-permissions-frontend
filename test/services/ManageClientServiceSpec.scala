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

package services

import akka.Done
import connectors.{AgentPermissionsConnector, AgentUserClientDetailsConnector}
import controllers.CLIENT_REFERENCE
import helpers.BaseSpec
import models.DisplayClient
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Client}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class ManageClientServiceSpec extends BaseSpec {

  val mockAgentUserClientDetailsConnector: AgentUserClientDetailsConnector =
    mock[AgentUserClientDetailsConnector]
  lazy val sessionCacheRepo: SessionCacheRepository =
    new SessionCacheRepository(mongoComponent, timestampSupport)

  val mockAgentPermissionsConnector: AgentPermissionsConnector =
    mock[AgentPermissionsConnector]

  val service =
    new ManageClientService(mockAgentUserClientDetailsConnector, sessionCacheRepo)

  val fakeClients: Seq[Client] = (1 to 10)
    .map(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly name $i"))

  val displayClients: Seq[DisplayClient] =
    fakeClients.map(DisplayClient.fromClient(_))

  "updateClientReference" should {
    "PUT client to agentUserClientDetailsConnector" in {
      //given
      val newName = "The new name"
      val clientWithNewName = Client("HMRC-MTD-VAT~VRN~123456781", newName)
      (mockAgentUserClientDetailsConnector
        .updateClientReference(_: Arn, _:Client)(_: HeaderCarrier, _: ExecutionContext))
        .expects(arn, clientWithNewName,*,*)
        .returning(Future successful Done)

      //when
      val updateRef = await(service.updateClientReference(arn, displayClients.head, newName))

      //then
      updateRef shouldBe Done
    }
  }

  "getNewNameFromSession" should {
    "Get new name from the session" in {
      //given
      await(sessionCacheRepo.putSession(CLIENT_REFERENCE, "new name"))

      //when
      val maybeNewName: Option[String] =
        await(service.getNewNameFromSession())
      val name: String = maybeNewName.get

      //then
      name shouldBe "new name"
    }
  }


}
