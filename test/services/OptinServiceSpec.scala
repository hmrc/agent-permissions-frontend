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

package services

import akka.Done
import com.google.inject.AbstractModule
import connectors.AgentPermissionsConnector
import helpers.BaseSpec
import play.api.Application
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repository.SessionCacheRepository
import uk.gov.hmrc.agents.accessgroups.optin.OptedInSingleUser

class OptinServiceSpec extends BaseSpec {

  lazy val sessionCacheRepo: SessionCacheRepository = new SessionCacheRepository(mongoComponent, timestampSupport)
  implicit lazy val mockAgentPermissionsConnector: AgentPermissionsConnector = mock[AgentPermissionsConnector]

  override def moduleWithOverrides = new AbstractModule() {

    override def configure(): Unit = {
      bind(classOf[SessionCacheRepository]).toInstance(sessionCacheRepo)
      bind(classOf[AgentPermissionsConnector]).toInstance(mockAgentPermissionsConnector)
    }
  }

  override implicit lazy val fakeApplication: Application =
    appBuilder.configure("mongodb.uri" -> mongoUri).build()

  val service = fakeApplication.injector.instanceOf[OptInServiceImpl]

  "Opt out" should {
    "call opt out" in {
      expectOptInStatusOk(arn)(OptedInSingleUser)
      expectPostOptOutAccepted(arn)

      val result = await(service.optOut(arn))

      result shouldBe Done
    }
  }

  "Opt in" should {
    "call opt in" in {
      expectOptInStatusOk(arn)(OptedInSingleUser)
      expectPostOptInAccepted(arn)

      val result = await(service.optIn(arn, Some("ENG")))

      result shouldBe Done
    }
  }

}
