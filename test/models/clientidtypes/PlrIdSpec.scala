/*
 * Copyright 2025 HM Revenue & Customs
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

package models.clientidtypes

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class PlrIdSpec extends AnyWordSpec with Matchers {

  "Pillar2Id" should {

    "parse json as model correctly" in {
      val expectedModel: PlrId = PlrId("XBPLR1234567890")
      val json = Json.obj("value" -> "XBPLR1234567890")

      json.as[PlrId] shouldBe expectedModel
    }
    "be valid if matching the regex" in {
      PlrId.isValid("XBPLR1234567890") shouldBe true
    }

    "invalid if more than 15 characters" in {
      PlrId.isValid("XBPLR1234567890125637") shouldBe false
    }

    "invalid if less than 15 characters" in {
      PlrId.isValid("XBPLR12345") shouldBe false
    }

    "invalid if characters don't match regex" in {
      PlrId.isValid("Xdefinitely-not") shouldBe false
    }

    "invalid if empty" in {
      PlrId.isValid("") shouldBe false
    }
  }
}
