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
import com.google.inject.ImplementedBy
import connectors.AgentPermissionsConnector
import controllers.OPTIN_STATUS
import play.api.mvc.Request
import repository.SessionCacheRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, OptinStatus}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[OptInServiceImpl])
trait OptinService {
  def optIn(arn: Arn, lang: Option[String])(implicit request: Request[_],
                      hc: HeaderCarrier,
                      ec: ExecutionContext): Future[Option[Done]]

  def optOut(arn: Arn)(implicit request: Request[_],
                       hc: HeaderCarrier,
                       ec: ExecutionContext): Future[Option[Done]]

}


@Singleton
class OptInServiceImpl @Inject()(
    agentPermissionsConnector: AgentPermissionsConnector,
    sessionCacheRepository: SessionCacheRepository
) extends OptinService {

  def optIn(arn: Arn, lang: Option[String])(implicit request: Request[_],
                      hc: HeaderCarrier,
                      ec: ExecutionContext): Future[Option[Done]] = {
    optingTo(agentPermissionsConnector.optIn(_, lang))(arn)(request, hc, ec)
  }

  def optOut(arn: Arn)(implicit request: Request[_],
                       hc: HeaderCarrier,
                       ec: ExecutionContext): Future[Option[Done]] =
    optingTo(agentPermissionsConnector.optOut)(arn)(request, hc, ec)

  private def optingTo(func: Arn => Future[Option[Done]])(arn: Arn)(
      implicit request: Request[_],
      hc: HeaderCarrier,
      ec: ExecutionContext): Future[Option[Done]] = {
    for {
      maybeDone <- func(arn)
      maybeStatus <- maybeDone.fold(Future successful Option.empty[OptinStatus])(_ => agentPermissionsConnector.getOptInStatus(arn))
    } yield {
      maybeStatus.map { status =>
        sessionCacheRepository.putSession[OptinStatus](OPTIN_STATUS, status)
        Done
      }
    }
  }

}
