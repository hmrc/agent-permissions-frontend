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

import controllers.{CLEAR_BUTTON, CONTINUE_BUTTON, FILTER_BUTTON}
import models.{AddClientsToGroup, DisplayClient}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.data.FormError

class AddClientsToGroupFormSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  val hasSelectedClients = "hasSelectedClients"
  val search = "search"
  val filter = "filter"
  val clients = "clients[]"

  val client1: DisplayClient = DisplayClient("123", "JD", "VAT", s"id-key-1")
  val client2: DisplayClient = DisplayClient("456", "HH", "CGT", s"id-key-2")

  "CreateGroupFrom binding" should {

    "be fillable with a AddClientsToGroup" in {
      val validatedForm = AddClientsToGroupForm
        .form()
        .fill(AddClientsToGroup(None, None, Some(List(client1.id, client2.id)), "continue"))
      validatedForm.hasErrors shouldBe false
      validatedForm.value shouldBe Option(AddClientsToGroup(None, None, Some(List(client1.id, client2.id)), "continue"))
    }

    "be successful when button is Continue and clients are non-empty" in {
      val params = Map(
        search   -> List.empty,
        filter   -> List.empty,
        clients  -> List(client1.id, client2.id),
        "submit" -> List("continue")
      )
      val boundForm = AddClientsToGroupForm.form().bindFromRequest(params)
      boundForm.value shouldBe Some(
        AddClientsToGroup(
          None,
          None,
          Some(List(client1.id, client2.id)),
          "continue"
        )
      )
    }

    "be successful when button is Clear and form is empty" in {
      val params = Map(
        search   -> List.empty,
        filter   -> List.empty,
        clients  -> List.empty,
        "submit" -> List("clear")
      )
      val boundForm =
        AddClientsToGroupForm.form().bindFromRequest(params)
      boundForm.value shouldBe Some(AddClientsToGroup(None, None, None, "clear"))
    }

    "be successful when button is Filter and form is non-empty" in {
      val params = Map(
        search   -> List.empty,
        filter   -> List("abc"),
        clients  -> List(client1.id, client2.id),
        "submit" -> List("filter")
      )
      val boundForm = AddClientsToGroupForm.form().bindFromRequest(params)

      boundForm.value shouldBe Some(
        AddClientsToGroup(
          None,
          Some("abc"),
          Some(List(client1.id, client2.id)),
          "filter"
        )
      )
    }

    "be successful when button is Continue, clients is empty but hasPreSelected is true" in {
      val params =
        Map(
          search   -> List.empty,
          filter   -> List.empty,
          clients  -> List.empty,
          "submit" -> List("continue")
        )

      val boundForm = AddClientsToGroupForm.form(true).bindFromRequest(params)

      boundForm.hasErrors shouldBe false
      boundForm.value shouldBe Some(
        AddClientsToGroup(
          None,
          None,
          None,
          "continue"
        )
      )
    }

    "have errors when button is Continue, clients is empty and hasPreSelected is false" in {
      val params =
        Map(
          search   -> List.empty,
          filter   -> List.empty,
          clients  -> List.empty,
          "submit" -> List("continue")
        )

      val boundForm = AddClientsToGroupForm.form().bindFromRequest(params)

      boundForm.errors shouldBe List(FormError("", List("error.select-clients.empty")))
    }

    "have NOT have errors when button is Filter and search and filter fields are empty" in {
      val params = Map(
        search   -> List.empty,
        filter   -> List.empty,
        clients  -> List(client1.id, client2.id),
        "submit" -> List("filter")
      )
      val boundForm = AddClientsToGroupForm.form().bindFromRequest(params)

      boundForm.hasErrors shouldBe false
    }

    "have errors when button is Filter and search contains invalid characters" in {
      val params = Map(
        search   -> List("bad<search>"),
        filter   -> List.empty,
        clients  -> List(client1.id, client2.id),
        "submit" -> List("filter")
      )
      val boundForm =
        AddClientsToGroupForm.form().bindFromRequest(params)
      boundForm.errors shouldBe List(FormError("search", List("error.search-filter.invalid")))
    }

  }

  "Unbind form" should {

    "give expected Map of data Continue" in {
      val model = AddClientsToGroup(None, None, Some(List(client1.id, client2.id)), submit = "continue")
      AddClientsToGroupForm
        .form()
        .mapping
        .unbind(model) shouldBe Map(
        "clients[0]" -> client1.id,
        "clients[1]" -> client2.id,
        "submit"     -> CONTINUE_BUTTON
      )
    }

    "have errors  when button is continue and no clients" in {
      val params = Map(
        search   -> List.empty,
        filter   -> List.empty,
        clients  -> List.empty,
        "submit" -> List("continue")
      )
      val boundForm = AddClientsToGroupForm.form().bindFromRequest(params)
      boundForm.hasErrors shouldBe true
      boundForm.errors.head.messages shouldBe Seq("error.select-clients.empty")
    }

    "give expected Map of data Filter" in {
      val model = AddClientsToGroup(Option("Ab"), Option("Bc"), Some(List(client1.id)), "clear")
      AddClientsToGroupForm
        .form()
        .mapping
        .unbind(model) shouldBe Map(
        "filter"     -> "Bc",
        "search"     -> "Ab",
        "clients[0]" -> client1.id,
        "submit"     -> CLEAR_BUTTON
      )
    }

    "give expected Map of data Clear" in {
      val model =
        AddClientsToGroup(None, None, None, submit = "filter")
      AddClientsToGroupForm
        .form()
        .mapping
        .unbind(model) shouldBe Map(
        "submit" -> FILTER_BUTTON
      )
    }
  }

}
