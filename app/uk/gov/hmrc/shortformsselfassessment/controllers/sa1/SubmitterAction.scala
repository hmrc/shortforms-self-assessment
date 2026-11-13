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

import controllers.sa1.SubmitterAction.{AuthorisationHeader, SessionIdHeader}
import play.api.libs.json.Json
import play.api.mvc.{ActionRefiner, Request, Result, Results, WrappedRequest}
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisationException, NoActiveSession}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import uk.gov.hmrc.shortformsselfassessment.models.sa1.Submitter

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

object SubmitterAction {
  val AuthorisationHeader: String = "Authorization"
  val SessionIdHeader: String     = "X-Session-ID"
}

final class SubmitterRequest[A](val submitter: Submitter, request: Request[A]) extends WrappedRequest[A](request)
@Singleton
class SubmitterAction @Inject() (authConnector: AuthConnector)(implicit val executionContext: ExecutionContext)
    extends ActionRefiner[Request, SubmitterRequest] {

  override protected def refine[A](request: Request[A]): Future[Either[Result, SubmitterRequest[A]]] = {
    def header(name: String): Option[String] = request.headers.get(name).map(_.trim).filter(_.nonEmpty)

    (header(AuthorisationHeader), header(SessionIdHeader)) match {
      case (None, _)                      =>
        Future.successful(Left(Results.Unauthorized(Json.obj("message" -> s"Missing $AuthorisationHeader header"))))
      case (_, None)                      =>
        Future.successful(Left(Results.BadRequest(Json.obj("message" -> s"Missing $SessionIdHeader header"))))
      case (Some(token), Some(sessionId)) =>
        authorise(token, sessionId, request)
    }
  }

  private def authorise[A](
    token: String,
    sessionId: String,
    request: Request[A]
  ): Future[Either[Result, SubmitterRequest[A]]] =
    authConnector
      .authorise(EmptyPredicate, Retrievals.credentials and Retrievals.affinityGroup)(
        HeaderCarrier(authorization = Some(Authorization(token))),
        executionContext
      )
      .map[Either[Result, SubmitterRequest[A]]] {
        case Some(credentials) ~ affinityGroup =>
          val submitter = Submitter(credentials.providerId, sessionId, affinityGroup.map(_.toString))
          Right(new SubmitterRequest(submitter, request))
        case None ~ _                          =>
          Left(Results.Forbidden(Json.obj("message" -> "No credentials available for the caller")))
      }
      .recover[Either[Result, SubmitterRequest[A]]] {
        case _: NoActiveSession        => Left(Results.Unauthorized(Json.obj("message" -> "No active session")))
        case _: AuthorisationException => Left(Results.Forbidden(Json.obj("message" -> "Not authorised")))
      }
}
