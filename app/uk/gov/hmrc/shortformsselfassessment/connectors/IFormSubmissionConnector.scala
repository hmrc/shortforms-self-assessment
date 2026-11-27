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

package uk.gov.hmrc.shortformsselfassessment.connectors

import play.api.Configuration
import play.api.Logging
import play.api.libs.ws.DefaultBodyWritables.writeableOf_String
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.{Instant, LocalDateTime, ZoneId}
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NonFatal
import scala.xml.{Elem, XML}

final case class IFormAcknowledgement(acknowledgementRef: String, receivedTimestamp: Instant)

enum IFormSubmissionError {
  case UnexpectedStatus(status: Int, body: String)
  case NoResponse(cause: Throwable)
  case MalformedResponse(reason: String)
}

object IFormAcknowledgement {

  // iForms may omit the zone offset, so a zone-less timestamp is read as London time, matching the confirmation page.
  private val londonZone = ZoneId.of("Europe/London")

  private def parseTimestamp(value: String): Option[Instant] =
    Try(Instant.parse(value)).toOption
      .orElse(Try(LocalDateTime.parse(value).atZone(londonZone).toInstant).toOption)

  def fromEnvelope(body: String): Either[IFormSubmissionError, IFormAcknowledgement] =
    for {
      root      <- Try(XML.loadString(body)).toEither.left.map(e => IFormSubmissionError.MalformedResponse(e.getMessage))
      ref       <- text(root, "AcknowledgmentReference")
                     .toRight(IFormSubmissionError.MalformedResponse("Missing AcknowledgmentReference"))
      timestamp <- text(root, "ReceivedTimestamp")
                     .toRight(IFormSubmissionError.MalformedResponse("Missing ReceivedTimestamp"))
      received  <- parseTimestamp(timestamp)
                     .toRight(IFormSubmissionError.MalformedResponse(s"Invalid ReceivedTimestamp: $timestamp"))
    } yield IFormAcknowledgement(ref, received)

  // Lookups ignore namespaces because the iForms envelope prefixes its elements (for example "ns:").
  private def text(root: Elem, element: String): Option[String] =
    (root \\ element).headOption.map(_.text.trim).filter(_.nonEmpty)
}

@Singleton
class IFormSubmissionConnector @Inject() (
  httpClient: HttpClientV2,
  servicesConfig: ServicesConfig,
  configuration: Configuration
)(implicit ec: ExecutionContext)
    extends Logging {

  private val baseUrl         = servicesConfig.baseUrl("iforms-submission")
  private val readTimeout     = configuration.get[FiniteDuration]("microservice.services.iforms-submission.read-timeout")
  private val envelopeVersion = "1"

  def submit(formName: String, xml: String): Future[Either[IFormSubmissionError, IFormAcknowledgement]] = {
    val url = url"$baseUrl/iforms-channel/form/$formName/version/$envelopeVersion"
    httpClient
      .post(url)(HeaderCarrier())
      .transform(_.withRequestTimeout(readTimeout))
      .setHeader(
        "x-iform-envelope-version" -> envelopeVersion,
        "content-type"             -> "text/xml"
      )
      .withBody(xml)
      .execute[HttpResponse]
      .map { response =>
        if response.status == 200 then IFormAcknowledgement.fromEnvelope(response.body)
        else {
          logger.error(s"[IFormSubmissionConnector][submit] iForms returned ${response.status}: ${response.body}")
          Left(IFormSubmissionError.UnexpectedStatus(response.status, response.body))
        }
      }
      .recover { case NonFatal(e) =>
        logger.error(s"[IFormSubmissionConnector][submit] no response from iForms for form $formName", e)
        Left(IFormSubmissionError.NoResponse(e))
      }
  }
}
