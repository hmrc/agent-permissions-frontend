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

package filters

import akka.stream.Materializer
import config.AppConfig
import controllers.AuthAction
import play.api.Logging
import play.api.mvc.Results.Forbidden
import play.api.mvc.{Filter, RequestHeader, Result}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ArnAllowListFilter @Inject()(authAction: AuthAction, appConfig: AppConfig)
                                  (implicit val mat: Materializer, ec: ExecutionContext)
  extends Filter with Logging {

  def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    implicit val rh: RequestHeader = requestHeader
    implicit val ac: AppConfig = appConfig

    if (ArnAllowListFilter.noCheckUriPatterns.exists(requestHeader.path.startsWith(_))) {
      nextFilter(requestHeader)
    } else {
      if (appConfig.checkArnAllowList) {
        authAction.isAuthorisedAgent { arn =>
          if (appConfig.allowedArns.contains(arn.value)) {
            nextFilter(requestHeader)
          } else {
            logger.error(s"'${arn.value}' is not in allowed ARN list, cannot access '${requestHeader.path}'")
            Future successful Forbidden
          }
        }
      } else {
        nextFilter(requestHeader)
      }
    }
  }

}

object ArnAllowListFilter {
  lazy val noCheckUriPatterns: Seq[String] = Seq(
    "/admin/metrics",
    "/ping/ping",
    "/agent-permissions/assets/",
    "/agent-permissions/hmrc-frontend/",
    "/agent-permissions/arnallowed")
}
