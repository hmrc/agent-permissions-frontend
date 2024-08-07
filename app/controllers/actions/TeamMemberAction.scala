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

package controllers.actions

import controllers._
import config.AppConfig
import models.TeamMember
import play.api.mvc.Results.Ok
import play.api.mvc.{AnyContent, MessagesRequest, Result}
import play.api.{Configuration, Environment, Logging}
import services.{SessionCacheService, TeamMemberService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import views.html.group_member_details.add_groups_to_team_member.team_member_not_found

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TeamMemberAction @Inject() (
  val authConnector: AuthConnector,
  val env: Environment,
  val config: Configuration,
  authAction: AuthAction,
  optInStatusAction: OptInStatusAction,
  val teamMemberService: TeamMemberService,
  val sessionCacheService: SessionCacheService, // indirectly used
  team_member_not_found: team_member_not_found
) extends Logging {

  import optInStatusAction._

  def withTeamMemberForAuthorisedOptedAgent(clientId: String)(fn: (TeamMember, Arn) => Future[Result])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier,
    request: MessagesRequest[AnyContent],
    appConfig: AppConfig
  ): Future[Result] =
    authAction.isAuthorisedAgent { arn =>
      isOptedIn(arn) { _ =>
        teamMemberService
          .lookupTeamMember(arn)(clientId)
          .flatMap(_.fold(teamMemberNotFound)(client => fn(client, arn)))
      }
    }

  def teamMemberNotFound(implicit request: MessagesRequest[AnyContent], appConfig: AppConfig): Future[Result] =
    Ok(team_member_not_found()).toFuture

}
