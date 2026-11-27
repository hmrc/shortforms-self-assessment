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

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.http.Fault
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.shortformsselfassessment.connectors.IFormSubmissionError.{MalformedResponse, NoResponse, UnexpectedStatus}

import java.time.Instant
import scala.concurrent.duration.*
import scala.concurrent.Await

class IFormSubmissionConnectorISpec extends PlaySpec with GuiceOneAppPerSuite with WireMockSupport {

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.iforms-submission.protocol" -> "http",
        "microservice.services.iforms-submission.host" -> "localhost",
        "microservice.services.iforms-submission.port" -> wireMockPort,
        "microservice.services.iforms-submission.read-timeout" -> "5 seconds"
      )
      .build()

  private val submitUrl         = "/iforms-channel/form/nps-shortform-i/version/1"
  private val xml               = "<SA1>payload</SA1>"
  private val receivedTimestamp = Instant.parse("2026-11-13T14:54:23.038Z")
  private val validEnvelope     =
    """<ns:Response xmlns:ns="urn:iforms">
      |  <ns:AcknowledgmentReference>ACK-123</ns:AcknowledgmentReference>
      |  <ns:ReceivedTimestamp>2026-11-13T14:54:23.038Z</ns:ReceivedTimestamp>
      |</ns:Response>""".stripMargin

  private lazy val connector = app.injector.instanceOf[IFormSubmissionConnector]

  private def submit(): Either[IFormSubmissionError, IFormAcknowledgement] =
    Await.result(connector.submit(xml), 10.seconds)

  "IFormSubmissionConnector" should {
    "send the XML with the iForms headers and return the acknowledgement for a 200 response" in {
      stubFor(post(urlEqualTo(submitUrl)).willReturn(ok(validEnvelope)))

      submit() mustBe Right(IFormAcknowledgement("ACK-123", receivedTimestamp))
      verify(
        postRequestedFor(urlEqualTo(submitUrl))
          .withHeader("x-iform-envelope-version", equalTo("1"))
          .withHeader("Content-Type", containing("text/xml"))
          .withRequestBody(equalTo(xml))
      )
    }

    "return UnexpectedStatus with the response body for a non-200 response" in {
      stubFor(post(urlEqualTo(submitUrl)).willReturn(aResponse().withStatus(500).withBody("iForms failure")))

      submit() mustBe Left(UnexpectedStatus(500, "iForms failure"))
    }

    "read a ReceivedTimestamp without a zone offset as London time" in {
      val envelope =
        """<ns:Envelope xmlns:ns="http://www.hmrc.gov.uk/iforms-channel/submission/envelope/response/1">
          |  <ns:Header><ns:ReceivedTimestamp>2026-07-01T14:54:23.038</ns:ReceivedTimestamp></ns:Header>
          |  <ns:Body><ns:Success><ns:AcknowledgmentReference>ACK-123</ns:AcknowledgmentReference></ns:Success></ns:Body>
          |</ns:Envelope>""".stripMargin
      stubFor(post(urlEqualTo(submitUrl)).willReturn(ok(envelope)))

      submit() mustBe Right(IFormAcknowledgement("ACK-123", Instant.parse("2026-07-01T13:54:23.038Z")))
    }

    "return MalformedResponse when a 200 response has no acknowledgement reference" in {
      stubFor(
        post(urlEqualTo(submitUrl)).willReturn(
          ok("<ns:Response><ns:ReceivedTimestamp>2026-11-13T14:54:23.038Z</ns:ReceivedTimestamp></ns:Response>")
        )
      )

      submit() mustBe a[Left[MalformedResponse, ?]]
    }

    "return NoResponse when the connection to iForms fails" in {
      stubFor(post(urlEqualTo(submitUrl)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

      submit() mustBe a[Left[NoResponse, ?]]
    }
  }
}
