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

package utils

import models.DisplayClient

object FilterUtils {
  //TODO move to tests, unused in code
  // Ideally we won't need this logic here any more after the backend becomes able to serve all client lists as paginated.
  def filterClients(clients: Seq[DisplayClient], search: Option[String], filter: Option[String]): Seq[DisplayClient] = {
    clients
      .filter(_.name.toLowerCase.contains(search.getOrElse("").toLowerCase))
      .filter(dc =>
        filter match {
          case None => true
          case Some(empty) if empty.trim.isEmpty => true
          case Some(fltr) => dc.taxService.equalsIgnoreCase(fltr) || (fltr == "TRUST" && dc.taxService.startsWith("HMRC-TERS"))
        }
      )
  }
}
