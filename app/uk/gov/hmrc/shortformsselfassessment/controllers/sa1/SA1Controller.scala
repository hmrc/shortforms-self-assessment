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

import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.shortformsselfassessment.models.sa1.{SA1Request, SA1Response}
import uk.gov.hmrc.shortformsselfassessment.services.sa1.SA1XmlGenerator

import java.time.{Instant, ZoneId, ZonedDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class SA1Controller @Inject() (cc: ControllerComponents, submitterAction: SubmitterAction)
    extends BackendController(cc)
    with Logging {
  private val SubmissionZone        = ZoneId.of("Europe/London")
  private def getAcknowledgementRef = "SA1-" + java.util.UUID.randomUUID().toString.take(8).toUpperCase()

  def submitSA1(): Action[JsValue] = (Action andThen submitterAction).async(parse.json) { implicit request =>
    withJsonBody[SA1Request] { sa1Request =>
      SA1XmlGenerator.generate(sa1Request, request.submitter, ZonedDateTime.now(SubmissionZone)) match {
        case Left(_)  =>
          logger.error("[SA1Controller][submitSA1] unable to generate a valid SA1 ShortForm submission")
          Future.successful(
            InternalServerError(
              Json.obj("code" -> "SF-ERR-SYSERR", "message" -> "Unable to generate the SA1 submission")
            )
          )
        case Right(_) =>
          val acknowledgementRef = getAcknowledgementRef
          logger.info(s"[SA1Controller][submitSA1] successfully submitted request - $acknowledgementRef")
          Future.successful(
            Accepted(
              Json.toJson(
                SA1Response(
                  acknowledgementRef = acknowledgementRef,
                  receivedTimestamp = Instant.now()
                )
              )
            )
          )
      }
    }
  }
}
