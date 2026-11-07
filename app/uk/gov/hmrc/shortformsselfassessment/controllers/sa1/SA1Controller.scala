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
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.shortformsselfassessment.models.sa1.{SA1Request, SA1Response}

import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class SA1Controller @Inject()(cc: ControllerComponents) extends BackendController(cc) with Logging {

  def submitSA1(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[SA1Request] { sa1Request =>
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

  private def getAcknowledgementRef = "SA1-" + java.util.UUID.randomUUID().toString.take(8).toUpperCase()
}
