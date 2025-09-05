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

package models

import models.clientidtypes.{CbcId, CgtRef, MtdItId, PlrId, PptRef, Urn, Utr, Vrn}
import play.api.libs.json.Format
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.domain.SimpleObjectReads
import uk.gov.hmrc.domain.SimpleObjectWrites
import uk.gov.hmrc.domain.TaxIdentifier

sealed abstract class Service(
  val id: String,
  val invitationIdPrefix: Char,
  val enrolmentKey: String,
  val supportedSuppliedClientIdType: ClientIdType[_ <: TaxIdentifier],
  val supportedClientIdType: ClientIdType[_ <: TaxIdentifier]
) {
  override def toString: String = this.id

  override def equals(that: Any): Boolean =
    that match {
      case that: Service => this.id.equals(that.id)
      case _             => false
    }
}

object Service {

  val HMRCMTDIT = "HMRC-MTD-IT"
  val HMRCPIR = "PERSONAL-INCOME-RECORD"
  val HMRCMTDVAT = "HMRC-MTD-VAT"
  val HMRCTERSORG = "HMRC-TERS-ORG"
  val HMRCTERSNTORG = "HMRC-TERSNT-ORG"
  val HMRCCGTPD = "HMRC-CGT-PD"
  val HMRCPPTORG = "HMRC-PPT-ORG"
  val HMRCCBCORG = "HMRC-CBC-ORG"
  val HMRCCBCNONUKORG = "HMRC-CBC-NONUK-ORG"
  val HMRCPILLAR2ORG = "HMRC-PILLAR2-ORG"
  val HMRCMTDITSUPP = "HMRC-MTD-IT-SUPP"

  case object MtdIt extends Service(
        id = "HMRC-MTD-IT",
        invitationIdPrefix = 'A',
        enrolmentKey = "HMRC-MTD-IT",
        supportedSuppliedClientIdType = NinoType,
        supportedClientIdType = MtdItIdType
      )

  case object PersonalIncomeRecord extends Service(
        id = "PERSONAL-INCOME-RECORD",
        invitationIdPrefix = 'B',
        enrolmentKey = "HMRC-NI",
        supportedSuppliedClientIdType = NinoType,
        supportedClientIdType = NinoType
      )

  case object Vat extends Service(
        id = "HMRC-MTD-VAT",
        invitationIdPrefix = 'C',
        enrolmentKey = "HMRC-MTD-VAT",
        supportedSuppliedClientIdType = VrnType,
        supportedClientIdType = VrnType
      )

  case object Trust extends Service(
        id = "HMRC-TERS-ORG",
        invitationIdPrefix = 'D',
        enrolmentKey = "HMRC-TERS-ORG",
        supportedSuppliedClientIdType = UtrType,
        supportedClientIdType = UtrType
      )

  case object TrustNT extends Service(
        id = "HMRC-TERSNT-ORG",
        invitationIdPrefix = 'F',
        enrolmentKey = "HMRC-TERSNT-ORG",
        supportedSuppliedClientIdType = UrnType,
        supportedClientIdType = UrnType
      )

  case object CapitalGains extends Service(
        id = "HMRC-CGT-PD",
        invitationIdPrefix = 'E',
        enrolmentKey = "HMRC-CGT-PD",
        supportedSuppliedClientIdType = CgtRefType,
        supportedClientIdType = CgtRefType
      )

  case object Ppt extends Service(
        id = "HMRC-PPT-ORG",
        invitationIdPrefix = 'G',
        enrolmentKey = "HMRC-PPT-ORG",
        supportedSuppliedClientIdType = PptRefType,
        supportedClientIdType = PptRefType
      )

  case object Cbc extends Service(
        id = "HMRC-CBC-ORG",
        invitationIdPrefix = 'H',
        enrolmentKey = "HMRC-CBC-ORG",
        supportedSuppliedClientIdType = CbcIdType,
        supportedClientIdType = CbcIdType
      )

  case object CbcNonUk extends Service(
        id = "HMRC-CBC-NONUK-ORG",
        invitationIdPrefix = 'J',
        enrolmentKey = "HMRC-CBC-NONUK-ORG",
        supportedSuppliedClientIdType = CbcIdType,
        supportedClientIdType = CbcIdType
      )

  case object Pillar2 extends Service(
        id = "HMRC-PILLAR2-ORG",
        invitationIdPrefix = 'K',
        enrolmentKey = "HMRC-PILLAR2-ORG",
        supportedSuppliedClientIdType = PlrIdType,
        supportedClientIdType = PlrIdType
      )

  case object MtdItSupp extends Service(
        id = "HMRC-MTD-IT-SUPP",
        invitationIdPrefix = 'L',
        enrolmentKey = "HMRC-MTD-IT-SUPP",
        supportedSuppliedClientIdType = NinoType,
        supportedClientIdType = MtdItIdType
      )

  val supportedServices: Seq[Service] =
    Seq(MtdIt, Vat, PersonalIncomeRecord, Trust, TrustNT, CapitalGains, Ppt, Cbc, CbcNonUk, Pillar2, MtdItSupp)

  def findById(id: String): Option[Service] = supportedServices.find(_.id == id)

  def forId(id: String): Service = findById(id).getOrElse(throw new Exception("Not a valid service id: " + id))

  def apply(id: String): Service = forId(id)
  def unapply(service: Service): Option[String] = Some(service.id)

  val reads = new SimpleObjectReads[Service]("id", Service.apply)
  val writes = new SimpleObjectWrites[Service](_.id)
  implicit val format: Format[Service] = Format(reads, writes)

}

sealed abstract class ClientIdType[+T <: TaxIdentifier](
  val clazz: Class[_],
  val id: String,
  val enrolmentId: String,
  val createUnderlying: String => T
)

case object NinoType
    extends ClientIdType(clazz = classOf[Nino], id = "ni", enrolmentId = "NINO", createUnderlying = Nino.apply)

case object MtdItIdType extends ClientIdType(
      clazz = classOf[MtdItId],
      id = "MTDITID",
      enrolmentId = "MTDITID",
      createUnderlying = MtdItId.apply
    )

case object VrnType
    extends ClientIdType(clazz = classOf[Vrn], id = "vrn", enrolmentId = "VRN", createUnderlying = Vrn.apply)

case object UtrType
    extends ClientIdType(clazz = classOf[Utr], id = "utr", enrolmentId = "SAUTR", createUnderlying = Utr.apply)

case object UrnType
    extends ClientIdType(clazz = classOf[Urn], id = "urn", enrolmentId = "URN", createUnderlying = Urn.apply)

case object CgtRefType extends ClientIdType(
      clazz = classOf[CgtRef],
      id = "CGTPDRef",
      enrolmentId = "CGTPDRef",
      createUnderlying = CgtRef.apply
    )

case object PptRefType
    extends ClientIdType(
      clazz = classOf[PptRef],
      id = "EtmpRegistrationNumber",
      enrolmentId = "EtmpRegistrationNumber",
      createUnderlying = PptRef.apply
    )

case object CbcIdType
    extends ClientIdType(clazz = classOf[CbcId], id = "cbcId", enrolmentId = "cbcId", createUnderlying = CbcId.apply)

case object PlrIdType
    extends ClientIdType(clazz = classOf[PlrId], id = "PLRID", enrolmentId = "PLRID", createUnderlying = PlrId.apply)
