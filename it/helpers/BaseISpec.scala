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

package helpers

import com.google.inject.AbstractModule
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import controllers.AuthAction
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.{Application, Configuration, Environment}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.mongo.TimestampSupport
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

abstract class BaseISpec extends AnyWordSpec
  with Matchers
  with GuiceOneAppPerSuite
  with AuthorisationStub
  with ScalaFutures
  with MongoSupport
   {

  implicit val mockAuthConnector: AuthConnector = mock[AuthConnector]
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val appConfig = app.injector.instanceOf[AppConfig]
     implicit val metrics = app.injector.instanceOf[Metrics]

   implicit val request = FakeRequest()
       .withHeaders("Authorization" -> "Bearer XYZ")
       .withSession(SessionKeys.sessionId -> "session-x")

  implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

  val agentEnrolment = "HMRC-AS-AGENT"
  val agentReferenceNumberIdentifier = "AgentReferenceNumber"
  val validArn = "TARN0000001"
     val arn = Arn(validArn)

  val agentEnrolmentIdentifiers: Seq[EnrolmentIdentifier] = Seq(EnrolmentIdentifier(agentReferenceNumberIdentifier, validArn))

  val mockedAuthResponse = Enrolments(Set(Enrolment(agentEnrolment, agentEnrolmentIdentifiers, "Activated"))) and Some(User)

     def moduleWithOverrides = new AbstractModule() {

       lazy val conf: Configuration = GuiceApplicationBuilder().configuration
       lazy val env: Environment = GuiceApplicationBuilder().environment

       override def configure(): Unit = {
         bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf))

       }
     }

  override def fakeApplication(): Application = {
    GuiceApplicationBuilder()
      .disable[com.kenshoo.play.metrics.PlayModule]
      .configure("auditing.enabled" -> false)
      .configure("metrics.enabled" -> false)
      .configure("metrics.jvm" -> false)
      .overrides(moduleWithOverrides)
      .build()
  }

  protected val ttl       = 1000.millis
  protected val now       = Instant.now()

  protected val timestampSupport = new TimestampSupport {
    override def timestamp(): Instant = now
  }



}
