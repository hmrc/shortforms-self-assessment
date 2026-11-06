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

import play.api.Logging

import java.time.LocalDate
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.shortformsselfassessment.models.sa1.{SA1Request, SA1Response}
import uk.gov.hmrc.shortformsselfassessment.services.SA1SubmissionService

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SA1Controller @Inject() (
  cc: ControllerComponents,
  submissionService: SA1SubmissionService
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def submitSA1(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.headers.get("Journey-ID") match {

      case Some(journeyId) =>
        withJsonBody[SA1Request] { sa1Request =>
          submissionService.processSubmission(sa1Request, journeyId).map {
            case Right(generatedId) =>
              logger.info(s"[SA1Controller][submitSA1] successfully submitted request - $generatedId")
              Accepted(
                Json.toJson(
                  SA1Response(
                    acknowledgementRef = generatedId,
                    receivedTimestamp = LocalDate.now()
                  )
                )
              )
            case Left("NOT_FOUND")  =>
              Conflict(
                Json.obj(
                  "code"    -> "NOT_FOUND",
                  "message" -> "The request resource count not be found."
                )
              )
            case Left(_)            =>
              InternalServerError(
                Json.obj(
                  "code"    -> "INTERNAL_SERVER_ERROR",
                  "message" -> "System error."
                )
              )
          }
        }.recover {
          case ex: NoSuchElementException =>
            logger.error("[SA1Controller][submitSA1] ex.getMessage")

            NotFound(
              Json.toJson(
                "code"    -> "NOT_FOUND",
                "message" -> ex.getMessage
              )
            )
          case ex: RuntimeException       =>
            logger.error("[SA1Controller][submitSA1] ex.getMessage")

            InternalServerError(
              Json.obj(
                "code"    -> "INTERNAL_SERVER_ERROR",
                "message" -> ex.getMessage
              )
            )
        }
      case None            =>
        logger.error("[SA1Controller][submitSA1] The mandatory Journey_ID header is missing")
        Future.successful(
          BadRequest(
            Json.obj(
              "code"    -> "MISSING Journey-ID",
              "message" -> "The mandatory Journey-ID header is missing."
            )
          )
        )
    }
  }
}
