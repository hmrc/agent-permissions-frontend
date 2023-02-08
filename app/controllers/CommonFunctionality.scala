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

package controllers

import forms.{AddClientsToGroupForm, SearchAndFilterForm}
import models.{AddClientsToGroup, SearchFilter}
import play.api.data.Forms.{single, text}
import play.api.data.{Form, FormBinding}
import play.api.mvc.Results.Redirect
import play.api.mvc.{Call, Request, Result}
import services.{SessionCacheOperationsService, SessionCacheService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

/**
 * The function in this class will take care of handling the submission (post) of a page with both pagination and
 * filtering present.
 * This is an abstract class as the user must provide implementations for some page-specific pieces of functionality.
 * The following conventions should be respected:
 * - The form should have at minimum the following values:
 *   * "search" (optional)
 *   * "filter" (optional)
 *   * "submit" (mandatory)
 * - The submit button will indicate what input the user has given:
 *   * "continue": the user intends to continue the journey onto the next page
 *   * "filter": the user wishes to apply filters to the current view according to their form input
 *   * "clear": the user wishes to clear all current filters
 *   * "pagination_[x]": the user wishes to view page number 'x' of the view.
 * - When rendering, the search term is not passed explicitly but is stored in the CLIENT_SEARCH_INPUT session value
 * - When rendering, the filter term is not passed explicitly but is stored in the CLIENT_FILTER_INPUT session value
 */
abstract class POSTPaginationHandler[FormData](sessionCacheService: SessionCacheService) {
  /** A blank form of the type used by this page. */
  val emptyForm: Form[FormData]
  /** A function to render the page given a (potentially) pre-filled form. */
  val renderPage: Form[FormData] => Future[Result]
  /** HTTP call used by the page to reload itself, if it should need to. The parameters are page number, search term, filter term */
  val reloadCall: (Option[Int], Option[String], Option[String]) => Call
  /** A function that will be called when submitting with a valid form, regardless of which button the user has submitted with.
   *  Typically used to update the session state in some way. */
  val onSubmit: FormData => Future[Unit]
  /** The function that will be called when submitting with a "continue" button, and the data is valid. */
  val onContinue: FormData => Future[Result]

  val checkSubmitTypeForm: Form[String] = Form(single("submit" -> text))

  def handlePost(implicit ec: ExecutionContext, request: Request[_], formBinding: FormBinding): Future[Result] = {
    emptyForm.bindFromRequest()
      .fold(
        formWithErrors => renderPage(formWithErrors),
        formData => onSubmit(formData).flatMap { _ =>
          val submitType = checkSubmitTypeForm.bindFromRequest().value.getOrElse("")
          submitType match {
            // The form was submitted using the 'continue' button. Execute the user-supplied logic.
            case CONTINUE_BUTTON => onContinue(formData)
            // The form was submitted using the 'filter' button. Save the filter settings and reload the page.
            case FILTER_BUTTON =>
              val searchFilter: SearchFilter = SearchAndFilterForm.form().bindFromRequest().get
              for {
                _ <- sessionCacheService.put(CLIENT_SEARCH_INPUT, searchFilter.search.getOrElse(""))
                _ <- sessionCacheService.put(CLIENT_FILTER_INPUT, searchFilter.filter.getOrElse(""))
                result <- Future.successful(Redirect(reloadCall(None, searchFilter.search, searchFilter.filter)))
              } yield result
            // The form was submitted using the 'clear filters' button. Clear the filters and reload the page.
            case CLEAR_BUTTON =>
              sessionCacheService.deleteAll(clientFilteringKeys).map(_ =>
                Redirect(reloadCall(None, None, None))
              )
            // The form was submitted using a pagination button. Reload the page at the correct page.
            case paginationButton if paginationButton.startsWith(PAGINATION_BUTTON) =>
              val pageToShow = paginationButton.replace(s"${PAGINATION_BUTTON}_", "").toInt
              Future.successful(Redirect(reloadCall(Some(pageToShow), None, None)))
            case _ => //bad submit
              Future.successful(Redirect(reloadCall(None, None, None)))
          }
        }
      )
  }
}

/**
 * Same as [[POSTPaginationHandler]] but also takes care of remembering selected items while browsing through
 * different pagination pages, and when reloading the page by setting and unsetting filters.
 * filtering present. The following conventions should be respected.
 * The currently selected items are saved in the session value "SELECTED_CLIENTS".
 */
abstract class POSTPaginatedSearchableClientSelectHandler(sessionCacheService: SessionCacheService, sessionCacheOps: SessionCacheOperationsService) { self =>
  val renderPage: Form[AddClientsToGroup] => Future[Result]
  val reloadCall: (Option[Int], Option[String], Option[String]) => Call
  val onContinue: AddClientsToGroup => Future[Result]

  def handlePost(implicit ec: ExecutionContext, hc: HeaderCarrier, request: Request[_], formBinding: FormBinding): Future[Result] = {
    new POSTPaginationHandler[AddClientsToGroup](sessionCacheService) {
      val emptyForm: Form[AddClientsToGroup] = AddClientsToGroupForm.form()
      val renderPage: Form[AddClientsToGroup] => Future[Result] = self.renderPage
      val reloadCall: (Option[Int], Option[String], Option[String]) => Call = self.reloadCall
      val onSubmit: AddClientsToGroup => Future[Unit] = formData => sessionCacheOps.savePageOfClients(formData).map(_ => ())
      val onContinue: AddClientsToGroup => Future[Result] = { formData =>
        // check selected clients from session cache AFTER saving (removed de-selections)
        sessionCacheService.get(SELECTED_CLIENTS).flatMap { nowSelectedClients =>
          if (nowSelectedClients.nonEmpty) {
            self.onContinue(formData)
          } else { // render page with empty client error
            renderPage(emptyForm.withError("clients", "error.select-clients.empty"))
          }
        }
      }
    }.handlePost
  }
}