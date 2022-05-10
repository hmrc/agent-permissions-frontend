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

import connectors.AgentPermissionsConnector
import models.JourneySession
import play.api.mvc.Results.Forbidden
import play.api.mvc.{Request, Result}
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.{ExecutionContext, Future}


trait SessionBehaviour {

  val sessionCacheRepository: SessionCacheRepository
  val agentPermissionsConnector: AgentPermissionsConnector


  private def eligibleTo(optin: Boolean)(arn: Arn)(body: => Future[Result])(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Result] = {
    val forbidden = {
      Forbidden(s"not eligible to ${if (optin) "opt-in" else "out-out"}")
    }
    sessionCacheRepository
      .getFromSession[JourneySession](DataKey("opting")).flatMap {
      case Some(session) if optin && session.isEligibleToOptIn || !optin && session.isEligibleToOptOut => body
      case Some(_) => Future successful forbidden
      case None => agentPermissionsConnector.getOptinStatus(arn).flatMap {
        case Some(status) => sessionCacheRepository
          .putSession(DataKey("opting"), JourneySession(sessionId = request.session.get("sessionId").get, optinStatus = status))
          .flatMap(_ =>
            if (optin && status == OptedOutEligible) body
            else if (!optin && (status == OptedInReady || status == OptedInNotReady || status == OptedInSingleUser)) body
            else Future successful forbidden)
        case None => Future successful forbidden
      }
    }
  }


  def withEligibleToOptOut(arn: Arn)(body: => Future[Result])(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    eligibleTo(false)(arn)(body)(request, hc, ec)

  def withEligibleToOptIn(arn: Arn)(body: => Future[Result])(implicit request: Request[_], hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    eligibleTo(true)(arn)(body)(request, hc, ec)
}



