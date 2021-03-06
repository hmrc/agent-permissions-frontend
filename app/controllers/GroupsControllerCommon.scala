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

import models.ButtonSelect

trait GroupsControllerCommon {

  def buttonClickedByUserOnFilterFormPage(
      formData: Option[Map[String, Seq[String]]]): ButtonSelect = {
    formData.fold(ButtonSelect.Continue: ButtonSelect)(
      someMap =>
        ButtonSelect(
          someMap
            .getOrElse("continue",
                       someMap.getOrElse(
                         "submitFilter",
                         someMap.getOrElse(
                           "submitClear",
                           throw new RuntimeException(
                             "invalid button value for submitAddClients"))))
            .last
      )
    )
  }
}
