/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.shortformsselfassessment.controllers.sa1

import org.mockito.ArgumentMatchers.{any, eq as mockEq}
import org.mockito.Mockito.when
import org.scalatest.matchers.must.Matchers.mustBe
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.shortformsselfassessment.models.sa1.SA1Request
import uk.gov.hmrc.shortformsselfassessment.services.SA1SubmissionService

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SA1ControllerSpec extends PlaySpec with MockitoSugar {

  val mockSubmissionService: SA1SubmissionService = mock[SA1SubmissionService]
  val controller                                  = new SA1Controller(stubControllerComponents(), mockSubmissionService)

  val validJsonBody: JsValue = Json.parse(
    """
                   {
                      "title2" : "Mrs",
                      "firstName": "Sherlock",
                      "lastName": "Holmes",
                      "niNumber": "AA123456B",
                      "dob": "1854-01-01",
                      "address": {
                            "line1": "221B Baker Street",
                            "line2": "London",
                            "postcode": "NW1 6XE"
                      },
                      "moveToAddressOn": "1887-01-01",
                      "phone": "07 123 456 789",
                      "email": "sherlock.holmes@consultingdetective.com",
                      "reasons": {
                            "companyDirectorSince": "1889-01-01",
                            "otherReason": "I am making self-invested pension contributions",
                            "otherReasonAppliesSince": "1888-06-01"
                      },
                      "personCompletingForm": "Sherlock Holmes"
                      }
                 """
  )
  "SA1ControllerSpec" should {
    "return 400 Bad Request when the Journey-Id header is missing" in {

      val request                = FakeRequest("POST", "/submit")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(validJsonBody)
      val result: Future[Result] = controller.submitSA1()(request)

      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "MISSING Journey-ID",
        "message" -> "The mandatory Journey-ID header is missing."
      )
    }

    "return 202 Accepted with a valid JSON and jouney id" in {

      val testJourneyId          = "test-jouney-id-12345"
      val expectedGeneratedId    = "SA1-XYZ987"
      val request                = FakeRequest("POST", "/submit")
        .withHeaders("Content-Type" -> "application/json", "Journey-ID" -> testJourneyId)
        .withBody(validJsonBody)
      when(mockSubmissionService.processSubmission(any[SA1Request], mockEq(testJourneyId)))
        .thenReturn(Future.successful(Right(expectedGeneratedId)))
      val result: Future[Result] = controller.submitSA1()(request)

      status(result) mustBe ACCEPTED
      val jsonResult = contentAsJson(result)
      (jsonResult \ "acknowledgementRef").as[String] mustBe expectedGeneratedId
      (jsonResult \ "receivedTimestamp").as[LocalDate] mustBe LocalDate.now()
    }

    "return NOT_FOUND when a internal error" in {

      val testJourneyId = "notfound-jouney-id-12345"

      val request = FakeRequest("POST", "/submit")
        .withHeaders("Content-Type" -> "application/json", "Journey-ID" -> testJourneyId)
        .withBody(validJsonBody)

      when(mockSubmissionService.processSubmission(any[SA1Request], mockEq(testJourneyId)))
        .thenReturn(Future.successful(Left("NOT_FOUND")))

      val result: Future[Result] = controller.submitSA1()(request)

      status(result) mustBe CONFLICT
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "NOT_FOUND",
        "message" -> "The request resource count not be found."
      )
    }

    "return 400 Bad Request for invalid Json" in {

      val testJourneyId            = "invalid-jouney-id-12345"
      val invalidJsonBody: JsValue = Json.obj("brokenField" -> "Invalid structure")

      val request = FakeRequest("POST", "/submit")
        .withHeaders("Content-Type" -> "application/json", "Journey-ID" -> testJourneyId)
        .withBody(invalidJsonBody)

      val result: Future[Result] = controller.submitSA1()(request)

      status(result) mustBe BAD_REQUEST
    }

  }
}
