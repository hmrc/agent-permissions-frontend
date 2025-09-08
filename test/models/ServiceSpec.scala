/*
 * Copyright 2025 HM Revenue & Customs
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

package models

import models.Service.unapply
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json

class ServiceSpec extends AnyFlatSpec with Matchers {

  it should "create model if valid Service ID supplied" in {
    val expectedModel: Service = Service("HMRC-MTD-IT")
    val json = Json.obj("id" -> "HMRC-MTD-IT")

    json.as[Service] shouldBe expectedModel
    expectedModel.toString shouldBe "HMRC-MTD-IT"
  }

  it should "unapply to Service ID" in {
    val model: Service = Service("HMRC-MTD-IT")
    unapply(model).get shouldBe "HMRC-MTD-IT"
  }

  it should "override equals as expected" in {
    val model: Service = Service("HMRC-MTD-IT")
    model.equals(Service("HMRC-MTD-IT")) shouldBe true
    model.equals("") shouldBe false
  }

  it should "Throw execption if invalid Service ID supplied" in {
    val json = Json.obj("id" -> "HMRC-XXX")
    val ex = intercept[RuntimeException] {
      json.as[Service]
    }
    ex.getMessage shouldBe "JsResultException(errors:List((,List(JsonValidationError(List(Not a valid service id: HMRC-XXX),List())))))"
  }
}
