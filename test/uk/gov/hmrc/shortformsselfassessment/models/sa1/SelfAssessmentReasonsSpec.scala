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

package uk.gov.hmrc.shortformsselfassessment.models.sa1

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsSuccess, Json, OWrites}

import java.time.LocalDate

class SelfAssessmentReasonsSpec extends PlaySpec with ScalaCheckPropertyChecks {
  import org.scalacheck.Arbitrary.arbitrary

  private val randomSelfAssessmentReasons = for
    companyDirectorSince          <- arbitrary[Option[LocalDate]]
    dateLandPropertyUk            <- arbitrary[Option[LocalDate]]
    earningForeignIncomeSince     <- arbitrary[Option[LocalDate]]
    dateAnnualIncome              <- arbitrary[Option[LocalDate]]
    exceedingIncomeThresholdSince <- arbitrary[Option[LocalDate]]
    earningOffPayeSince           <- arbitrary[Option[LocalDate]]
    highIncomeChildBenefitsSince  <- arbitrary[Option[LocalDate]]
    gainingCapitalSince           <- arbitrary[Option[LocalDate]]
    companyDirectorSince          <- arbitrary[Option[LocalDate]]
    otherReason                   <- arbitrary[Option[String]]
    otherReasonAppliesSince       <- arbitrary[Option[LocalDate]]
  yield SelfAssessmentReasons(
    companyDirectorSince,
    dateLandPropertyUk,
    earningForeignIncomeSince,
    dateAnnualIncome,
    exceedingIncomeThresholdSince,
    highIncomeChildBenefitsSince,
    earningOffPayeSince,
    gainingCapitalSince,
    otherReason,
    otherReasonAppliesSince
  )

  private val writer = Json.writes[SelfAssessmentReasons]

  "SelfAssessmentReasonsSpec" should {
    "parse a sample valid SelfAssessmentReasons request" in {
      val json = Json.parse(
        """
                   {        "companyDirectorSince": "1889-01-01",
                            "otherReason": "I am making self-invested pension contributions",
                            "otherReasonAppliesSince": "1888-06-01"
                      }
                 """
      )
      json.validate[SelfAssessmentReasons] mustBe JsSuccess(
        SelfAssessmentReasons(
          companyDirectorSince = Some(LocalDate.parse("1889-01-01")),
          otherReason = Some("I am making self-invested pension contributions"),
          otherReasonAppliesSince = Some(LocalDate.parse("1888-06-01"))
        )
      )
    }

    "parse a random valid SelfAssessmentReasons request" in
      forAll(randomSelfAssessmentReasons) { expectedReasons =>
        val json          = writer.writes(expectedReasons)
        val parsedReasons = json.as[SelfAssessmentReasons]

        parsedReasons mustBe expectedReasons
      }
  }
}
