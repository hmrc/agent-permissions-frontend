/*
 * Copyright 2024 HM Revenue & Customs
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

import controllers.*
import models.{DisplayClient, TeamMember}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import repository.SessionCacheRepository
import services.{SessionCacheService, SessionCacheServiceImpl}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, SymmetricCryptoFactory}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.cache.CacheItem
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class SessionCacheServiceISpec extends AnyWordSpec with Matchers with CleanMongoCollectionSupport {

  private val sessionId: String = UUID.randomUUID.toString

  private implicit val request: FakeRequest[AnyContent] = FakeRequest().withSession(
    SessionKeys.sessionId -> sessionId
  )

  private implicit val crypto: Encrypter & Decrypter = SymmetricCryptoFactory.aesGcmCrypto(
    "oaJdbtyXIUyd+hHefKbMUqtehotAG99pH0bqpkSuQ/Q="
  )

  private val sessionCacheRepository: SessionCacheRepository = new SessionCacheRepository(
    mongoComponent = mongoComponent,
    timestampSupport = new CurrentTimestampSupport()
  )

  private val sessionCacheService: SessionCacheService = new SessionCacheServiceImpl(
    sessionCacheRepository = sessionCacheRepository
  )

  private val displayClient: DisplayClient = DisplayClient(
    hmrcRef = "Ref123",
    name = "John Doe",
    taxService = "HMRC-MTD-VAT",
    enrolmentKeyExtra = "VRN"
  )

  private val displayClients: Seq[DisplayClient] = Seq.fill(2)(displayClient)

  private val teamMember: TeamMember = TeamMember(
    name = "John Doe",
    email = "test@email.com",
    userId = Some("userId123"),
    credentialRole = Some("User")
  )

  private val teamMembers: Seq[TeamMember] = Seq.fill(2)(teamMember)

  private val unassignedClientsGroups: Seq[String] = Seq("group 1", "group 2")

  private val expectedDisplayClientJson = Json.toJson(displayClient)(using DisplayClient.format)
  private val expectedDisplayClientsJson: JsValue = JsArray(
    displayClients.map(dc => Json.toJson(dc)(using DisplayClient.format))
  )

  private val expectedTeamMemberJson: JsValue = Json.toJson(teamMember)(using TeamMember.format)

  private val expectedTeamMembersJson: JsValue = JsArray(
    teamMembers.map(tm => Json.toJson(tm)(using TeamMember.format))
  )

  private val expectedUnassignedClientsGroupsJson: JsValue = Json.toJson(unassignedClientsGroups)

  "SessionCacheService" should {
    "encrypt and decrypt when storing and retrieving data" which {
      Seq(
        GROUP_NAME,
        CLIENT_SEARCH_INPUT,
        GROUP_SEARCH_INPUT,
        TEAM_MEMBER_SEARCH_INPUT,
        NAME_OF_GROUP_CREATED,
        GROUP_RENAMED_FROM,
        GROUP_DELETED_NAME,
        CLIENT_REFERENCE
      ).foreach { dataKey =>
        s"is for ${dataKey.unwrap}" in {
          await(sessionCacheService.put(dataKey, "John Doe"))

          val cacheItem: CacheItem = await(sessionCacheRepository.cacheRepo.collection.find().head())

          val jsonData: JsObject = cacheItem.data

          val extractedValue: String = (jsonData \ dataKey.unwrap).as[String]

          extractedValue should not be "John Doe" // should not be stored in plain text

          await(sessionCacheService.get(dataKey)) shouldBe Some("John Doe")
        }
      }

      s"is for ${CLIENT_TO_REMOVE.unwrap}" in {
        await(sessionCacheService.put(CLIENT_TO_REMOVE, displayClient))

        val cacheItem: CacheItem = await(sessionCacheRepository.cacheRepo.collection.find().head())

        val jsonData: JsObject = cacheItem.data

        val extractedValue: JsValue = (jsonData \ CLIENT_TO_REMOVE.unwrap).as[JsValue]

        // should not be the unencrypted version
        extractedValue should not be expectedDisplayClientJson

        await(sessionCacheService.get(CLIENT_TO_REMOVE)) shouldBe Some(displayClient)
      }

      Seq(SELECTED_CLIENTS, CURRENT_PAGE_CLIENTS).foreach { dataKey =>
        s"is for ${dataKey.unwrap}" in {
          await(sessionCacheService.put(dataKey, displayClients))

          val cacheItem: CacheItem = await(sessionCacheRepository.cacheRepo.collection.find().head())

          val jsonData: JsObject = cacheItem.data

          val extractedValue: JsValue = (jsonData \ dataKey.unwrap).as[JsValue]

          // should not be the unencrypted version
          extractedValue should not be expectedDisplayClientsJson

          await(sessionCacheService.get(dataKey)) shouldBe Some(displayClients)
        }
      }

      s"is for ${MEMBER_TO_REMOVE.unwrap}" in {
        await(sessionCacheService.put(MEMBER_TO_REMOVE, teamMember))

        val cacheItem: CacheItem = await(sessionCacheRepository.cacheRepo.collection.find().head())

        val jsonData: JsObject = cacheItem.data

        val extractedValue: JsValue = (jsonData \ MEMBER_TO_REMOVE.unwrap).as[JsValue]

        // should not be the unencrypted version
        extractedValue should not be expectedTeamMemberJson

        await(sessionCacheService.get(MEMBER_TO_REMOVE)) shouldBe Some(teamMember)
      }

      Seq(CURRENT_PAGE_TEAM_MEMBERS, SELECTED_TEAM_MEMBERS).foreach { dataKey =>
        s"is for ${dataKey.unwrap}" in {
          await(sessionCacheService.put(dataKey, teamMembers))

          val cacheItem: CacheItem = await(sessionCacheRepository.cacheRepo.collection.find().head())

          val jsonData: JsObject = cacheItem.data

          val extractedValue: JsValue = (jsonData \ dataKey.unwrap).as[JsValue]

          // should not be the unencrypted version
          extractedValue should not be expectedTeamMembersJson

          await(sessionCacheService.get(dataKey)) shouldBe Some(teamMembers)
        }
      }

      s"is for ${GROUPS_FOR_UNASSIGNED_CLIENTS.unwrap}" in {
        await(sessionCacheService.put(GROUPS_FOR_UNASSIGNED_CLIENTS, unassignedClientsGroups))

        val cacheItem: CacheItem = await(sessionCacheRepository.cacheRepo.collection.find().head())

        val jsonData: JsObject = cacheItem.data

        val extractedValue: JsValue = (jsonData \ GROUPS_FOR_UNASSIGNED_CLIENTS.unwrap).as[JsValue]

        // should not be the unencrypted version
        extractedValue should not be expectedUnassignedClientsGroupsJson

        await(sessionCacheService.get(GROUPS_FOR_UNASSIGNED_CLIENTS)) shouldBe Some(unassignedClientsGroups)
      }
    }

    "not encrypt and decrypt when storing and retrieving data" which
      Seq(
        CLIENT_FILTER_INPUT,
        GROUP_SERVICE_TYPE,
        GROUP_TYPE,
        RETURN_URL
      ).foreach { dataKey =>
        s"is for ${dataKey.unwrap}" in {
          await(sessionCacheService.put(dataKey, "John Doe"))

          val cacheItem: CacheItem = await(sessionCacheRepository.cacheRepo.collection.find().head())

          val jsonData: JsObject = cacheItem.data

          val extractedValue: String = (jsonData \ dataKey.unwrap).as[String]

          extractedValue shouldBe "John Doe"

          await(sessionCacheService.get(dataKey)) shouldBe Some("John Doe")
        }
      }

    "return None when no data" that {
      Seq(
        GROUP_NAME,
        CLIENT_SEARCH_INPUT,
        GROUP_SEARCH_INPUT,
        TEAM_MEMBER_SEARCH_INPUT,
        NAME_OF_GROUP_CREATED,
        GROUP_RENAMED_FROM,
        GROUP_DELETED_NAME,
        CLIENT_REFERENCE
      ).foreach { dataKey =>
        s"is stored for ${dataKey.unwrap} is found" in {
          await(sessionCacheService.get(dataKey)) shouldBe None
        }
      }

      s"is stored for ${CLIENT_TO_REMOVE.unwrap} is found" in {
        await(sessionCacheService.get(CLIENT_TO_REMOVE)) shouldBe None
      }

      Seq(SELECTED_CLIENTS, CURRENT_PAGE_CLIENTS).foreach { dataKey =>
        s"is stored for ${dataKey.unwrap} is found" in {
          await(sessionCacheService.get(dataKey)) shouldBe None
        }
      }

      s"is stored for ${MEMBER_TO_REMOVE.unwrap} is found" in {
        await(sessionCacheService.get(MEMBER_TO_REMOVE)) shouldBe None
      }

      Seq(CURRENT_PAGE_TEAM_MEMBERS, SELECTED_TEAM_MEMBERS).foreach { dataKey =>
        s"is stored for ${dataKey.unwrap} is found" in {
          await(sessionCacheService.get(dataKey)) shouldBe None
        }
      }

      s"is stored for ${GROUPS_FOR_UNASSIGNED_CLIENTS.unwrap} is found" in {
        await(sessionCacheService.get(GROUPS_FOR_UNASSIGNED_CLIENTS)) shouldBe None
      }
    }
  }
}
