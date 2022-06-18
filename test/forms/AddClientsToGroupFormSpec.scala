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

import models.DisplayClient
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json

import java.util.Base64

class AddClientsToGroupFormSpec extends AnyWordSpec
  with Matchers
  with GuiceOneAppPerSuite {

  val clients = "clients[]"

  "CreateGroupFrom binding" should {

    val client1 = DisplayClient("123", "JD", "VAT",s"id-key-1")
    val client2 = DisplayClient("456", "HH", "CGT", s"id-key-2")

    val encode: DisplayClient => String = client => Base64.getEncoder.encodeToString(Json.toJson(client).toString.getBytes)

    "be fillable with a list of DisplayClients" in {
      val validatedForm = AddClientsToGroupForm.form().fill(List(client1, client2))
      validatedForm.hasErrors shouldBe false
      validatedForm.value shouldBe Option(List(client1, client2))
    }

    "be successful when non-empty" in {
      val params: Map[String, List[String]] = Map(clients -> List(encode(client1), encode(client2)))
      val boundForm = AddClientsToGroupForm.form().bindFromRequest(params)
      boundForm.value shouldBe Some(List(client1, client2))
    }

    "have errors when empty" in {
      val params: Map[String, List[String]] = Map(clients -> List.empty[String])
      val boundForm = AddClientsToGroupForm.form().bindFromRequest(params)
      boundForm.value shouldBe None
    }

  }

}
