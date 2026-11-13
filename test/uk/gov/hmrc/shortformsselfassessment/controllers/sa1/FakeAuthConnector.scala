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

import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, AuthConnector}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class FakeAuthConnector(
  credentials: Option[Credentials] = Some(Credentials("cred-123", "GovernmentGateway")),
  affinityGroup: Option[AffinityGroup] = Some(AffinityGroup.Agent),
  failure: Option[Throwable] = None
) extends AuthConnector {

  @volatile var lastAuthorization: Option[String] = None

  override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[A] = {
    lastAuthorization = hc.authorization.map(_.value)
    failure match {
      case Some(error) => Future.failed(error)
      case None        => Future.successful(new ~(credentials, affinityGroup).asInstanceOf[A])
    }
  }
}
