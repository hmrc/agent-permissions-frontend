package uk.gov.hmrc.agentpermissions.controllers

import play.api.http.Status.SEE_OTHER
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, status}
import uk.gov.hmrc.agentpermissions.helpers.BaseISpec
import uk.gov.hmrc.auth.core.{AuthConnector, MissingBearerToken}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class AuthActionISpec  extends BaseISpec {

  "Auth Action" when {
    "the user hasn't logged in" must {
      "redirect the user to log in " in {
        val authAction = new AuthAction(
          new FakeFailingAuthConnector(new MissingBearerToken),
          fakeApplication().environment,
          fakeApplication().configuration
        )

        implicit val request = FakeRequest("GET", "/agent-permission/opt-in/start")
          .withHeaders("Authorization" -> "Bearer some-token", "X-Session-ID" -> UUID.randomUUID().toString)

        val result = authAction.withAuthorisedAgent((arn) => Future.successful(Ok("whatever")))
        status(result) shouldBe SEE_OTHER
        //        redirectLocation(result).get
      }
    }


  }
}

class FakeFailingAuthConnector(exceptionToReturn: Throwable) extends AuthConnector {
  val serviceUrl: String = ""

  override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
    Future.failed(exceptionToReturn)


}
