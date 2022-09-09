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

import models.ButtonSelect.{Clear, Continue, Filter}
import models.{AddClientsToGroup, ButtonSelect, DisplayClient}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.data.FormError

class AddClientsToGroupFormSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite {

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
        .fill(
          AddClientsToGroup(hasSelectedClients = false,
                            None,
                            None,
                            Some(List(client1.id, client2.id))))
      validatedForm.hasErrors shouldBe false
      validatedForm.value shouldBe Option(
        AddClientsToGroup(hasSelectedClients = false,
                          None,
                          None,
                          Some(List(client1.id, client2.id))))
    }

    "be successful when clients are non-empty" in {
      val params = Map(
        hasSelectedClients -> List("false"),
        search -> List.empty,
        filter -> List.empty,
        clients -> List(client1.id, client2.id)
      )
      val boundForm = AddClientsToGroupForm.form().bindFromRequest(params)
      boundForm.value shouldBe Some(
        AddClientsToGroup(hasSelectedClients = false,
                          None,
                          None,
                          Some(List(client1.id, client2.id))))
    }

    "be successful when button is Clear and form is empty" in {
      val params = Map(
        hasSelectedClients -> List("false"),
        search -> List.empty,
        filter -> List.empty,
        clients -> List.empty
      )
      val boundForm =
        AddClientsToGroupForm.form(ButtonSelect.Clear).bindFromRequest(params)
      boundForm.value shouldBe Some(
        AddClientsToGroup(hasSelectedClients = false, None, None, None))
    }

    "be successful when button is Filter and form is non-empty" in {
      val params = Map(
        hasSelectedClients -> List("false"),
        search -> List.empty,
        filter -> List("abc"),
        clients -> List(client1.id, client2.id)
      )
      val boundForm =
        AddClientsToGroupForm.form(ButtonSelect.Filter).bindFromRequest(params)
      boundForm.value shouldBe Some(
        AddClientsToGroup(hasSelectedClients = false,
                          None,
                          Some("abc"),
                          Some(List(client1.id, client2.id)))
      )
    }

    "have errors when clients is empty and hiddenClients is false" in {
      val params =
        Map(hasSelectedClients -> List("false"),
            search -> List.empty,
            filter -> List.empty,
            clients -> List.empty)
      val boundForm = AddClientsToGroupForm.form().bindFromRequest(params)
      boundForm.errors shouldBe List(
        FormError("", List("error.select-clients.empty")))
    }

    "have errors when button is Filter and search and filter fields are empty" in {
      val params = Map(
        hasSelectedClients -> List("false"),
        search -> List.empty,
        filter -> List.empty,
        clients -> List(client1.id, client2.id)
      )
      val boundForm =
        AddClientsToGroupForm.form(ButtonSelect.Filter).bindFromRequest(params)
      boundForm.errors shouldBe List(
        FormError("", List("error.search-filter.empty")))
    }

    "have errors when button is Filter and search contains invalid characters" in {
      val params = Map(
        hasSelectedClients -> List("false"),
        search -> List("bad<search>"),
        filter -> List.empty,
        clients -> List(client1.id, client2.id)
      )
      val boundForm =
        AddClientsToGroupForm.form(ButtonSelect.Filter).bindFromRequest(params)
      boundForm.errors shouldBe List(
        FormError("search", List("error.search.invalid")))
    }

    "throw exception for invalid Button Select" in {
      val exception = intercept[RuntimeException] {
        AddClientsToGroupForm.form(null).bindFromRequest(Map.empty)
      }
      exception.getMessage shouldBe "invalid button null"

    }

  }

  "Unbind form" should {

    "give expected Map of data Continue" in {
      val model = AddClientsToGroup(hasSelectedClients = false,
                                    None,
                                    None,
                                    Some(List(client1.id, client2.id)))
      AddClientsToGroupForm
        .form(Continue)
        .mapping
        .unbind(model) shouldBe Map(
        "hasSelectedClients" -> "false",
        "clients[0]" -> client1.id,
        "clients[1]" -> client2.id
      )
    }

    "have errors  when button is continue and no clients" in {
      val params = Map(
        hasSelectedClients -> List("false"),
        search -> List.empty,
        filter -> List.empty,
        clients -> List.empty
      )
      val boundForm = AddClientsToGroupForm
        .form(ButtonSelect.Continue)
        .bindFromRequest(params)
      boundForm.hasErrors shouldBe true
      boundForm.errors.head.messages shouldBe Seq("error.select-clients.empty")
    }

    "give expected Map of data Filter" in {
      val model = AddClientsToGroup(hasSelectedClients = false,
                                    Option("Ab"),
                                    Option("Bc"),
                                    Some(List(client1.id)))
      AddClientsToGroupForm
        .form(Clear)
        .mapping
        .unbind(model) shouldBe Map(
        "hasSelectedClients" -> "false",
        "filter" -> "Bc",
        "search" -> "Ab",
        "clients[0]" -> client1.id
      )
    }

    "give expected Map of data Clear" in {
      val model =
        AddClientsToGroup(hasSelectedClients = false, None, None, None)
      AddClientsToGroupForm
        .form(Filter)
        .mapping
        .unbind(model) shouldBe Map(
        "hasSelectedClients" -> "false"
      )
    }
  }

}
