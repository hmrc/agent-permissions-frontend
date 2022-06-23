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

import models.{AddClientsToGroup, ButtonSelect, DisplayClient}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.data.FormError
import play.api.libs.json.Json

import java.util.Base64

class AddClientsToGroupFormSpec extends AnyWordSpec
  with Matchers
  with GuiceOneAppPerSuite {

  val hasSelectedClients = "hasSelectedClients"
  val search = "search"
  val filter = "filter"
  val clients = "clients[]"

  "CreateGroupFrom binding" should {

    val client1 = DisplayClient("123", "JD", "VAT",s"id-key-1")
    val client2 = DisplayClient("456", "HH", "CGT", s"id-key-2")

    val encode: DisplayClient => String = client => Base64.getEncoder.encodeToString(Json.toJson(client).toString.getBytes)

    "be fillable with a AddClientsToGroup" in {
      val validatedForm = AddClientsToGroupForm.form().fill(AddClientsToGroup(false, None, None, Some(List(client1, client2))))
      validatedForm.hasErrors shouldBe false
      validatedForm.value shouldBe Option(AddClientsToGroup(false, None, None, Some(List(client1, client2))))
    }

    "be successful when clients are non-empty" in {
      val params = Map(
        hasSelectedClients -> List("false"),
        search -> List.empty,
        filter -> List.empty,
        clients -> List(encode(client1), encode(client2)
        )
      )
      val boundForm = AddClientsToGroupForm.form().bindFromRequest(params)
      boundForm.value shouldBe Some(AddClientsToGroup(false, None, None, Some(List(client1, client2))))
    }

    "be successful when button is Clear and form is empty" in {
      val params = Map(
        hasSelectedClients -> List("false"),
        search -> List.empty,
        filter -> List.empty,
        clients -> List.empty
      )
      val boundForm = AddClientsToGroupForm.form(ButtonSelect.Clear).bindFromRequest(params)
      boundForm.value shouldBe Some(AddClientsToGroup(false, None, None, None))
    }

    "have errors when clients is empty and hiddenClients is false" in {
      val params = Map(
        hasSelectedClients -> List("false"),
        search -> List.empty,
        filter -> List.empty,
        clients -> List.empty)
      val boundForm = AddClientsToGroupForm.form().bindFromRequest(params)
      boundForm.errors shouldBe List(FormError("clients", List("error.select-clients.empty")))
    }

    "have errors when button is Filter and search and filter fields are empty" in {
      val params = Map(
        hasSelectedClients -> List("false"),
        search -> List.empty,
        filter -> List.empty,
        clients -> List(encode(client1), encode(client2)))
      val boundForm = AddClientsToGroupForm.form(ButtonSelect.Filter).bindFromRequest(params)
      boundForm.errors shouldBe List(FormError("", List("error.search-filter.empty")))
    }
  }

}
