import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

val appName = "agent-permissions-frontend"

val silencerVersion = "1.7.8"

TwirlKeys.templateImports ++= Seq(
  "views.html.components._",
  "views.html.main_layout",
  "utils.ViewUtils._",
  "uk.gov.hmrc.govukfrontend.views.html.components._",
  "uk.gov.hmrc.hmrcfrontend.views.html.components._",
)

lazy val microservice = Project(appName, file("."))
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .settings(
    majorVersion              := 0,
    scalaVersion              := "2.13.10",
    PlayKeys.playDefaultPort  := 9452,
    libraryDependencies       ++= AppDependencies.compile ++ AppDependencies.test,
    //fix for scoverage compile errors for scala 2.13.10
    libraryDependencySchemes ++= Seq("org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always),
    Assets / pipelineStages   := Seq(gzip),
    scalacOptions ++= Seq(
      "-nowarn",
      "-Wdead-code",
      "-Xlint",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-language:implicitConversions",
      "-Wconf:src=target/.*:s", // silence warnings from compiled files
      "-Wconf:msg=match may not be exhaustive:s", // silence warnings about non-exhaustive pattern matching
    )
  )
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers ++= Seq(Resolver.jcenterRepo))
  .settings(CodeCoverageSettings.settings: _*)
  .settings(IntegrationTest / Keys.fork := false)
  .settings(IntegrationTest / parallelExecution := false)
  //v Required to prevent https://github.com/scalatest/scalatest/issues/1427 (unstable build due to failure to read test reports)
  .disablePlugins(JUnitXmlReportPlugin)
