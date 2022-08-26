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

package config

import com.google.inject.ImplementedBy
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration

@ImplementedBy(classOf[AppConfigImpl])
trait AppConfig {
  def servicesConfig: ServicesConfig
  def appName: String
  def welshLanguageSupportEnabled: Boolean
  def contactFrontendBaseUrl: String
  def contactFrontendServiceId: String
  def betaFeedbackUrl: String
  def basGatewayUrl: String
  def loginContinueUrl: String
  def agentServicesAccountExternalUrl: String
  def agentServicesAccountManageAccountPath: String
  def agentServicesAccountManageAccountUrl: String
  def agentPermissionsBaseUrl: String
  def agentUserClientDetailsBaseUrl: String
  def sessionCacheExpiryDuration: Duration
}

@Singleton
class AppConfigImpl @Inject()(val servicesConfig: ServicesConfig)
    extends AppConfig {
  lazy val appName: String = servicesConfig.getString("appName")
  lazy val welshLanguageSupportEnabled: Boolean =
    servicesConfig.getBoolean("features.welsh-language-support")
  lazy val contactFrontendBaseUrl: String =
    servicesConfig.baseUrl("contact-frontend")
  lazy val contactFrontendServiceId: String =
    servicesConfig.getString("contact-frontend.serviceId")
  lazy val betaFeedbackUrl: String =
    s"$contactFrontendBaseUrl/contact/beta-feedback?service=$contactFrontendServiceId"
  lazy val basGatewayUrl: String =
    servicesConfig.getString("microservice.services.bas-gateway.external-url")
  lazy val loginContinueUrl: String =
    servicesConfig.getString("microservice.services.bas-gateway.login-continue")
  lazy val agentServicesAccountExternalUrl: String = servicesConfig.getString(
    "microservice.services.agent-services-account-frontend.external-url")
  lazy val agentServicesAccountManageAccountPath: String = servicesConfig.getString(
    "microservice.services.agent-services-account-frontend.manage-account-path")
  lazy val agentServicesAccountManageAccountUrl: String = agentServicesAccountExternalUrl + agentServicesAccountManageAccountPath
  lazy val agentPermissionsBaseUrl: String =
    servicesConfig.baseUrl("agent-permissions")
  lazy val agentUserClientDetailsBaseUrl: String =
    servicesConfig.baseUrl("agent-user-client-details")
  lazy val sessionCacheExpiryDuration: Duration =
    servicesConfig.getDuration("mongodb.cache.expiry")
}
