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
    scalaVersion              := "2.12.15",
    PlayKeys.playDefaultPort  := 9452,
    libraryDependencies       ++= AppDependencies.compile ++ AppDependencies.test,
    Assets / pipelineStages   := Seq(gzip),
    // Use the silencer plugin to suppress warnings
    scalacOptions += "-P:silencer:pathFilters=routes",
    // silence all warnings on autogenerated files
    scalacOptions += "-P:silencer:pathFilters=target/.*",
    // Make sure you only exclude warnings for the project directories, i.e. make builds reproducible
    scalacOptions += s"-P:silencer:sourceRoots=${baseDirectory.value.getCanonicalPath}",
    // Suppress warnings due to mongo dates using `$date` in their Json representation
    scalacOptions += "-P:silencer:globalFilters=possible missing interpolator: detected interpolated identifier `\\$date`",
    scalacOptions += "-Ywarn-macros:after",
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
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
