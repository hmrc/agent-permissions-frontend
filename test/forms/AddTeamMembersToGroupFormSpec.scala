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

import models.{TeamMember}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json

import java.util.Base64

class AddTeamMembersToGroupFormSpec extends AnyWordSpec
  with Matchers
  with GuiceOneAppPerSuite {

  val clients = "members[]"

  "AddTeamMembersToGroup form binding" should {

    val member1 = TeamMember("Bob", "bob@builds.com", None, None,true)
    val member2 = TeamMember("Steve", "steve@abc.com", None, None, false)

    val encode: TeamMember => String = teamMember => Base64.getEncoder.encodeToString(Json.toJson(teamMember).toString.getBytes)

    "be fillable with a list of TeamMembers" in {
      val validatedForm = AddTeamMembersToGroupForm.form().fill(List(member1, member2))
      validatedForm.hasErrors shouldBe false
      validatedForm.value shouldBe Option(List(member1, member2))
    }

    "be successful when non-empty" in {
      val params: Map[String, List[String]] = Map(clients -> List(encode(member1), encode(member2)))
      val boundForm = AddTeamMembersToGroupForm.form().bindFromRequest(params)
      boundForm.value shouldBe Some(List(member1, member2))
    }

    "have errors when empty" in {
      val params: Map[String, List[String]] = Map(clients -> List.empty[String])
      val boundForm = AddTeamMembersToGroupForm.form().bindFromRequest(params)
      boundForm.value shouldBe None
    }

  }

}
