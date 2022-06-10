import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

val appName = "agent-permissions-frontend"

val silencerVersion = "1.7.8"

TwirlKeys.templateImports ++= Seq(
  "views.html.components._",
  "views.html.main_layout",
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
    pipelineStages in Assets  := Seq(gzip),
    // ***************
    // Use the silencer plugin to suppress warnings
    scalacOptions += "-P:silencer:pathFilters=routes",
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    )
    // ***************
  )
  .settings(publishingSettings: _*)
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers ++= Seq(Resolver.jcenterRepo))
  .settings(CodeCoverageSettings.settings: _*)
  .settings(IntegrationTest / Keys.fork := false)
  .settings(IntegrationTest / parallelExecution := false)

