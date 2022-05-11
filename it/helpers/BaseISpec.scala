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
import config.AppConfig
import connectors.mocks.{MockAgentPermissionsConnector, MockHttpClient}
import controllers.{AuthAction, OptInController}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.MessagesApi
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import play.api.{Application, Configuration, Environment}
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.mongo.TimestampSupport
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import views.html._

import java.time.Instant
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

abstract class BaseISpec extends AnyWordSpec
  with Matchers
  with GuiceOneAppPerSuite
  with AuthorisationStub
  with ScalaFutures
  with CleanMongoCollectionSupport
  with MockAgentPermissionsConnector
  with MockHttpClient
  with MetricsTestSupport
   {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val appConfig = app.injector.instanceOf[AppConfig]


   implicit val request = FakeRequest()
       .withHeaders("Authorization" -> "Bearer XYZ")
       .withSession(SessionKeys.sessionId -> "session-x")

  implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

  val agentEnrolment = "HMRC-AS-AGENT"
  val agentReferenceNumberIdentifier = "AgentReferenceNumber"
  val validArn = "TARN0000001"
     val arn = Arn(validArn)

  val agentEnrolmentIdentifiers: Seq[EnrolmentIdentifier] = Seq(EnrolmentIdentifier(agentReferenceNumberIdentifier, validArn))

     implicit lazy val mockAuthConnector: AuthConnector = mock[AuthConnector]

  val mockedAuthResponse = Enrolments(Set(Enrolment(agentEnrolment, agentEnrolmentIdentifiers, "Activated"))) and Some(User)

  val mcc: MessagesControllerComponents = fakeApplication().injector.instanceOf[MessagesControllerComponents]
     val messagesApi: MessagesApi = fakeApplication().injector.instanceOf[MessagesApi]

     val startPage = fakeApplication().injector.instanceOf[start]
     val optInPage = fakeApplication().injector.instanceOf[want_to_opt_in]
     val optedInPage = fakeApplication().injector.instanceOf[you_have_opted_in]
     val notOptedInPage = fakeApplication.injector.instanceOf[you_have_not_opted_in]
     val errorPage = fakeApplication().injector.instanceOf[error]


     val authAction: AuthAction = fakeApplication().injector.instanceOf[AuthAction]


     def moduleWithOverrides = new AbstractModule() {

       lazy val conf: Configuration = GuiceApplicationBuilder().configuration
       lazy val env: Environment = GuiceApplicationBuilder().environment


       override def configure(): Unit = {
         bind(classOf[AuthAction]).toInstance(new AuthAction(mockAuthConnector, env, conf))
         bind(classOf[OptInController]).toInstance(new OptInController(authAction, mcc,
           mockAgentPermissionsConnector,mongoSessionCacheRepository,startPage,optInPage,optedInPage,notOptedInPage)(appConfig,ec,messagesApi))
       }
     }

  override def fakeApplication(): Application = {
    GuiceApplicationBuilder()
      //.disable[com.kenshoo.play.metrics.PlayModule]
      .configure("auditing.enabled" -> false)
      .configure("metrics.enabled" -> true)
      .configure("metrics.jvm" -> false)
      .overrides(moduleWithOverrides)
      .build()
  }

  protected val ttl       = 1000.millis
  protected val now       = Instant.now()

  protected val timestampSupport = new TimestampSupport {
    override def timestamp(): Instant = now
  }

     def bodyOf(result: Result): String = Helpers.contentAsString(Future.successful(result))

     def status(result: Result): Int = result.header.status
     def status(result: Future[Result]): Int = Helpers.status(result)

     val mongoSessionCacheRepository: SessionCacheRepository = new SessionCacheRepository(mongoComponent, fakeApplication().configuration, timestampSupport)

}
