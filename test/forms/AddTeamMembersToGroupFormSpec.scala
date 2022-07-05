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

import models.{AddTeamMembersToGroup, ButtonSelect, TeamMember}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.data.FormError
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson

import java.util.Base64

class AddTeamMembersToGroupFormSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite {

  val hasAlreadySelected = "hasAlreadySelected"
  val search = "search"
  val members = "members[]"

  val member1 = TeamMember("Bob", "bob@builds.com", None, None, selected = true)
  val member2 = TeamMember("Steve", "steve@abc.com", None, None)

  val encode: TeamMember => String = teamMember =>
    Base64.getEncoder.encodeToString(Json.toJson(teamMember).toString.getBytes)

  "AddTeamMembersToGroup form binding" should {

    "be fillable with a AddTeamMembersToGroup" in {
      val validatedForm = AddTeamMembersToGroupForm
        .form()
        .fill(
          AddTeamMembersToGroup(hasAlreadySelected = false,
                                None,
                                Some(List(member1, member2))))
      validatedForm.hasErrors shouldBe false
      validatedForm.value shouldBe Some(
        AddTeamMembersToGroup(hasAlreadySelected = false,
                              None,
                              Some(List(member1, member2))))
    }

    "be successful when button is Continue and team members are non-empty" in {
      val params = Map(
        hasAlreadySelected -> List("false"),
        search -> List.empty,
        members -> List(encode(member1), encode(member2))
      )
      val boundForm = AddTeamMembersToGroupForm
        .form(ButtonSelect.Continue)
        .bindFromRequest(params)
      boundForm.value shouldBe Some(
        AddTeamMembersToGroup(hasAlreadySelected = false,
                              None,
                              Some(List(member1, member2))))
    }

    "have errors when team members is empty and hasAlreadySelected is false" in {
      val params = Map(
        hasAlreadySelected -> List("false"),
        search -> List.empty,
        members -> List.empty
      )
      val boundForm = AddTeamMembersToGroupForm
        .form(ButtonSelect.Continue)
        .bindFromRequest(params)
      boundForm.errors shouldBe List(
        FormError("members", List("error.select-members.empty")))
    }

    "be successful when button is Filter with search value" in {
      val params = Map(
        hasAlreadySelected -> List("false"),
        search -> List("abc"),
        members -> List(encode(member1), encode(member2))
      )
      val boundForm = AddTeamMembersToGroupForm
        .form(ButtonSelect.Filter)
        .bindFromRequest(params)
      boundForm.value shouldBe Some(
        AddTeamMembersToGroup(hasAlreadySelected = false,
                              Some("abc"),
                              Some(List(member1, member2))))
    }

    "have errors when button is Filter and search field is empty" in {
      val params = Map(
        hasAlreadySelected -> List("false"),
        search -> List.empty,
        members -> List(encode(member1), encode(member2))
      )
      val boundForm = AddTeamMembersToGroupForm
        .form(ButtonSelect.Filter)
        .bindFromRequest(params)
      boundForm.errors shouldBe List(
        FormError("search", List("error.search-members.empty")))
    }

    "be successful when button is Clear and form is empty" in {
      val params = Map(
        hasAlreadySelected -> List("false"),
        search -> List.empty,
        members -> List.empty
      )
      val boundForm = AddTeamMembersToGroupForm
        .form(ButtonSelect.Clear)
        .bindFromRequest(params)
      boundForm.value shouldBe Some(
        AddTeamMembersToGroup(hasAlreadySelected = false, None, None))
    }

  }

  "Add TeamMembersToGroup unbind form" should {

    "give expected Map of data Continue" in {
      val model = AddTeamMembersToGroup(hasAlreadySelected = false,
                                        None,
                                        Some(List(member1, member2)))
      AddTeamMembersToGroupForm
        .form(ButtonSelect.Continue)
        .mapping
        .unbind(model) shouldBe Map(
        "hasAlreadySelected" -> "false",
        "members[0]" -> Base64.getEncoder.encodeToString(
          toJson[TeamMember](member1).toString().getBytes),
        "members[1]" -> Base64.getEncoder.encodeToString(
          toJson[TeamMember](member2).toString().getBytes),
      )
    }

    "give expected Map of data Filter" in {
      val model = AddTeamMembersToGroup(hasAlreadySelected = false,
                                        Option("Ab"),
                                        Some(List(member1)))
      AddTeamMembersToGroupForm
        .form(ButtonSelect.Filter)
        .mapping
        .unbind(model) shouldBe Map(
        "hasAlreadySelected" -> "false",
        "search" -> "Ab",
        "members[0]" -> Base64.getEncoder.encodeToString(
          toJson[TeamMember](member1).toString().getBytes),
      )
    }

    "give expected Map of data Clear" in {
      val model = AddTeamMembersToGroup(hasAlreadySelected = false, None, None)
      AddTeamMembersToGroupForm
        .form(ButtonSelect.Clear)
        .mapping
        .unbind(model) shouldBe Map(
        "hasAlreadySelected" -> "false",
      )
    }
  }

}
