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

class FilterByGroupNameFormSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite {

  val field = "searchGroupByName"

  "FilterByGroupNameForm binding" should {

    "be successful when non-empty" in {
      val params = Map(field -> "XYZ V@L!D")
      FilterByGroupNameForm.form.bind(params).value shouldBe Some("XYZ V@L!D")
    }

    "have errors when empty" in {
      val params = Map(field -> "   ")
      val validatedForm = FilterByGroupNameForm.form.bind(params)
      validatedForm.hasErrors shouldBe true
      validatedForm
        .error(field)
        .get
        .message shouldBe "error.group.filter.required"
    }

    "have errors when length exceeds max allowed characters" in {
      val params = Map(field -> RandomStringUtils.randomAlphanumeric(33))
      val validatedForm = FilterByGroupNameForm.form.bind(params)
      validatedForm.hasErrors shouldBe true
      validatedForm
        .error(field)
        .get
        .message shouldBe "error.group.filter.max.length"
    }

    "have errors when invalid characters" in {
      val params = Map(field -> " invalid <chars")
      val validatedForm = FilterByGroupNameForm.form.bind(params)
      validatedForm.hasErrors shouldBe true
      validatedForm
        .error(field)
        .get
        .message shouldBe "error.group.filter.invalid"
    }
  }

}
