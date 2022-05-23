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

package models

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.{Enrolment, OptedInNotReady, OptedInReady, OptedInSingleUser, OptedOutEligible, OptinStatus}

case class JourneySession(
                           optInStatus: OptinStatus,
                           clientList: Option[Seq[Enrolment]] = None){

  val isEligibleToOptIn = optInStatus == OptedOutEligible
  val isEligibleToOptOut = optInStatus == OptedInReady || optInStatus == OptedInNotReady || optInStatus == OptedInSingleUser
}


object JourneySession {
  implicit val format: Format[JourneySession] = Json.format[JourneySession]
}
