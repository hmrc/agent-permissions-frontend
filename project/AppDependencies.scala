import sbt._

object AppDependencies {

  private val mongoVer: String = "2.11.0"
  private val bootstrapVer: String = "10.5.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-30"            % bootstrapVer,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-30"                    % mongoVer,
    "uk.gov.hmrc"       %% "play-frontend-hmrc-play-30"            % "12.24.0",
    "uk.gov.hmrc"       %% "play-conditional-form-mapping-play-30" % "3.4.0",
    "uk.gov.hmrc"       %% "crypto-json-play-30"                   % "8.4.0",
    "uk.gov.hmrc"       %% "domain-play-30"                        % "11.0.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% "bootstrap-test-play-30"  % bootstrapVer % Test,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-30" % mongoVer     % Test,
    "org.scalamock"     %% "scalamock"               % "7.4.1"      % Test,
    "org.scalacheck"    %% "scalacheck"              % "1.18.1"     % Test
  )
}
