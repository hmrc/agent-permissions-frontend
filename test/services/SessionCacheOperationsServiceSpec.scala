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

import controllers.{CLIENT_FILTER_INPUT, CLIENT_SEARCH_INPUT, CONTINUE_BUTTON, CURRENT_PAGE_CLIENTS, FILTERED_CLIENTS, FILTER_BUTTON, SELECTED_CLIENTS}
import helpers.BaseSpec
import models.{AddClientsToGroup, DisplayClient}
import org.scalatest.BeforeAndAfterEach
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentmtdidentifiers.model.Client

import scala.concurrent.Future

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

  "saveSelectedOrFilteredClients" should {

    "work for CONTINUE_BUTTON" in {

      val CLIENTS = displayClients.take(8)
      //expect
      await(sessionCacheService.put(SELECTED_CLIENTS, CLIENTS.take(2)))
      await(sessionCacheService.put(FILTERED_CLIENTS, CLIENTS.takeRight(1)))

      val expectedPutSelected = List(
        DisplayClient("123456781","friendly name 1","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456782","friendly name 2","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456783","friendly name 3","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456784","friendly name 4","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456785","friendly name 5","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456786","friendly name 6","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456787","friendly name 7","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456788","friendly name 8","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456781","friendly name 1","HMRC-MTD-VAT","VRN",false),
        DisplayClient("123456782","friendly name 2","HMRC-MTD-VAT","VRN",false)
      )
      val formData = AddClientsToGroup(None, None, Some(CLIENTS.map(_.id).toList), CONTINUE_BUTTON)

      //when
      await(sessionCacheOps.saveSelectedOrFilteredClients(arn)(formData)(_ => Future.successful(CLIENTS)))

      await(sessionCacheService.get(SELECTED_CLIENTS)) shouldBe Some(expectedPutSelected)

      await(sessionCacheService.get(FILTERED_CLIENTS)) shouldBe None
      await(sessionCacheService.get(CLIENT_FILTER_INPUT)) shouldBe None
      await(sessionCacheService.get(CLIENT_SEARCH_INPUT)) shouldBe None
    }

    "work for FILTER_BUTTON" in {
      //given
      val CLIENTS = displayClients.take(8)

      val formData = AddClientsToGroup(
        Some("searchTerm"),
        Some("filterTerm"),
        Some(CLIENTS.map(_.id).toList),
        FILTER_BUTTON
      )

      val expectedPutSelected = List(
        DisplayClient("123456781","friendly name 1","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456782","friendly name 2","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456783","friendly name 3","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456784","friendly name 4","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456785","friendly name 5","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456786","friendly name 6","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456787","friendly name 7","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456788","friendly name 8","HMRC-MTD-VAT","VRN",true),
        DisplayClient("123456781","friendly name 1","HMRC-MTD-VAT","VRN",false),
        DisplayClient("123456782","friendly name 2","HMRC-MTD-VAT","VRN",false)
      )

      // set up
      await(sessionCacheService.put(SELECTED_CLIENTS, CLIENTS.take(2)))
      await(sessionCacheService.put(FILTERED_CLIENTS, CLIENTS.takeRight(1)))

      // do request
      await(sessionCacheOps.saveSelectedOrFilteredClients(arn)(formData)(_ => Future.successful(CLIENTS)))

      // check session cache values
      await(sessionCacheService.get(SELECTED_CLIENTS)) shouldBe Some(expectedPutSelected)
      await(sessionCacheService.get(CLIENT_SEARCH_INPUT)) shouldBe Some("searchTerm")
      await(sessionCacheService.get(CLIENT_FILTER_INPUT)) shouldBe Some("filterTerm")
      await(sessionCacheService.get(FILTERED_CLIENTS)) shouldBe Some(Seq.empty)

    }

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

  "filterClients save filtered clients to session and return filtered clients" should {
    "WHEN the search term doesn't match any" in {
      //given
      val formData = AddClientsToGroup(clients = Some(displayClients.map(_.id).toList), search = Some("zzzzzz"))
      val selectedClients = displayClients.take(1)
      await(sessionCacheService.put(SELECTED_CLIENTS, displayClients.takeRight(2)))

      //when
      val filteredClients = await(sessionCacheOps.filterClients(formData)(selectedClients))

      await(sessionCacheService.get(FILTERED_CLIENTS)) shouldBe Some(Seq.empty)

      filteredClients shouldBe Nil
    }

    "WHEN the search term DOES MATCH SOME but none are selected" in {
      //given
      val formData = AddClientsToGroup(clients = Some(displayClients.map(_.id).toList), search = Some("friendly name"))
      val clients = displayClients.take(1)
      await(sessionCacheService.put(SELECTED_CLIENTS, displayClients.takeRight(2)))

      //when
      val filteredClients = await(sessionCacheOps.filterClients(formData)(clients))

      filteredClients shouldBe displayClients.take(1)
      await(sessionCacheService.get(FILTERED_CLIENTS)) shouldBe Some(displayClients.take(1))

    }

    "WHEN the search term DOES MATCH SOME and there are SELECTED clients that match those passed in" in {
      //given
      val formData = AddClientsToGroup(clients = Some(displayClients.map(_.id).toList), search = Some("friendly name"))
      val clients = displayClients.take(5)
      //the ones that match the SELECTED_CLIENTS session items will be marked as selected = true
      val expectedClients = clients.zipWithIndex.map(zip => if(zip._2 < 3) zip._1.copy(selected = true) else zip._1)
      await(sessionCacheService.put(SELECTED_CLIENTS, clients.take(3)))

      //when
      val filteredClients = await(sessionCacheOps.filterClients(formData)(clients))

      //first 2 clients should be
      filteredClients shouldBe expectedClients

      await(sessionCacheService.get(FILTERED_CLIENTS)) shouldBe Some(expectedClients)
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