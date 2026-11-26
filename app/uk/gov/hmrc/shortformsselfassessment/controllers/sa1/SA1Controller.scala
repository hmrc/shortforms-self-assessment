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
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import uk.gov.hmrc.shortformsselfassessment.connectors.IFormSubmissionConnector
import uk.gov.hmrc.shortformsselfassessment.models.sa1.{SA1Request, SA1Response}
import uk.gov.hmrc.shortformsselfassessment.services.sa1.SA1XmlGenerator

import java.time.{ZoneId, ZonedDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SA1Controller @Inject() (
  cc: ControllerComponents,
  submitterAction: SubmitterAction,
  iFormSubmissionConnector: IFormSubmissionConnector
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {
  private val SubmissionZone = ZoneId.of("Europe/London")
  private val SA1FormName    = "nps-shortform-l"

  def submitSA1(): Action[JsValue] = (Action andThen submitterAction).async(parse.json) { implicit request =>
    withJsonBody[SA1Request] { sa1Request =>
      SA1XmlGenerator.generate(sa1Request, request.submitter, ZonedDateTime.now(SubmissionZone)) match {
        case Left(_)    =>
          logger.error("[SA1Controller][submitSA1] unable to generate a valid SA1 ShortForm submission")
          Future.successful(
            InternalServerError(
              Json.toJson(
                ErrorResponse(
                  statusCode = 500,
                  message = "Unable to generate the SA1 submission",
                  xStatusCode = Some("SF-ERR-SYSERR")
                )
              )
            )
          )
        case Right(xml) =>
          iFormSubmissionConnector.submit(SA1FormName, xml).map {
            case Right(acknowledgement) =>
              logger.info(
                s"[SA1Controller][submitSA1] successfully submitted request - ${acknowledgement.acknowledgementRef}"
              )
              Accepted(
                Json.toJson(
                  SA1Response(
                    acknowledgementRef = acknowledgement.acknowledgementRef,
                    receivedTimestamp = acknowledgement.receivedTimestamp
                  )
                )
              )
            case Left(error)            =>
              logger.error(s"[SA1Controller][submitSA1] iForms submission failed: $error")
              BadGateway(
                Json.toJson(
                  ErrorResponse(
                    statusCode = 502,
                    message = "iForms submission failed",
                    xStatusCode = Some("SF-ERR-SYSERR")
                  )
                )
              )
          }
      }
    }
  }
}
