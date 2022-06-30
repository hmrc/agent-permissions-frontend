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

package helpers

import org.scalamock.scalatest.MockFactory
import play.api.libs.json.Writes
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads}

import scala.concurrent.{ExecutionContext, Future}

trait HttpClientMocks extends MockFactory {

  def mockHttpGet[A](response: A)(implicit mockHttpClient: HttpClient): Unit = {
    (mockHttpClient.GET[A](_: String, _: Seq[(String, String)], _: Seq[(String, String)])
      (_: HttpReads[A], _: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *, *, *)
      .returns(Future.successful(response))
  }

  def mockHttpGetWithUrl[A](expectedUrl: String, response: A)(implicit mockHttpClient: HttpClient): Unit = {
    (mockHttpClient.GET[A](_: String, _: Seq[(String, String)], _: Seq[(String, String)])
      (_: HttpReads[A], _: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *, *, *)
      .returns(Future.successful(response))
  }

  def mockHttpPostEmpty[A](response: A)(implicit mockHttpClient: HttpClient): Unit = {
    (mockHttpClient.POSTEmpty(_: String, _: Seq[(String, String)])
    (_: HttpReads[A], _: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *, *, *)
      .returns(Future.successful(response))
  }

  def mockHttpPost[I, O](url: String, input: I, output: O)(implicit mockHttpClient: HttpClient): Unit = {
    (mockHttpClient.POST(_: String, _: I, _: Seq[(String, String)])
    (_: Writes[I], _: HttpReads[O], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, input, *, *, *, *, *)
      .returns(Future.successful(output))
  }

  def mockHttpPATCH[I, O](url: String, input: I, output: O)(implicit mockHttpClient: HttpClient): Unit = {
    (mockHttpClient.PATCH(_: String, _: I, _: Seq[(String, String)])
    (_: Writes[I], _: HttpReads[O], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, input, *, *, *, *, *)
      .returns(Future.successful(output))
  }
}
