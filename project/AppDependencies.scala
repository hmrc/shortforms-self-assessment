import sbt.*

object AppDependencies {

  private val bootstrapVersion = "10.8.0"
  

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % bootstrapVersion
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion            % Test,
    "org.scalatestplus"       %% "scalacheck-1-17"         % "3.2.18.0"
  )

  val it: Seq[ModuleID] = Seq.empty
}
