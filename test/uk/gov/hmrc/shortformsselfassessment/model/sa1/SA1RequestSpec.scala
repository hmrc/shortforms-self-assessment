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

package uk.gov.hmrc.shortformsselfassessment.model.sa1

import org.scalatest.matchers.must.Matchers.mustBe
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsSuccess, Json}
import uk.gov.hmrc.shortformsselfassessment.models.sa1.{Address, SA1Request, SelfAssessmentReasons}

import java.time.LocalDate

class SA1RequestSpec extends AnyWordSpec with Matchers {

  "SA1ResponsePec" should {
    "must parse a valid SA1Controller request" in {
      val json = Json.parse(
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

      json.validate[SA1Request] mustBe JsSuccess(
        SA1Request(
          title2 = Some("Mrs"),
          firstName = "Sherlock",
          lastName = "Holmes",
          previousLastName = None,
          niNumber = Some("AA123456B"),
          niNoReason = None,
          dob = LocalDate.parse("1854-01-01"),
          address = Address(
            line1 = "221B Baker Street",
            line2 = "London",
            postcode = "NW1 6XE"
          ),
          moveToAddressOn = LocalDate.parse("1887-01-01"),
          phone = "07 123 456 789",
          email = "sherlock.holmes@consultingdetective.com",
          reasons = SelfAssessmentReasons(
            companyDirectorSince = Some(LocalDate.parse("1889-01-01")),
            otherReason = Some("I am making self-invested pension contributions"),
            otherReasonAppliesSince = Some(LocalDate.parse("1888-06-01"))
          ),
          personCompletingForm = Some("Sherlock Holmes")
        )
      )
    }

    "must fail when postcode is missing" in {
      val json = Json.parse(
        """
                 {
                    "title2" : "Mrs",
                    "firstName": "Sherlock",
                    "lastName": "Holmes",
                    "niNumber": "AA123456B",
                    "dob": "1854-01-01",
                    "address": {},
                    "moveToAddressOn": "1887-01-01",
                    "phone": "07 123 456 789",
                    "email": "sherlock.holmes@consultingdetective.com",
                    "reasons": {
                          "companyDirectorSince": "1889-01-01",
                          "otherReason": "I am making self-invested pension contributions",
                          "otherReasonAppliesSince": "1888-06-01"
                    },
                    "declaration": false,
                    "personCompletingForm": "Sherlock Holmes"
                    }
               """
      )

      json.validate[SA1Request] mustBe a[JsError]
    }

    "must fail when phone and emails are missing" in {
      val json = Json.parse(
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
                          "reasons": {
                                "companyDirectorSince": "1889-01-01",
                                "otherReason": "I am making self-invested pension contributions",
                                "otherReasonAppliesSince": "1888-06-01"
                          },
                          "declaration": false,
                          "personCompletingForm": "Sherlock Holmes"
                          }
                     """
      )

      json.validate[SA1Request] mustBe a[JsError]
    }
  }
}
