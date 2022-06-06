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

import models.DisplayClient
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.mongo.cache.DataKey

import scala.concurrent.Future

package object controllers {

  implicit class ToFuture[T](t: T) {
    def toFuture = Future successful t
  }

  val isEligibleToOptIn: OptinStatus => Boolean = status => status == OptedOutEligible
  val isOptedIn: OptinStatus => Boolean = status => status == OptedInReady || status == OptedInNotReady || status == OptedInSingleUser
  val isOptedOut: OptinStatus => Boolean = status => status == OptedOutEligible || status == OptedOutSingleUser || status == OptedOutWrongClientCount
  val isOptedInComplete: OptinStatus => Boolean = status => status == OptedInReady

  val OPTIN_STATUS: DataKey[OptinStatus] = DataKey("optinStatus")
  val GROUP_NAME: DataKey[String] = DataKey("groupName")
  val GROUP_NAME_CONFIRMED: DataKey[Boolean] = DataKey("groupNameConfirmed")
  val GROUP_CLIENTS_SELECTED: DataKey[Seq[DisplayClient]] = DataKey("groupClientsSelected")
}
