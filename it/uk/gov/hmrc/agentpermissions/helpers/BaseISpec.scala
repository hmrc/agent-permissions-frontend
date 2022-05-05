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

package uk.gov.hmrc.agentpermissions.helpers

import com.google.inject.AbstractModule
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Application, Configuration, Environment}
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentpermissions.AuthorisationStub
import uk.gov.hmrc.agentpermissions.controllers.AuthAction
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

abstract class BaseISpec extends AnyWordSpec
  with Matchers
  with GuiceOneAppPerSuite
  with AuthorisationStub
  with MongoSupport {

  implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit val stubAuditConnector: AuditConnector = stub[AuditConnector]

  override def fakeApplication(): Application = {

    def moduleWithOverrides = new AbstractModule() {
      lazy val conf: Configuration = GuiceApplicationBuilder().configuration
      lazy val env: Environment = GuiceApplicationBuilder().environment

        override def configure(): Unit = {
          bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf))
        }
      }

    GuiceApplicationBuilder()
      .disable[com.kenshoo.play.metrics.PlayModule]
      .configure(
        "auditing.enabled" -> false,
      )
      .overrides(moduleWithOverrides)
      .build()
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
  }

}
