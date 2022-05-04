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

package uk.gov.hmrc.agentpermissionsfrontend.helpers

import org.scalatest.{BeforeAndAfterEach, Suite}
import uk.gov.hmrc.mongo.MongoSpecSupport

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.concurrent._
import scala.language.postfixOps

trait MongoSupport extends MongoSpecSupport with BeforeAndAfterEach {
  me: Suite =>

  def dropMongoDb()(implicit ec: ExecutionContext = global): Unit =
    Await.result(mongo().drop(), 5 seconds)
}
