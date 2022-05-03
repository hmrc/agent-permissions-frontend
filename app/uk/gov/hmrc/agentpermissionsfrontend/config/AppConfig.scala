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

package uk.gov.hmrc.agentpermissionsfrontend.config

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class AppConfig @Inject()(servicesConfig: ServicesConfig) {
  val welshLanguageSupportEnabled: Boolean = servicesConfig.getBoolean("features.welsh-language-support")
  val contactFrontendBaseUrl: String = servicesConfig.baseUrl("contact-frontend")
  val contactFrontendServiceId: String = servicesConfig.getString("contact-frontend.serviceId")
  val betaFeedbackUrl: String = s"$contactFrontendBaseUrl/contact/beta-feedback?service=$contactFrontendServiceId"
  val basGatewayUrl: String = servicesConfig.getString("microservice.services.bas-gateway.external-url")
  val loginContinueUrl: String = servicesConfig.getString("microservice.services.bas-gateway.login-continue")
}
