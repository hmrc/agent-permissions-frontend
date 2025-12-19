import sbt._

object AppDependencies {

  private val playVer: String = "play-30"
  private val mongoVer: String = "2.11.0"
  private val bootstrapVer: String = "10.5.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-frontend-$playVer"            % bootstrapVer,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVer"                    % mongoVer,
    "uk.gov.hmrc"       %% s"play-frontend-hmrc-$playVer"            % "12.10.0",
    "uk.gov.hmrc"       %% s"play-conditional-form-mapping-$playVer" % "3.4.0",
    "uk.gov.hmrc"       %% s"crypto-json-$playVer"                   % "8.4.0",
    "uk.gov.hmrc"       %% s"domain-$playVer"                        % "11.0.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"       %% s"bootstrap-test-$playVer"  % bootstrapVer,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test-$playVer" % mongoVer,
    "org.scalamock"     %% "scalamock"                 % "7.5.2",
    "org.scalacheck"    %% "scalacheck"                % "1.19.0"
  ).map(_ % Test)
}
