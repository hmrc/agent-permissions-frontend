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

import models.{AddTeamMembersToGroup, TeamMember}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.data.FormError


class AddTeamMembersToGroupFormSpec
  extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite {

  val hasAlreadySelected = "hasAlreadySelected"
  val search = "search"
  val members = "members[]"

  val member1: TeamMember = TeamMember("Bob", "bob@builds.com", Some("user1"), None, selected = true)
  val member2: TeamMember = TeamMember("Steve", "steve@abc.com", Some("user2"), None)

  "AddTeamMembersToGroup form binding" should {

    "be fillable with a AddTeamMembersToGroup" in {
      val validatedForm = AddTeamMembersToGroupForm
        .form()
        .fill(
          AddTeamMembersToGroup(hasAlreadySelected = false,
            None,
            Some(List(member1.id, member2.id))))
      validatedForm.hasErrors shouldBe false
      validatedForm.value shouldBe Some(
        AddTeamMembersToGroup(hasAlreadySelected = false,
          None,
          Some(List(member1.id, member2.id))))
    }

    "be successful when button is Continue and team members are non-empty" in {
      val params = Map(
        hasAlreadySelected -> List("false"),
        search -> List.empty,
        members -> List(member1.id, member2.id),
        "submit" -> List("continue")
      )
      val boundForm = AddTeamMembersToGroupForm
        .form()
        .bindFromRequest(params)

      boundForm.value shouldBe Some(
        AddTeamMembersToGroup(hasAlreadySelected = false,
          None,
          Some(List(member1.id, member2.id)),
          submit = "continue")
      )
    }

    "have errors when team members is empty and submit is continue" in {
      val params = Map(
        hasAlreadySelected -> List("false"),
        search -> List.empty,
        members -> List.empty,
        "submit" -> List("continue"),
      )
      val boundForm = AddTeamMembersToGroupForm.form().bindFromRequest(params)

      boundForm.errors shouldBe List(FormError("members", List("error.select-members.empty")))
    }


    "be successful when button is Filter with search value" in {
      val params = Map(
        hasAlreadySelected -> List("false"),
        search -> List("abc"),
        members -> List(member1.id, member2.id),
        "submit" -> List("filter"),
      )
      val boundForm = AddTeamMembersToGroupForm
        .form()
        .bindFromRequest(params)
      boundForm.value shouldBe Some(
        AddTeamMembersToGroup(
          hasAlreadySelected = false,
          Some("abc"),
          Some(List(member1.id, member2.id)),
          submit = "filter")
      )
    }

    "have errors when button is Filter and search contains invalid characters" in {
      val params = Map(
        hasAlreadySelected -> List("false"),
        search -> List("bad<search>"),
        members -> List(member1.id, member2.id),
        "submit" -> List("filter"),
      )
      val boundForm = AddTeamMembersToGroupForm
        .form()
        .bindFromRequest(params)

      boundForm.errors shouldBe List(
        FormError("search", List("error.search-members.invalid")))
    }

    "no errors when button is Filter and search field is empty" in {
      val params = Map(
        hasAlreadySelected -> List("false"),
        search -> List.empty,
        members -> List(member1.id, member2.id),
        "submit" -> List("filter"),
      )
      val boundForm = AddTeamMembersToGroupForm
        .form()
        .bindFromRequest(params)

      boundForm.hasErrors shouldBe false
    }

    "be successful when button is Clear and form is empty" in {
      val params = Map(
        hasAlreadySelected -> List("false"),
        search -> List.empty,
        members -> List.empty,
        "submit" -> List("clear"),
      )
      val boundForm = AddTeamMembersToGroupForm
        .form()
        .bindFromRequest(params)
      boundForm.value shouldBe Some(
        AddTeamMembersToGroup(hasAlreadySelected = false, None, None, submit = "clear"))
    }

  }

  "Add TeamMembersToGroup unbind form" should {

    "give expected Map of data Continue" in {
      val model = AddTeamMembersToGroup(
        hasAlreadySelected = false,
        None,
        Some(List(member1.id, member2.id)),
        submit = "continue"
      )
      AddTeamMembersToGroupForm
        .form()
        .mapping
        .unbind(model) shouldBe Map(
        "hasAlreadySelected" -> "false",
        "members[0]" -> member1.id,
        "members[1]" -> member2.id,
        "submit" -> "continue"
      )
    }

    "give expected Map of data Filter" in {
      val model = AddTeamMembersToGroup(
        hasAlreadySelected = false,
        Option("Ab"),
        Some(List(member1.id)),
        submit = "filter"
      )
      AddTeamMembersToGroupForm
        .form()
        .mapping
        .unbind(model) shouldBe Map(
        "hasAlreadySelected" -> "false",
        "search" -> "Ab",
        "members[0]" -> member1.id,
        "submit" -> "filter",
      )
    }

    "give expected Map of data Clear" in {
      val model = AddTeamMembersToGroup(hasAlreadySelected = false, None, None, submit = "clear")
      AddTeamMembersToGroupForm
        .form()
        .mapping
        .unbind(model) shouldBe Map(
        "hasAlreadySelected" -> "false",
        "submit" -> "clear"
      )
    }
  }

}
