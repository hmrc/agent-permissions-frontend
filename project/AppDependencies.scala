import sbt._

object AppDependencies {

  private val mongoVer: String = "1.9.0"
  private val bootstrapVer: String = "8.6.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"        %% "bootstrap-frontend-play-30"            % bootstrapVer,
    "uk.gov.hmrc.mongo"  %% "hmrc-mongo-play-30"                    % mongoVer,
    "uk.gov.hmrc"        %% "play-frontend-hmrc-play-30"            % "8.5.0",
    "uk.gov.hmrc"        %% "play-conditional-form-mapping-play-30" % "2.0.0",
    "uk.gov.hmrc"        %% "agent-mtd-identifiers"                 % "2.0.0",
    "uk.gov.hmrc"        %% "crypto-json-play-30"                   % "8.1.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVer  % Test,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % mongoVer      % Test,
    "org.scalamock"           %% "scalamock"                  % "6.0.0"       % Test
  )
}
