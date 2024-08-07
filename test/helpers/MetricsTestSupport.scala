/*
 * Copyright 2023 HM Revenue & Customs
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

import com.codahale.metrics.MetricRegistry
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Assertion, Suite}
import play.api.Application
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.collection.JavaConverters._

trait MetricsTestSupport {
  self: Suite with Matchers =>

  def app: Application

  implicit val metrics: Metrics = app.injector.instanceOf[Metrics]

  private var metricsRegistry: MetricRegistry = _

  def givenCleanMetricRegistry(): Unit = {
    val registry = metrics.defaultRegistry
    for (metric <- registry.getMetrics.keySet().iterator().asScala)
      registry.remove(metric)
    metricsRegistry = registry
  }

  def verifyTimerExistsAndBeenUpdated(metric: String): Assertion = {
    val timers = metricsRegistry.getTimers
    val metrics = timers.get(s"Timer-$metric")
    if (metrics == null)
      throw new Exception(s"Metric [$metric] not found, try one of ${timers.keySet()}")
    metrics.getCount should be >= 1L
  }

}
