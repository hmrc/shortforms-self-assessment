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

import org.w3c.dom.Document
import org.xml.sax.{ErrorHandler, SAXParseException}

import javax.xml.XMLConstants
import javax.xml.transform.dom.DOMSource
import javax.xml.validation.{Schema, SchemaFactory}
import scala.util.Try

object SA1XmlValidator {

  private lazy val schema: Schema =
    SchemaFactory
      .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
      .newSchema(getClass.getResource("/sa1/ShortForm.xsd"))

  def validate(shortForm: Document): Either[String, Unit] = {
    val validator = schema.newValidator()
    validator.setErrorHandler(new ErrorHandler {
      override def warning(e: SAXParseException): Unit = ()
      override def error(e: SAXParseException): Unit = throw e
      override def fatalError(e: SAXParseException): Unit = throw e
    })
    Try(validator.validate(new DOMSource(shortForm))).toEither.left
      .map(e => s"ShortForm failed XSD validation: ${e.getMessage}")
      .map(_ => ())
  }
}
