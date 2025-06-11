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

package helpers

import izumi.reflect.Tag
import org.scalamock.scalatest.MockFactory
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.libs.ws.BodyWritable
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}

import java.net.URL
import scala.annotation.unused
import scala.concurrent.{ExecutionContext, Future}

trait HttpClientMocks extends AnyWordSpec with MockFactory {
  val mockRequestBuilder = mock[RequestBuilder]

  def expectHttpClientGet[A](response: A)(implicit mockHttpClient: HttpClientV2, @unused hc: HeaderCarrier): Unit = {
    (mockHttpClient
      .get(_: URL)(_: HeaderCarrier))
      .expects(*, *)
      .returns(mockRequestBuilder)

    (mockRequestBuilder
      .execute[A](_: HttpReads[A], _: ExecutionContext))
      .expects(*, *)
      .returns(Future.successful(response))
  }

  def expectHttpClientGetWithUrl[A](expectedUrl: URL, response: A)(implicit
    mockHttpClient: HttpClientV2,
    @unused hc: HeaderCarrier
  ): Unit = {
    (mockHttpClient
      .get(_: URL)(_: HeaderCarrier))
      .expects(expectedUrl, *)
      .returns(mockRequestBuilder)

    (mockRequestBuilder
      .execute[A](_: HttpReads[A], _: ExecutionContext))
      .expects(*, *)
      .returns(Future.successful(response))
  }

  def expectHttpClientGetWithUrlPathOnly[A](expectedUrlPath: String, response: A)(implicit
    mockHttpClient: HttpClientV2,
    @unused hc: HeaderCarrier
  ): Unit = {

    def stripQuery(url: URL): String = s"${url.getProtocol}://${url.getHost}:${url.getPort}${url.getPath}"

    (mockHttpClient
      .get(_: URL)(_: HeaderCarrier))
      .expects(where((url: URL, _: HeaderCarrier) => stripQuery(url) == expectedUrlPath))
      .returns(mockRequestBuilder)

    (mockRequestBuilder
      .execute[A](_: HttpReads[A], _: ExecutionContext))
      .expects(*, *)
      .returns(Future.successful(response))
  }

  def expectHttpClientPostEmpty[A](
    response: A
  )(implicit mockHttpClient: HttpClientV2, @unused hc: HeaderCarrier): Unit = {
    (mockHttpClient
      .post(_: URL)(_: HeaderCarrier))
      .expects(*, *)
      .returns(mockRequestBuilder)

    (mockRequestBuilder
      .execute[A](_: HttpReads[A], _: ExecutionContext))
      .expects(*, *)
      .returns(Future.successful(response))
  }

  def expectHttpClientPost[I, O](expectedUrl: URL, input: I, output: O)(implicit
    mockHttpClient: HttpClientV2,
    @unused hc: HeaderCarrier,
    @unused ec: ExecutionContext,
    writes: Writes[I]
  ): Unit = {
    (mockHttpClient
      .post(_: URL)(_: HeaderCarrier))
      .expects(expectedUrl, *)
      .returns(mockRequestBuilder)

    (mockRequestBuilder
      .withBody[JsValue](_: JsValue)(_: BodyWritable[JsValue], _: Tag[JsValue], _: ExecutionContext))
      .expects(Json.toJson(input), *, *, *)
      .returns(mockRequestBuilder)

    (mockRequestBuilder
      .execute[O](_: HttpReads[O], _: ExecutionContext))
      .expects(*, *)
      .returns(Future.successful(output))
  }

  def expectHttpClientPut[I, O](expectedUrl: URL, input: I, output: O)(implicit
    mockHttpClient: HttpClientV2,
    @unused hc: HeaderCarrier,
    @unused ec: ExecutionContext
  ): Unit = {
    (mockHttpClient
      .put(_: URL)(_: HeaderCarrier))
      .expects(expectedUrl, *)
      .returns(mockRequestBuilder)

    (mockRequestBuilder
      .execute[O](_: HttpReads[O], _: ExecutionContext))
      .expects(*, *)
      .returns(Future.successful(output))
  }

  def expectHttpClientPatch[I, O](expectedUrl: URL, input: I, output: O)(implicit
    mockHttpClient: HttpClientV2,
    @unused hc: HeaderCarrier,
    @unused ec: ExecutionContext,
    writes: Writes[I]
  ): Unit = {
    (mockHttpClient
      .patch(_: URL)(_: HeaderCarrier))
      .expects(expectedUrl, *)
      .returns(mockRequestBuilder)

    (mockRequestBuilder
      .withBody[JsValue](_: JsValue)(_: BodyWritable[JsValue], _: Tag[JsValue], _: ExecutionContext))
      .expects(Json.toJson(input), *, *, *)
      .returns(mockRequestBuilder)

    (mockRequestBuilder
      .execute[O](_: HttpReads[O], _: ExecutionContext))
      .expects(*, *)
      .returns(Future.successful(output))
  }

  def expectHttpClientDelete[O](expectedUrl: URL, output: O)(implicit
    mockHttpClient: HttpClientV2,
    @unused hc: HeaderCarrier
  ): Unit = {
    (mockHttpClient
      .delete(_: URL)(_: HeaderCarrier))
      .expects(expectedUrl, *)
      .returns(mockRequestBuilder)

    (mockRequestBuilder
      .execute[O](_: HttpReads[O], _: ExecutionContext))
      .expects(*, *)
      .returns(Future.successful(output))
  }
}
