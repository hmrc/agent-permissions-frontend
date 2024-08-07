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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class AddGroupsToClientFormSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  "AddGroupsToClientForm binding" should {

    "have no errors when groups are present" in {
      val params = Map("groups[]" -> Seq("id1", "id2"))
      val boundForm = AddGroupsToClientForm
        .form()
        .bindFromRequest(params)
      boundForm.hasErrors shouldBe false
      boundForm.value shouldBe Some(Seq("id1", "id2"))
    }

    "have no errors when only 1 group is present" in {
      val params = Map("groups[]" -> Seq("id3"))
      val boundForm = AddGroupsToClientForm
        .form()
        .bindFromRequest(params)
      boundForm.hasErrors shouldBe false
      boundForm.value shouldBe Some(Seq("id3"))
    }

    "be successful when only 'none of the above' is selected option" in {
      val params = Map("groups[0]" -> AddGroupsToClientForm.NoneValue)
      val boundForm = AddGroupsToClientForm.form().bind(params)
      boundForm.hasErrors shouldBe false
      boundForm.value shouldBe Some(Seq(AddGroupsToClientForm.NoneValue))
    }

    "have errors when no groups selected" in {
      val params = Map("groups" -> List.empty)
      val boundForm = AddGroupsToClientForm
        .form()
        .bindFromRequest(params)
      boundForm.hasErrors shouldBe true
      boundForm.errors.head.messages shouldBe Seq("error.select.groups.empty")
    }

    "have errors when groups and 'none of the above' are selected" in {
      val params = Map("groups[]" -> Seq("id1", "id2", AddGroupsToClientForm.NoneValue))
      val boundForm = AddGroupsToClientForm
        .form()
        .bindFromRequest(params)
      boundForm.hasErrors shouldBe true
      boundForm.errors.head.messages shouldBe Seq("unassigned.client.assign.invalid-selection.error")
    }
  }

}
