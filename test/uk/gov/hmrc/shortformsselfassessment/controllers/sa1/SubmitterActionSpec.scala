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

import org.scalatestplus.play.PlaySpec
import play.api.mvc.{Action, AnyContent, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.{AffinityGroup, NoActiveSession}

class SubmitterActionSpec extends PlaySpec {

  private val cc = stubControllerComponents()

  private def handlerFor(authConnector: FakeAuthConnector): Action[AnyContent] =
    (cc.actionBuilder andThen new SubmitterAction(authConnector)(cc.executionContext)) { request =>
      Results.Ok(s"${request.submitter.credentialId}|${request.submitter.sessionId}|${request.submitter.affinityGroup}")
    }

  private val authorisedRequest = FakeRequest().withHeaders(
    SubmitterAction.AuthorisationHeader -> "Bearer gateway-token",
    SubmitterAction.SessionIdHeader     -> "session-123"
  )

  "SubmitterAction" should {
    "derive the credential ID and affinity group by authorising the forwarded token" in {
      val authConnector = new FakeAuthConnector()

      contentAsString(handlerFor(authConnector)(authorisedRequest)) mustBe "cred-123|session-123|Some(Agent)"
      authConnector.lastAuthorization mustBe Some("Bearer gateway-token")
    }

    "treat a missing affinity group as absent" in {
      val authConnector = new FakeAuthConnector(affinityGroup = None)

      contentAsString(handlerFor(authConnector)(authorisedRequest)) mustBe "cred-123|session-123|None"
    }

    "return 401 Unauthorized when the Authorization header is missing" in {
      val request = FakeRequest().withHeaders(SubmitterAction.SessionIdHeader -> "session-123")

      status(handlerFor(new FakeAuthConnector())(request)) mustBe UNAUTHORIZED
    }

    "return 400 Bad Request when the session ID header is missing" in {
      val request = FakeRequest().withHeaders(SubmitterAction.AuthorisationHeader -> "Bearer gateway-token")

      status(handlerFor(new FakeAuthConnector())(request)) mustBe BAD_REQUEST
    }

    "return 401 Unauthorized when the session is no longer active" in {
      val authConnector = new FakeAuthConnector(failure = Some(new NoActiveSession("expired") {}))

      status(handlerFor(authConnector)(authorisedRequest)) mustBe UNAUTHORIZED
    }

    "return 403 Forbidden when the token carries no credentials" in {
      val authConnector = new FakeAuthConnector(credentials = None, affinityGroup = Some(AffinityGroup.Individual))

      status(handlerFor(authConnector)(authorisedRequest)) mustBe FORBIDDEN
    }
  }
}
