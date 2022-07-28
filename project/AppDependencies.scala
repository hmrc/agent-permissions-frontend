import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-frontend-play-28" % "6.4.0",
    "uk.gov.hmrc"             %% "play-frontend-hmrc"         % "3.22.0-play-28",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-28"         % "0.68.0",
    "uk.gov.hmrc"             %% "agent-mtd-identifiers"      % "0.45.0-play-28",
    "uk.gov.hmrc"             %% "agent-kenshoo-monitoring"   % "4.8.0-play-28"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-28"     % "6.4.0"            % "test, it",
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-28"    % "0.63.0"            % "test, it",
    "uk.gov.hmrc"             %% "reactivemongo-test"         % "5.0.0-play-28"     % "test, it",
    "org.jsoup"               %  "jsoup"                      % "1.13.1"            % "test, it",
    "org.scalamock"           %% "scalamock"                  % "5.2.0"             % "test, it",
    "com.vladsch.flexmark"    %  "flexmark-all"               % "0.36.8"            % "test, it"
  )
}
