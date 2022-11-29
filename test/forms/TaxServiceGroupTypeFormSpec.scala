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

import models.TaxServiceGroupType
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class TaxServiceGroupTypeFormSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite {

  "form binding" should{
    "work correctly" in {
      val params = Map("addAutomatically" -> Seq("true"), "taxType" -> Seq("HMRC-MTD-VAT"))
      val boundForm = TaxServiceGroupTypeForm.form.bindFromRequest(params)
      boundForm.value shouldBe Some(TaxServiceGroupType("HMRC-MTD-VAT", true))
    }
  }

  "form unbinding" should{
    "work correctly" in {
      val form = TaxServiceGroupTypeForm.form
      val unboundForm = form.mapping.unbind(TaxServiceGroupType("HMRC-MTD-VAT", true))
      unboundForm shouldBe Map("taxType" -> "HMRC-MTD-VAT", "addAutomatically" -> "true")
    }
  }

}
