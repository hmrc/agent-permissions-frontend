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

class ClientReferenceFormSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  val clientReference = "clientRef"

  "ClientReferenceForm binding" should {

    "be successful when non-empty" in {
      val params = Map(clientReference -> "XYZ @42")
      ClientReferenceForm.form().bind(params).value shouldBe Some("XYZ @42")
    }

    "have errors when empty" in {
      val params = Map(clientReference -> "")
      val validatedForm = ClientReferenceForm.form().bind(params)
      validatedForm.hasErrors shouldBe true
      validatedForm.errors.length shouldBe 1
      validatedForm
        .error(clientReference)
        .get
        .message shouldBe "error.client-reference.required"
    }

    "have errors when length exceeds max allowed characters" in {
      val params = Map(clientReference -> RandomStringUtils.randomAlphanumeric(81))
      val validatedForm = ClientReferenceForm.form().bind(params)
      validatedForm.hasErrors shouldBe true
      validatedForm.errors.length shouldBe 1
      validatedForm
        .error(clientReference)
        .get
        .message shouldBe "error.client-reference.max-length"
    }

    "have errors when invalid characters < present" in {
      val params = Map(clientReference -> "<invalid>")
      val validatedForm = ClientReferenceForm.form().bind(params)
      validatedForm.hasErrors shouldBe true
      validatedForm.errors.length shouldBe 1
      validatedForm
        .error(clientReference)
        .get
        .message shouldBe "error.client-reference.invalid"
    }

    "have errors when invalid characters \\ present" in {
      val params = Map(clientReference -> "inva\\id")
      val validatedForm = ClientReferenceForm.form().bind(params)
      validatedForm.hasErrors shouldBe true
      validatedForm.errors.length shouldBe 1
      validatedForm
        .error(clientReference)
        .get
        .message shouldBe "error.client-reference.invalid"
    }

  }

}
