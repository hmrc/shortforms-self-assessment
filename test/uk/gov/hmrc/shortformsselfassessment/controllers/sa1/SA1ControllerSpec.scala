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

package controllers.sa1

import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.shortformsselfassessment.connectors.{IFormAcknowledgement, IFormSubmissionConnector, IFormSubmissionError}

import java.time.Instant
import scala.concurrent.Future

class SA1ControllerSpec extends PlaySpec with MockitoSugar {

  private val cc              = stubControllerComponents()
  private val iFormSubmission = mock[IFormSubmissionConnector]
  private val controller      =
    new SA1Controller(cc, new SubmitterAction(new FakeAuthConnector())(cc.executionContext), iFormSubmission)(
      cc.executionContext
    )

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
    "return 202 Accepted with a valid JSON and jouney id" in {
      val request = FakeRequest("POST", "/submit")
        .withHeaders(
          "Content-Type"                      -> "application/json",
          SubmitterAction.AuthorisationHeader -> "Bearer gateway-token",
          SubmitterAction.SessionIdHeader     -> "session-123"
        )
        .withBody(validJsonBody)
      when(iFormSubmission.submit(anyString(), anyString()))
        .thenReturn(
          Future.successful(Right(IFormAcknowledgement("ACK-123", Instant.parse("2026-11-13T14:54:23.038Z"))))
        )
      val result  = controller.submitSA1()(request)

      status(result) mustBe ACCEPTED
      val jsonResult = contentAsJson(result)
      (jsonResult \ "acknowledgementRef").as[String] mustBe "ACK-123"
      (jsonResult \ "receivedTimestamp").as[Instant] mustBe Instant.parse("2026-11-13T14:54:23.038Z")
    }

    "return 502 Bad Gateway with SF-ERR-SYSERR when iForms rejects the submission" in {
      val request = FakeRequest("POST", "/submit")
        .withHeaders(
          "Content-Type"                      -> "application/json",
          SubmitterAction.AuthorisationHeader -> "Bearer gateway-token",
          SubmitterAction.SessionIdHeader     -> "session-123"
        )
        .withBody(validJsonBody)
      when(iFormSubmission.submit(anyString(), anyString()))
        .thenReturn(Future.successful(Left(IFormSubmissionError.UnexpectedStatus(500, "boom"))))

      val result = controller.submitSA1()(request)

      status(result) mustBe BAD_GATEWAY
      (contentAsJson(result) \ "xStatusCode").as[String] mustBe "SF-ERR-SYSERR"
    }

    "return 502 Bad Gateway with SF-ERR-SYSERR when iForms cannot be reached" in {
      val request = FakeRequest("POST", "/submit")
        .withHeaders(
          "Content-Type"                      -> "application/json",
          SubmitterAction.AuthorisationHeader -> "Bearer gateway-token",
          SubmitterAction.SessionIdHeader     -> "session-123"
        )
        .withBody(validJsonBody)
      when(iFormSubmission.submit(anyString(), anyString()))
        .thenReturn(Future.successful(Left(IFormSubmissionError.NoResponse(new RuntimeException("timeout")))))

      val result = controller.submitSA1()(request)

      status(result) mustBe BAD_GATEWAY
      (contentAsJson(result) \ "xStatusCode").as[String] mustBe "SF-ERR-SYSERR"
    }

    "return 400 Bad Request when the session ID header is missing" in {
      val request = FakeRequest("POST", "/submit")
        .withHeaders(
          "Content-Type"                      -> "application/json",
          SubmitterAction.AuthorisationHeader -> "Bearer gateway-token"
        )
        .withBody(validJsonBody)

      val result = controller.submitSA1()(request)

      status(result) mustBe BAD_REQUEST
      (contentAsJson(result) \ "message").as[String] must include("X-Session-ID")
    }

    "return 401 Unauthorized when the Authorization header is missing" in {
      val request = FakeRequest("POST", "/submit")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(validJsonBody)

      val result = controller.submitSA1()(request)

      status(result) mustBe UNAUTHORIZED
    }

    "return 400 Bad Request for invalid Json" in {
      val invalidJsonBody: JsValue = Json.obj("brokenField" -> "Invalid structure")

      val request = FakeRequest("POST", "/submit")
        .withHeaders(
          "Content-Type"                      -> "application/json",
          SubmitterAction.AuthorisationHeader -> "Bearer gateway-token",
          SubmitterAction.SessionIdHeader     -> "session-123"
        )
        .withBody(invalidJsonBody)

      val result = controller.submitSA1()(request)

      status(result) mustBe BAD_REQUEST
    }
  }
}
