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

import org.w3c.dom.{Document, Element}
import play.api.Logging
import uk.gov.hmrc.shortformsselfassessment.models.sa1.{SA1Request, Submitter}
import uk.gov.hmrc.shortformsselfassessment.utils.DateTimeFormats

import java.io.StringWriter
import java.time.LocalDate

import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.{OutputKeys, TransformerFactory}

object SA1XmlGenerator extends Logging {

  private val EnvelopeNamespace  = "http://www.hmrc.gov.uk/iforms-channel/submission/envelope/request/1"
  private val ShortFormNamespace = "http://www.hmrc.gov.uk/iforms-channel/shortform/1"
  private val FormId             = "SA1"
  private val VendorId           = "9998"
  private val VendorName         = "vendorName"
  private val ProductName        = "Short Form Portal Application"
  private val ProductVersion     = "1"
  private val AgentUserType      = "Agent"
  private val OtherUserType      = "Other"
  private val Language           = "eng"
  private val Checked            = "true"

  private val DateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  def generate(request: SA1Request, submitter: Submitter, submittedAt: ZonedDateTime): Either[String, String] = {
    val shortForm = shortFormDocument(request, submitter.sessionId, submittedAt)
    SA1XmlValidator.validate(shortForm).map { _ =>
      // Temporary validation logging: the XML contains personal data, so remove before merging.
      val xml = envelopeXml(shortForm, submitter.credentialId, submittedAt, submitterUserType(submitter.affinityGroup))
      logger.info(s"[SA1XmlGenerator][generate] generated SA1 XML payload:\n$xml")
      xml
      // TODO:
    }
  }

  private[sa1] def submitterUserType(affinityGroup: Option[String]): String =
    if (affinityGroup.contains("Agent")) AgentUserType else OtherUserType

  private[sa1] def dataItems(request: SA1Request): Seq[(String, String)] = {
    val address                                     = request.address
    val line2                                       = address.line2.trim
    val town                                        = trimmedNonEmpty(address.town)
    val addressItems: Seq[(String, Option[String])] =
      if (line2.nonEmpty)
        Seq("address14" -> Some(address.line1.trim), "address15" -> Some(line2), "address16" -> town)
      else
        Seq("address14" -> Some(address.line1.trim), "address15" -> town)

    val reasons                                    = request.reasons
    val reasonItems: Seq[(String, Option[String])] =
      checkboxWithDate("checkbox123", "date24", reasons.companyDirectorSince) ++
        checkboxWithDate("checkbox125", "date26", reasons.dateLandPropertyUk) ++
        checkboxWithDate("checkbox127", "date28", reasons.taxableForeignIncomeDate) ++
        checkboxWithDate("checkbox129", "date30", reasons.dateAnnualIncome) ++
        checkboxWithDate("checkbox131", "date32", reasons.dateIncomeThresholds) ++
        checkboxWithDate("checkbox133", "date34", reasons.untaxedIncomeNotInPayeStartDate) ++
        checkboxWithDate("checkbox136", "date37", reasons.highIncomeChildBenefitDate) ++
        checkboxWithDate("checkbox138", "date39", reasons.dateCapitalGainsTax) ++
        Seq(
          "text440" -> trimmedNonEmpty(reasons.otherReason),
          "date41"  -> reasons.otherReasonAppliesSince.map(formatDate)
        )

    val items: Seq[(String, Option[String])] =
      Seq(
        "title2"            -> trimmedNonEmpty(request.title),
        "name3"             -> Some(request.lastName.trim),
        "name4"             -> Some(request.firstName.trim),
        "name5"             -> trimmedNonEmpty(request.previousLastName),
        "date6"             -> request.dateLastNameChanged.map(formatDate),
        "nino17"            -> trimmedNonEmpty(request.niNumber.map(_.replaceAll("\\s", ""))),
        "text49"            -> trimmedNonEmpty(request.niNoReason),
        "dateOfBirth10"     -> Some(formatDate(request.dob)),
        "postcode17"        -> Some(address.postcode.trim),
        "dateCR8414"        -> Some(formatDate(request.moveToAddressOn)),
        "telephoneNumber18" -> Some(request.phone.trim),
        "emailAddress19"    -> Some(request.email.trim),
        "text546"           -> trimmedNonEmpty(request.personCompletingForm),
        "checkbox145"       -> Some(Checked)
      ) ++ addressItems ++ reasonItems

    items.collect { case (name, Some(value)) => name -> value }
  }

  private def shortFormDocument(request: SA1Request, sessionId: String, submittedAt: ZonedDateTime): Document = {
    val doc  = newDocument()
    val root = doc.createElementNS(ShortFormNamespace, "ShortForm")
    root.setAttribute("ShortForm_ID", FormId)
    doc.appendChild(root)

    val metaData = child(doc, root, "MetaData")
    child(doc, metaData, "WelshLanguage", "false")
    child(doc, metaData, "Submitted_Date", DateTimeFormats.xmlDateTimeFormat.format(submittedAt))
    child(doc, metaData, "Session_ID", sessionId)

    val submittedData = child(doc, root, "SubmittedData")
    dataItems(request).foreach { case (name, value) =>
      val item = child(doc, submittedData, "DataItem", value)
      item.setAttribute("Name", name)
    }
    doc
  }

  private def envelopeXml(
    shortForm: Document,
    credentialId: String,
    createdAt: ZonedDateTime,
    userType: String
  ): String = {
    val doc      = newDocument()
    val envelope = doc.createElementNS(EnvelopeNamespace, "Envelope")
    doc.appendChild(envelope)

    val header    = child(doc, envelope, "Header")
    val client    = child(doc, header, "Client")
    val vendor    = child(doc, client, "Vendor")
    child(doc, vendor, "Id", VendorId)
    child(doc, vendor, "Name", VendorName)
    val product   = child(doc, client, "Product")
    child(doc, product, "Name", ProductName)
    child(doc, product, "Version", ProductVersion)
    val submitter = child(doc, header, "Submitter")
    child(doc, submitter, "UserType", userType)
    child(doc, submitter, "CredentialID", credentialId)
    child(doc, header, "Language", Language)
    child(doc, header, "DateCreated", DateTimeFormats.xmlDateTimeFormat.format(createdAt))

    val body = child(doc, envelope, "Body")
    body.appendChild(doc.importNode(shortForm.getDocumentElement, true))

    serialise(doc)
  }

  private def checkboxWithDate(
    checkbox: String,
    dateField: String,
    date: Option[LocalDate]
  ): Seq[(String, Option[String])] =
    date.toSeq.flatMap(d => Seq(checkbox -> Some(Checked), dateField -> Some(formatDate(d))))

  private def trimmedNonEmpty(value: Option[String]): Option[String] =
    value.map(_.trim).filter(_.nonEmpty)

  private def formatDate(date: LocalDate): String = date.format(DateFormatter)

  private def child(doc: Document, parent: Element, name: String, text: String = ""): Element = {
    val element = doc.createElementNS(parent.getNamespaceURI, name)
    if (text.nonEmpty) element.setTextContent(text)
    parent.appendChild(element)
    element
  }

  private def newDocument(): Document = {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setNamespaceAware(true)
    factory.newDocumentBuilder().newDocument()
  }

  private def serialise(doc: Document): String = {
    val transformer = TransformerFactory.newInstance().newTransformer()
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    val writer      = new StringWriter()
    transformer.transform(new DOMSource(doc), new StreamResult(writer))
    writer.toString
  }
}
