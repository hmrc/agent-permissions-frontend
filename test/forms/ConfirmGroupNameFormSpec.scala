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

package forms

import org.apache.commons.lang3.RandomStringUtils
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class ConfirmGroupNameFormSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  val groupNameField = "name"
  val answerField = "answer"

  "CreateGroupFrom binding" should {

    "be successful when non-empty" in {
      val params = Map(groupNameField -> "XYZ VALID", answerField -> "true")
      ConfirmCreateGroupForm.form("").bind(params).hasErrors shouldBe false
    }

    "have errors when no answer given" in {
      val params = Map(groupNameField -> "My Clients", answerField -> "")
      val validatedForm =
        ConfirmCreateGroupForm.form("answer.not.given").bind(params)
      validatedForm.hasErrors shouldBe true
      validatedForm.errors.length shouldBe 1
      validatedForm.error(answerField).get.message shouldBe "answer.not.given"
    }

    "have errors when group name hidden input exceeds max allowed characters" in {
      val params = Map(groupNameField -> RandomStringUtils.randomAlphanumeric(33), answerField -> "true")
      val validatedForm = ConfirmCreateGroupForm.form("").bind(params)
      validatedForm.hasErrors shouldBe true
      validatedForm.errors.length shouldBe 1
      validatedForm
        .error(groupNameField)
        .get
        .message shouldBe "group.name.max.length"
    }

    "have errors when group name hidden input has invalid character < present" in {
      val params = Map(groupNameField -> "invalid < chars>  ", answerField -> "true")
      val validatedForm = ConfirmCreateGroupForm.form("").bind(params)
      validatedForm.hasErrors shouldBe true
      validatedForm.errors.length shouldBe 1
      validatedForm
        .error(groupNameField)
        .get
        .message shouldBe "group.name.invalid"
    }

    "have errors when group name hidden input has invalid character \\ present" in {
      val params = Map(groupNameField -> "invalid \\chars", answerField -> "true")
      val validatedForm = ConfirmCreateGroupForm.form("").bind(params)
      validatedForm.hasErrors shouldBe true
      validatedForm.errors.length shouldBe 1
      validatedForm
        .error(groupNameField)
        .get
        .message shouldBe "group.name.invalid"
    }

    "have errors when group name is empty" in {
      val params = Map(groupNameField -> "   ", answerField -> "true")
      val validatedForm = ConfirmCreateGroupForm.form("").bind(params)
      validatedForm.hasErrors shouldBe true
      validatedForm.errors.length shouldBe 1
      validatedForm
        .error(groupNameField)
        .get
        .message shouldBe "group.name.required"
    }
  }

}
