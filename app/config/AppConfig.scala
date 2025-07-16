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

package config

import com.google.inject.ImplementedBy
import play.api.{Environment, Mode}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration

@ImplementedBy(classOf[AppConfigImpl])
trait AppConfig {
  def servicesConfig: ServicesConfig
  def appName: String
  def welshLanguageSupportEnabled: Boolean
  val cbcEnabled: Boolean
  val pillar2Enabled: Boolean
  def contactFrontendBaseUrl: String
  def contactFrontendServiceId: String
  def betaFeedbackUrl: String
  def basGatewayUrl: String
  def loginContinueUrl: String
  def agentServicesAccountExternalUrl: String
  def agentServicesAccountManageAccountPath: String
  def agentServicesAccountManageAccountUrl: String
  def agentServicesAccountYourAssistantAccountUrl: String
  def agentPermissionsBaseUrl: String
  def agentUserClientDetailsBaseUrl: String
  def agentAssuranceBaseUrl: String
  def sessionCacheExpiryDuration: Duration
  def userTimeoutCountdown: Int
  def userTimeout: Int
  def isTest: Boolean
  val signOut: String
  val signInUrl: String
  val selfExternalUrl: String
}

@Singleton
class AppConfigImpl @Inject() (val servicesConfig: ServicesConfig, environment: Environment) extends AppConfig {
  def isTest: Boolean = environment.mode == Mode.Test
  lazy val appName: String = servicesConfig.getString("appName")
  lazy val welshLanguageSupportEnabled: Boolean = servicesConfig.getBoolean("features.welsh-language-support")
  override val cbcEnabled: Boolean = servicesConfig.getBoolean("features.enable-cbc")
  override val pillar2Enabled: Boolean = servicesConfig.getBoolean("features.enable-pillar2")

  lazy val contactFrontendBaseUrl: String = servicesConfig.getString("contact-frontend.external-url")
  lazy val contactFrontendServiceId: String = servicesConfig.getString("contact-frontend.serviceId")
  lazy val betaFeedbackUrl: String = s"$contactFrontendBaseUrl/contact/beta-feedback?service=$contactFrontendServiceId"
  lazy val basGatewayUrl: String = servicesConfig.getString("microservice.services.bas-gateway.external-url")
  lazy val loginContinueUrl: String = servicesConfig.getString("microservice.services.bas-gateway.login-continue")

  private val basGatewayFrontendExternalUrl: String = servicesConfig.getString("bas-gateway-frontend.external-url")
  private val signOutPath: String = servicesConfig.getString("bas-gateway-frontend.sign-out.path")
  private val signInPath: String = servicesConfig.getString("bas-gateway-frontend.sign-in.path")
  override lazy val signOut: String = s"$basGatewayFrontendExternalUrl$signOutPath"
  override lazy val signInUrl: String = s"$basGatewayFrontendExternalUrl$signInPath"
  override val selfExternalUrl: String = servicesConfig.getString("self.external-url")

  val agentServicesAccountExternalUrl: String =
    servicesConfig.getString("microservice.services.agent-services-account-frontend.external-url")
  val agentServicesAccountManageAccountPath: String =
    servicesConfig.getString("microservice.services.agent-services-account-frontend.manage-account-path")
  val agentServicesAccountYourAccountPath: String =
    servicesConfig.getString("microservice.services.agent-services-account-frontend.your-account-path")
  val agentServicesAccountManageAccountUrl: String =
    agentServicesAccountExternalUrl + agentServicesAccountManageAccountPath
  val agentServicesAccountYourAssistantAccountUrl: String =
    agentServicesAccountExternalUrl + agentServicesAccountYourAccountPath
  val agentPermissionsBaseUrl: String = servicesConfig.baseUrl("agent-permissions")
  val agentUserClientDetailsBaseUrl: String = servicesConfig.baseUrl("agent-user-client-details")
  val agentAssuranceBaseUrl: String = servicesConfig.baseUrl("agent-assurance")

  val sessionCacheExpiryDuration: Duration = servicesConfig.getDuration("mongodb.cache.expiry")

  lazy val userTimeout: Int = servicesConfig.getInt("timeout.duration")
  lazy val userTimeoutCountdown: Int = servicesConfig.getInt("timeout.countDown")
}
