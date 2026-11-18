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

package uk.gov.hmrc.shortformsselfassessment.services.sa1

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.shortformsselfassessment.models.sa1.{Address, SA1Request, SelfAssessmentReasons, Submitter}

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import javax.xml.parsers.DocumentBuilderFactory

class SA1XmlGeneratorSpec extends PlaySpec {

  private val submittedAt = ZonedDateTime.of(2026, 11, 9, 15, 0, 0, 0, ZoneId.of("Europe/London"))

  private def request(
    address: Address = Address(line1 = "221B Baker Street", line2 = "", town = Some("London"), postcode = "NW1 6XE"),
    niNumber: Option[String] = Some("AA 12 34 56 B"),
    reasons: SelfAssessmentReasons = SelfAssessmentReasons(
      companyDirectorSince = Some(LocalDate.of(1889, 1, 1)),
      otherReason = Some("Self-invested pension contributions"),
      otherReasonAppliesSince = Some(LocalDate.of(1888, 6, 1))
    )
  ): SA1Request =
    SA1Request(
      title = Some("Mrs"),
      firstName = "Sherlock",
      lastName = "Holmes",
      previousLastName = None,
      niNumber = niNumber,
      niNoReason = None,
      dob = LocalDate.of(1854, 1, 1),
      address = address,
      moveToAddressOn = LocalDate.of(1887, 1, 1),
      phone = "07 123 456 789",
      email = "sherlock.holmes@consultingdetective.com",
      reasons = reasons,
      personCompletingForm = Some("Sherlock Holmes"),
      dateLastNameChanged = Some(LocalDate.of(1880, 3, 4))
    )

  "SA1XmlGenerator.dataItems" should {
    "map the SA1 request onto iForms field names with DD/MM/YYYY dates and checkbox flags" in {
      SA1XmlGenerator.dataItems(request()).toMap mustBe Map(
        "title2"            -> "Mrs",
        "name3"             -> "Holmes",
        "name4"             -> "Sherlock",
        "date6"             -> "04/03/1880",
        "nino17"            -> "AA123456B",
        "dateOfBirth10"     -> "01/01/1854",
        "address14"         -> "221B Baker Street",
        "address15"         -> "London",
        "postcode17"        -> "NW1 6XE",
        "dateCR8414"        -> "01/01/1887",
        "telephoneNumber18" -> "07 123 456 789",
        "emailAddress19"    -> "sherlock.holmes@consultingdetective.com",
        "checkbox123"       -> "true",
        "date24"            -> "01/01/1889",
        "text440"           -> "Self-invested pension contributions",
        "date41"            -> "01/06/1888",
        "text546"           -> "Sherlock Holmes",
        "checkbox145"       -> "true"
      )
    }

    "place line2 in address15 and town in address16 when line2 is present" in {
      val items = SA1XmlGenerator
        .dataItems(request(address = Address("221B Baker Street", "Flat 2", Some("London"), "NW1 6XE")))
        .toMap

      items("address14") mustBe "221B Baker Street"
      items("address15") mustBe "Flat 2"
      items("address16") mustBe "London"
    }

    "omit empty and unchecked optional fields" in {
      val items = SA1XmlGenerator
        .dataItems(request(niNumber = None, reasons = SelfAssessmentReasons()))
        .toMap

      items.keySet must not contain "nino17"
      items.keySet must not contain "checkbox123"
      items.keySet must not contain "date24"
      items.keySet must not contain "text440"
      items.keySet must not contain "date41"
      items.keySet must not contain "address16"
    }
  }

  "SA1XmlGenerator.generate" should {
    "return an Envelope containing a valid ShortForm with the submitter and session details" in {
      val result = SA1XmlGenerator.generate(request(), Submitter("cred-123", "session-abc", None), submittedAt)

      val xml = result.toOption.getOrElse(fail(s"expected valid XML but got $result"))
      xml must include("<CredentialID>cred-123</CredentialID>")
      xml must include("<UserType>Other</UserType>")
      xml must include("<Id>9998</Id>")
      xml must include("<Session_ID>session-abc</Session_ID>")
      xml must include("""<Submitted_Date>2026-11-09T15:00:00.000Z</Submitted_Date>""")
      xml must include("""ShortForm_ID="SA1"""")
      xml must include("""Name="name3">Holmes</DataItem>""")
    }

    "set the submitter user type to Agent for agent submissions" in {
      val result = SA1XmlGenerator.generate(request(), Submitter("cred-123", "session-abc", Some("Agent")), submittedAt)

      val xml = result.toOption.getOrElse(fail(s"expected valid XML but got $result"))
      xml must include("<UserType>Agent</UserType>")
      xml must not include "<UserType>Other</UserType>"
    }

    "format DateCreated and Submitted_Date in UTC with milliseconds and a Z suffix" in {
      val createdAt = ZonedDateTime.of(2026, 1, 13, 10, 58, 47, 301000000, ZoneId.of("Europe/London"))
      val result    = SA1XmlGenerator.generate(request(), Submitter("cred-123", "session-abc", None), createdAt)

      val xml = result.toOption.getOrElse(fail(s"expected valid XML but got $result"))
      xml must include("<DateCreated>2026-01-13T10:58:47.301Z</DateCreated>")
      xml must include("<Submitted_Date>2026-01-13T10:58:47.301Z</Submitted_Date>")
    }
  }

  "SA1XmlGenerator.submitterUserType" should {
    "map the Agent affinity group to Agent and everything else to Other" in {
      SA1XmlGenerator.submitterUserType(Some("Agent")) mustBe "Agent"
      SA1XmlGenerator.submitterUserType(Some("Individual")) mustBe "Other"
      SA1XmlGenerator.submitterUserType(Some("Organisation")) mustBe "Other"
      SA1XmlGenerator.submitterUserType(None) mustBe "Other"
    }
  }

  "SA1XmlValidator.validate" should {
    "reject a ShortForm document that does not match the XSD" in {
      val factory = DocumentBuilderFactory.newInstance()
      factory.setNamespaceAware(true)
      val doc     = factory.newDocumentBuilder().newDocument()
      doc.appendChild(doc.createElementNS("http://www.hmrc.gov.uk/iforms-channel/shortform/1", "ShortForm"))

      SA1XmlValidator.validate(doc).isLeft mustBe true
    }
  }
}
