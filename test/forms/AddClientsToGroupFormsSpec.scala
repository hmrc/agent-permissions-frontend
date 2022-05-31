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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class AddClientsToGroupFormsSpec extends AnyWordSpec
  with Matchers
  with GuiceOneAppPerSuite {

  val clients = "clients[]"

  "CreateGroupFrom binding" should {

    "be fillable with a list of strings" in {
      val validatedForm = AddClientsToGroupForm.form().fill(List("one", "two"))
      validatedForm.hasErrors shouldBe false
      validatedForm.value shouldBe Option(List("one", "two"))
    }

    "be successful when non-empty" in {
      val params: Map[String, List[String]] = Map(clients -> List("one", "two", "ten"))
      val boundForm = AddClientsToGroupForm.form().bindFromRequest(params)
      boundForm.value shouldBe Some(List("one", "two", "ten"))
    }

    "have errors when empty" in {
      val params: Map[String, List[String]] = Map(clients -> List.empty[String])
      val boundForm = AddClientsToGroupForm.form().bindFromRequest(params)
      boundForm.value shouldBe None
    }

  }

}
