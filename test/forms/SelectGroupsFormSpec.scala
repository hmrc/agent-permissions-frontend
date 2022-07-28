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

class SelectGroupsFormSpec extends AnyWordSpec
  with Matchers
  with GuiceOneAppPerSuite {

  val createNewField = "createNew"

  "SelectGroupsForm binding" should {

    "be successful for createNew group option" in {
      val params = Map(createNewField -> "true")
      SelectGroupsForm.form().bind(params).get.createNew shouldBe Some(true)
    }
    "be successful for selected existing group option" in {
      val groupId = RandomStringUtils.randomAlphanumeric(10)
      val params = Map("groups[0]" -> groupId)
      SelectGroupsForm.form().bind(params).get.groups shouldBe Some(List(groupId))
    }

    "fails when both groups and createNew selected" in {
      val groupId = RandomStringUtils.randomAlphanumeric(10)
      val params = Map(createNewField -> "true", "groups[0]" -> groupId)
      SelectGroupsForm.form().bind(params).errors(0).message shouldBe "unassigned.client.assign.existing.or.new.error"
    }

    "fails when no checkboxes are selected" in {
      val groupId = RandomStringUtils.randomAlphanumeric(10)
      val params = Map.empty[String, String]
      SelectGroupsForm.form().bind(params).errors(0).message shouldBe "unassigned.client.assign.nothing.selected.error"
    }
  }

}
