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

package controllers

import helpers.BaseSpec
import play.api.Application
import play.api.http.Status.OK
import play.api.test.FakeRequest

class ArnAllowListControllerSpec extends BaseSpec {

  override implicit lazy val fakeApplication: Application = appBuilder.build()

  val controller: ArnAllowListController = fakeApplication.injector.instanceOf[ArnAllowListController]

  "Is ARN Allowed" should {
    s"return $OK" in {
      val result = controller.isArnAllowed()(FakeRequest())
      status(result) shouldBe OK
    }
  }

}
