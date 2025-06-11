import uk.gov.hmrc.{DefaultBuildSettings, SbtAutoBuildPlugin}
import CodeCoverageSettings.{settings}

val appName = "agent-permissions-frontend"

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "2.13.16"

TwirlKeys.templateImports ++= Seq(
  "views.html.components._",
  "views.html.main_layout",
  "utils.ViewUtils._",
  "uk.gov.hmrc.govukfrontend.views.html.components._",
  "uk.gov.hmrc.hmrcfrontend.views.html.components._",
)

val scalaCOptions = Seq(
  "-Werror",
  "-Wdead-code",
  "-Xlint",
  "-Wconf:src=target/.*:s", // silence warnings from compiled files
  "-Wconf:src=*html:w", // silence html warnings as they are wrong
  "-Wconf:cat=deprecation:s",
  "-Wconf:cat=unused-privates:s",
  "-Wconf:msg=match may not be exhaustive:is", // summarize warnings about non-exhaustive pattern matching
)


lazy val root = (project in file("."))
  .settings(
    name := appName,
    organization := "uk.gov.hmrc",
    PlayKeys.playDefaultPort := 9452,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    resolvers ++= Seq(Resolver.typesafeRepo("releases")),
    scalacOptions ++= scalaCOptions,
//    Assets / pipelineStages   := Seq(gzip),
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "resources"
  )
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(
    Test / parallelExecution := false,
    settings
  )
  .enablePlugins(PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)


lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(root % "test->test") // the "test->test" allows reusing test code and test dependencies
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.test)
  .settings(
    Compile / scalafmtOnCompile := true,
    Test / scalafmtOnCompile := true
  )


import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

lazy val microservice = Project(appName, file("."))
  .settings(


  )
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)
  .settings(resolvers ++= Seq(Resolver.jcenterRepo))
  .settings(CodeCoverageSettings.settings: _*)
  .settings(IntegrationTest / Keys.fork := false)
  .settings(IntegrationTest / parallelExecution := false)
  //v Required to prevent https://github.com/scalatest/scalatest/issues/1427 (unstable build due to failure to read test reports)
  .disablePlugins(JUnitXmlReportPlugin)
