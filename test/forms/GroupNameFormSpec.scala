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

package forms

import org.apache.commons.lang3.RandomStringUtils
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class GroupNameFormSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite {

  val groupNameField = "name"

  "CreateGroupFrom binding" should {

    "be successful when non-empty" in {
      val params = Map(groupNameField -> "XYZ")
      GroupNameForm.form().bind(params).value shouldBe Some("XYZ")
    }

    "have errors when empty" in {
      val params = Map(groupNameField -> "   ")
      val validatedForm = GroupNameForm.form().bind(params)
      validatedForm.hasErrors shouldBe true
      validatedForm
        .error(groupNameField)
        .get
        .message shouldBe "group.name.required"
    }

    "have errors when length exceeds max allowed characters" in {
      val params = Map(groupNameField -> RandomStringUtils.random(33))
      val validatedForm = GroupNameForm.form().bind(params)
      validatedForm.hasErrors shouldBe true
      validatedForm
        .error(groupNameField)
        .get
        .message shouldBe "group.name.max.length"
    }
  }

}
