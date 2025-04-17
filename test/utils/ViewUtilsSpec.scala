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

package utils

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.i18n.Messages
import play.api.test.Helpers
import uk.gov.hmrc.agentmtdidentifiers.model.Service._

class ViewUtilsSpec extends AnyWordSpec with Matchers {

  implicit val msgs: Messages = Helpers.stubMessages()

  "displayTaxServiceFromServiceKey" should {
    s"return correct message for $HMRCMTDIT" in {
      ViewUtils.displayTaxServiceFromServiceKey(HMRCMTDIT) shouldBe "tax-service.mdt-it"
    }
    s"return correct message for $HMRCMTDITSUPP" in {
      ViewUtils.displayTaxServiceFromServiceKey(HMRCMTDITSUPP) shouldBe "tax-service.mdt-it"
    }
    s"return correct message for $HMRCMTDVAT" in {
      ViewUtils.displayTaxServiceFromServiceKey(HMRCMTDVAT) shouldBe "tax-service.vat"
    }
    s"return correct message for $HMRCPILLAR2ORG" in {
      ViewUtils.displayTaxServiceFromServiceKey(HMRCPILLAR2ORG) shouldBe "tax-service.pillar2"
    }
    s"return correct message for $HMRCPPTORG" in {
      ViewUtils.displayTaxServiceFromServiceKey(HMRCPPTORG) shouldBe "tax-service.ppt"
    }
    s"return correct message for $HMRCCGTPD" in {
      ViewUtils.displayTaxServiceFromServiceKey(HMRCCGTPD) shouldBe "tax-service.cgt"
    }
    s"return correct message for $HMRCTERSORG" in {
      ViewUtils.displayTaxServiceFromServiceKey(HMRCTERSORG) shouldBe "tax-service.trusts"
    }
    s"return correct message for $HMRCTERSNTORG" in {
      ViewUtils.displayTaxServiceFromServiceKey(HMRCTERSNTORG) shouldBe "tax-service.trusts"
    }
    s"return correct message for $HMRCCBCORG" in {
      ViewUtils.displayTaxServiceFromServiceKey(HMRCCBCORG) shouldBe "tax-service.cbc"
    }
    s"return correct message for $HMRCCBCNONUKORG" in {
      ViewUtils.displayTaxServiceFromServiceKey(HMRCCBCNONUKORG) shouldBe "tax-service.cbc"
    }
  }
}
