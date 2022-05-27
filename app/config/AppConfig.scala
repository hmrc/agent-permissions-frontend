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
  val servicesConfig: ServicesConfig
  val appName: String
  val welshLanguageSupportEnabled: Boolean
  val contactFrontendBaseUrl: String
  val contactFrontendServiceId: String
  val betaFeedbackUrl: String
  val basGatewayUrl: String
  val loginContinueUrl: String
  val agentServicesAccountExternalUrl: String
  val agentServicesAccountManageAccountPath: String
  val agentServicesAccountManageAccountUrl: String
  val agentPermissionsBaseUrl: String
  val agentUserClientDetailsBaseUrl: String
  val sessionCacheExpiryDuration: Duration
}

@Singleton
class AppConfigImpl @Inject()(val servicesConfig: ServicesConfig) extends AppConfig {
  val appName = servicesConfig.getString("appName")
  val welshLanguageSupportEnabled: Boolean = servicesConfig.getBoolean("features.welsh-language-support")
  val contactFrontendBaseUrl: String = servicesConfig.baseUrl("contact-frontend")
  val contactFrontendServiceId: String = servicesConfig.getString("contact-frontend.serviceId")
  val betaFeedbackUrl: String = s"$contactFrontendBaseUrl/contact/beta-feedback?service=$contactFrontendServiceId"
  val basGatewayUrl: String = servicesConfig.getString("microservice.services.bas-gateway.external-url")
  val loginContinueUrl: String = servicesConfig.getString("microservice.services.bas-gateway.login-continue")
  val agentServicesAccountExternalUrl: String = servicesConfig.getString("microservice.services.agent-services-account-frontend.external-url")
  val agentServicesAccountManageAccountPath: String = servicesConfig.getString("microservice.services.agent-services-account-frontend.manage-account-path")
  val agentServicesAccountManageAccountUrl = agentServicesAccountExternalUrl + agentServicesAccountManageAccountPath
  val agentPermissionsBaseUrl: String = servicesConfig.baseUrl("agent-permissions")
  val agentUserClientDetailsBaseUrl: String = servicesConfig.baseUrl("agent-user-client-details")
  val sessionCacheExpiryDuration: Duration = servicesConfig.getDuration("mongodb.cache.expiry")
}
