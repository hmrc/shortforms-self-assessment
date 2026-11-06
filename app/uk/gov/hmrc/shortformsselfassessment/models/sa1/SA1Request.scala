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

import play.api.libs.json.*

import java.time.{Instant, LocalDate}

case class SA1Request(
  title2: Option[String] = None,
  firstName: String,
  lastName: String,
  previousLastName: Option[String],
  niNumber: Option[String],
  niNoReason: Option[String],
  dob: LocalDate,
  address: Address,
  moveToAddressOn: LocalDate,
  phone: String,
  email: String,
  reasons: SelfAssessmentReasons,
  personCompletingForm: Option[String]
)
object SA1Request {
  given Reads[SA1Request] = Json.reads
}
case class SA1Response(
  acknowledgementRef: String,
  receivedTimestamp: Instant
)

object SA1Response {
  given OWrites[SA1Response] = Json.writes
}
