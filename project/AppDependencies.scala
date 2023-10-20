import sbt._

object AppDependencies {

  private val mongoVer: String = "1.3.0"
  private val bootstrapVer: String = "7.22.0"

  val compile = Seq(
    "uk.gov.hmrc"        %% "bootstrap-frontend-play-28"     % bootstrapVer,
    "uk.gov.hmrc.mongo"  %% "hmrc-mongo-play-28"             % mongoVer,
    "uk.gov.hmrc"        %% "play-frontend-hmrc"             % "7.23.0-play-28",
    "uk.gov.hmrc"        %% "play-conditional-form-mapping"  % "1.13.0-play-28",
    "uk.gov.hmrc"        %% "agent-mtd-identifiers"          % "1.14.0",
    "uk.gov.hmrc"        %% "agent-kenshoo-monitoring"       % "5.5.0",
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % bootstrapVer  % "test, it",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % mongoVer   % "test, it",
    "org.jsoup"               %  "jsoup"                      % "1.15.4"   % "test, it",
    "org.scalamock"           %% "scalamock"                  % "5.2.0"    % "test, it",
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.36.8"   % "test, it",
  )
}
