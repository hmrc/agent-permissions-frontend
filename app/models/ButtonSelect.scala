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

package models

sealed trait ButtonSelect{
  val value: String
}

object ButtonSelect {

  case object Continue extends ButtonSelect {
    override val value = "continue"
  }
  case object Filter extends ButtonSelect {
    override val value = "filter"
  }
  case object Clear extends ButtonSelect {
    override val value = "clear"
  }

 def apply(s: String): ButtonSelect = s match {
   case "continue" => Continue
   case "filter"  => Filter
   case "clear" => Clear
 }

  implicit def unapply(o: ButtonSelect): String = o match {
   case Continue => "continue"
   case Filter => "filter"
   case Clear => "clear"
 }
}
