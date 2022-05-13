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

import helpers.BaseISpec
import play.api.Application
import play.api.http.Status.SEE_OTHER
import play.api.test.Helpers._

class RootControllerSpec extends BaseISpec {

  override implicit lazy val fakeApplication: Application = appBuilder.build()

  val controller = fakeApplication.injector.instanceOf[RootController]

  "root controller" should {
    "have logic to decide where to go!" in {
      val result = controller.start()(request)

      status(result) shouldBe SEE_OTHER

      redirectLocation(result).get shouldBe routes.OptInController.start.url
    }
  }

}
