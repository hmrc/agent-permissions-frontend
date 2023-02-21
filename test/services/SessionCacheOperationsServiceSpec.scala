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

package services

import controllers.{CLIENT_FILTER_INPUT, CLIENT_SEARCH_INPUT, CONTINUE_BUTTON, CURRENT_PAGE_CLIENTS, FILTERED_CLIENTS, SELECTED_CLIENTS}
import helpers.BaseSpec
import models.{AddClientsToGroup, DisplayClient}
import org.scalatest.BeforeAndAfterEach
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.Client

class SessionCacheOperationsServiceSpec extends BaseSpec with BeforeAndAfterEach {

  val sessionCacheService: InMemorySessionCacheService = new InMemorySessionCacheService()
  val sessionCacheOps = new SessionCacheOperationsService(sessionCacheService)

  val fakeClients: Seq[Client] = (1 to 10)
    .map(i => Client(s"HMRC-MTD-VAT~VRN~12345678$i", s"friendly name $i"))

  val displayClients: Seq[DisplayClient] =
    fakeClients.map(DisplayClient.fromClient(_))

  override def beforeEach(): Unit = {
    sessionCacheService.values.clear()
  }

  "addSelectablesToSession" should {
    
    "work as expected with none set as selected " in{
      //given existing session state
      await(sessionCacheService.put(SELECTED_CLIENTS, displayClients.take(2)))
      await(sessionCacheService.put(FILTERED_CLIENTS, displayClients.takeRight(1)))

      //when
      await(sessionCacheOps.addSelectablesToSession(displayClients.take(3).toList)(SELECTED_CLIENTS, FILTERED_CLIENTS))

      //we expect the sesion to be changed like this
      await(sessionCacheService.get(SELECTED_CLIENTS)) shouldBe Some(displayClients.take(3))
    }
    
    "work as expected with selected in session " in{
      //given existing session state
      await(sessionCacheService.put(SELECTED_CLIENTS, displayClients.take(2).map(_.copy(selected = true))))
      await(sessionCacheService.put(FILTERED_CLIENTS, displayClients.takeRight(1).map(_.copy(selected = true))))

      val expectedPayload = List(
        DisplayClient("123456781","friendly name 1","HMRC-MTD-VAT","VRN",false),
        DisplayClient("123456782","friendly name 2","HMRC-MTD-VAT","VRN",false),
        DisplayClient("123456783","friendly name 3","HMRC-MTD-VAT","VRN",false),
        DisplayClient("123456781","friendly name 1","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456782","friendly name 2","HMRC-MTD-VAT","VRN",true)
      )

      //when
      await(sessionCacheOps.addSelectablesToSession(displayClients.take(3).toList)(SELECTED_CLIENTS, FILTERED_CLIENTS))

      //we expect the sesion to be changed like this
      await(sessionCacheService.get(SELECTED_CLIENTS)) shouldBe Some(expectedPayload)
    }
  }

  "saveSearch" should {

    "PUT search terms to session" in {
      //expect
      val searchTerm = Some("blah")
      val filterTerm = Some("MTD-VAT")

      //when
      await(sessionCacheOps.saveSearch(searchTerm, filterTerm))

      await(sessionCacheService.get(CLIENT_SEARCH_INPUT)) shouldBe searchTerm
      await(sessionCacheService.get(CLIENT_FILTER_INPUT)) shouldBe filterTerm


    }

    "delete session items if both are empty" in {
      await(sessionCacheService.put(CLIENT_SEARCH_INPUT, "searchTerm"))
      await(sessionCacheService.put(CLIENT_FILTER_INPUT, "filterTerm"))

      val searchTerm = Some("")
      val filterTerm = Some("")

      //when
      await(sessionCacheOps.saveSearch(searchTerm, filterTerm))

      await(sessionCacheService.get(CLIENT_SEARCH_INPUT)) shouldBe None
      await(sessionCacheService.get(CLIENT_FILTER_INPUT)) shouldBe None
    }

    "delete session items if both are not defined" in {
      await(sessionCacheService.put(CLIENT_SEARCH_INPUT, "searchTerm"))
      await(sessionCacheService.put(CLIENT_FILTER_INPUT, "filterTerm"))

      val searchTerm = None
      val filterTerm = None

      //when
      await(sessionCacheOps.saveSearch(searchTerm, filterTerm))

      await(sessionCacheService.get(CLIENT_SEARCH_INPUT)) shouldBe None
      await(sessionCacheService.get(CLIENT_FILTER_INPUT)) shouldBe None
    }
  }

  "savePageOfClients" should {

    "ADD selected clients to SELECTED_CLIENTS for current page" in {

      //expect
      val clientsSelectedOnThisPage = displayClients.take(4)
      val selectedClientIdsPosted = clientsSelectedOnThisPage.map(_.id).toList
      val alreadySelectedClients = displayClients.takeRight(2)
      await(sessionCacheService.put(SELECTED_CLIENTS, alreadySelectedClients))
      await(sessionCacheService.put(CURRENT_PAGE_CLIENTS, displayClients.take(6)))

      val expectedToBeSaved = (alreadySelectedClients ++ clientsSelectedOnThisPage).map(_.copy(selected = true)).sortBy(_.name)

      val formData = AddClientsToGroup(None, None, Some(selectedClientIdsPosted), CONTINUE_BUTTON)

      //when
      await(sessionCacheOps.savePageOfClients(formData))

      await(sessionCacheService.get(SELECTED_CLIENTS)) shouldBe Some(expectedToBeSaved)

      await(sessionCacheService.get(FILTERED_CLIENTS)) shouldBe None
      await(sessionCacheService.get(CLIENT_FILTER_INPUT)) shouldBe None
      await(sessionCacheService.get(CLIENT_SEARCH_INPUT)) shouldBe None

    }

    "ADD selected clients to SELECTED_CLIENTS for current page and REMOVE existing ones on page that are not selected" in {

      //expect
      val clientsSelectedOnThisPage = displayClients.take(4).takeRight(2)
      val selectedClientIdsPosted = clientsSelectedOnThisPage.map(_.id).toList
      val alreadySelectedClients = displayClients.take(2) // <-- these were the already selected clients

      await(sessionCacheService.put(SELECTED_CLIENTS, alreadySelectedClients))
      await(sessionCacheService.put(CURRENT_PAGE_CLIENTS, displayClients.take(6)))

      //we expect to save in session ordered by name and 'selected = true'
      val expectedToBeSaved = (clientsSelectedOnThisPage).map(_.copy(selected = true)).sortBy(_.name)

      val formData = AddClientsToGroup(None, None, Some(selectedClientIdsPosted), CONTINUE_BUTTON)

      //when
      await(sessionCacheOps.savePageOfClients(formData))

      await(sessionCacheService.get(SELECTED_CLIENTS)) shouldBe Some(expectedToBeSaved)

      await(sessionCacheService.get(FILTERED_CLIENTS)) shouldBe None
      await(sessionCacheService.get(CLIENT_FILTER_INPUT)) shouldBe None
      await(sessionCacheService.get(CLIENT_SEARCH_INPUT)) shouldBe None

    }
  }

}
