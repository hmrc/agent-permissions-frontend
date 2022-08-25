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

import config.AppConfig
import connectors.{AddMembersToAccessGroupRequest, AgentPermissionsConnector}
import forms.AddGroupsToClientForm
import models.DisplayClient
import play.api.Logging
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import repository.SessionCacheRepository
import services.{GroupService, SessionCacheService}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import views.html.groups.add_groups_to_client.{confirm_added, select_groups}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AddClientToGroupsController @Inject()(
                                             clientAction: ClientAction,
                                             mcc: MessagesControllerComponents,
                                             val agentPermissionsConnector: AgentPermissionsConnector,
                                             val sessionCacheRepository: SessionCacheRepository,
                                             val sessionCacheService: SessionCacheService,
                                             groupService: GroupService,
                                             select_groups: select_groups,
                                             confirm_added: confirm_added
                                           )(implicit val appConfig: AppConfig,
                                             ec: ExecutionContext,
                                             implicit override val messagesApi: MessagesApi
                                           ) extends FrontendController(mcc)

  with GroupsControllerCommon
  with I18nSupport
  with SessionBehaviour
  with Logging {

  import clientAction._

  def showSelectGroupsForClient(clientId: String): Action[AnyContent] = Action.async { implicit request =>
    withClientForAuthorisedOptedAgent(clientId) { (displayClient: DisplayClient, arn: Arn) => {
      groupService.groupSummaries(arn).flatMap { allGroups =>
        groupService.groupSummariesForClient(arn, displayClient).map { clientGroups =>
          Ok(
            select_groups(
              clientGroups,
              allGroups._1.diff(clientGroups),
              displayClient,
              AddGroupsToClientForm.form()
            )
          )
        }
      }
    }
    }
  }

  def submitSelectGroupsForClient(clientId: String): Action[AnyContent] = Action.async { implicit request =>
    withClientForAuthorisedOptedAgent(clientId) { (displayClient: DisplayClient, arn: Arn) => {
      AddGroupsToClientForm.form().bindFromRequest().fold(formErrors => {
        groupService.groupSummaries(arn).flatMap { allGroups =>
          groupService.groupSummariesForClient(arn, displayClient).map { clientGroups =>
            Ok(
              select_groups(
                clientGroups,
                allGroups._1.diff(clientGroups),
                displayClient,
                formErrors
              )
            )
          }
        }
      }, { groupIds =>
        val enrolment = DisplayClient.toEnrolment(displayClient)
        Future.sequence(groupIds.map { grp =>
          agentPermissionsConnector.addMembersToGroup(
            grp, AddMembersToAccessGroupRequest(clients = Some(Set(enrolment))
            ))
        }).map { _ =>
          sessionCacheRepository.putSession[Seq[String]](GROUP_IDS_ADDED_TO, groupIds)
          Redirect(routes.AddClientToGroupsController.showConfirmClientAddedToGroups(clientId))
        }
      }
      )
    }
    }
  }

  def showConfirmClientAddedToGroups(clientId: String): Action[AnyContent] = Action.async { implicit request =>
    withClientForAuthorisedOptedAgent(clientId) { (displayClient: DisplayClient, arn: Arn) => {
      sessionCacheRepository.getFromSession[Seq[String]](GROUP_IDS_ADDED_TO).flatMap { maybeGroupIds =>
        groupService.groupSummaries(arn).map { tuple =>
          tuple._1
        }.map { groupSummaries =>
          val groupsAddedTo = groupSummaries
            .filter(grp => maybeGroupIds.getOrElse(Seq.empty).contains(grp.groupId))
          Ok(confirm_added(displayClient, groupsAddedTo))
        }
      }
    }
    }
  }

}