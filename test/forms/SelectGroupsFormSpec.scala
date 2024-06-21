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

import java.util.UUID

class SelectGroupsFormSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  val createNewField = "createNew"

  "SelectGroupsForm binding" should {

    "be successful for createNew group option" in {
      val params = Map(createNewField -> "true")
      SelectGroupsForm.form().bind(params).get shouldBe SelectGroups.CreateNew
    }
    "be successful for selected existing group option" in {
      val groupId = UUID.randomUUID().toString
      val params = Map("groups[0]" -> groupId)
      SelectGroupsForm.form().bind(params).get shouldBe SelectGroups.SelectedGroups(List(groupId))
    }
    "be successful when only 'none of the above' is selected option" in {
      val params = Map("groups[0]" -> SelectGroupsForm.NoneValue)
      SelectGroupsForm.form().bind(params).get shouldBe SelectGroups.NoneOfTheAbove
    }
    "fail when both groups and createNew selected" in {
      val groupId = UUID.randomUUID().toString
      val params = Map(createNewField -> "true", "groups[0]" -> groupId)
      SelectGroupsForm.form().bind(params).errors.head.message shouldBe "unassigned.client.assign.existing.or.new.error"
    }

    "fail when no checkboxes are selected" in {
      val params = Map.empty[String, String]
      SelectGroupsForm
        .form()
        .bind(params)
        .errors
        .head
        .message shouldBe "unassigned.client.assign.invalid-selection.error"
    }
    "fail when both groups and 'none of the above' are selected" in {
      val groupId = UUID.randomUUID().toString
      val params = Map("groups[0]" -> groupId, "groups[1]" -> SelectGroupsForm.NoneValue)
      SelectGroupsForm
        .form()
        .bind(params)
        .errors
        .head
        .message shouldBe "unassigned.client.assign.invalid-selection.error"
    }
    "populate form based on result value correctly" in {
      val groupId = UUID.randomUUID().toString
      SelectGroupsForm.form().fill(SelectGroups.CreateNew).data shouldBe Map(createNewField -> "true")
      SelectGroupsForm.form().fill(SelectGroups.NoneOfTheAbove).data shouldBe Map(
        "groups[0]" -> SelectGroupsForm.NoneValue
      )
      SelectGroupsForm.form().fill(SelectGroups.SelectedGroups(List(groupId))).data shouldBe Map("groups[0]" -> groupId)
    }

  }

}
