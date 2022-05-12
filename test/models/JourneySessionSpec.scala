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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.agentmtdidentifiers.model.{OptedInNotReady, OptedInReady, OptedInSingleUser, OptedOutEligible, OptedOutSingleUser, OptedOutWrongClientCount}

class JourneySessionSpec extends AnyWordSpec
with Matchers{


  "JourneySession optinStatus" when {
    "status is OptedOutEligible" should {
      "be EligibleToOptIn" in {
        JourneySession(optinStatus = OptedOutEligible).isEligibleToOptIn shouldBe true
      }
      "status is OptedInReady" should {
        "isEligibleToOptOut" in {
          JourneySession(optinStatus = OptedInReady).isEligibleToOptOut shouldBe true
        }
      }
      "status is OptedInNotReady" should {
        "isEligibleToOptOut" in {
          JourneySession(optinStatus = OptedInNotReady).isEligibleToOptOut shouldBe true
        }
      }
      "status is OptedInSingleUser" should {
        "isEligibleToOptOut" in {
          JourneySession(optinStatus = OptedInSingleUser).isEligibleToOptOut shouldBe true
        }
      }
      "status is OptedOutSingleUser" should {
        "isNotEligibleToOptIn" in {
          JourneySession(optinStatus = OptedOutSingleUser).isEligibleToOptIn shouldBe false
        }
      }
      "status is OptedOutWrongClientCount" should {
        "isNotEligibleToOptIn" in {
          JourneySession(optinStatus = OptedOutWrongClientCount).isEligibleToOptIn shouldBe false
        }
      }
    }
  }
}
