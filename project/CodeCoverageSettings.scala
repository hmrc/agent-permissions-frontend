import sbt.Keys.parallelExecution
import sbt.{Setting, Test}
import scoverage.ScoverageKeys

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq(
    "<empty>",
    "Reverse.*",
    "uk.gov.hmrc.BuildInfo",
    "views.html",
    "views.html.components",
    "views.html.partials",
    "app.assets.*",
    "prod.*",
    ".*Routes.*",
    "testOnly.*",
    "testOnlyDoNotUseInAppConf.*"
  )

  private val excludedFiles: Seq[String] = Seq(
    ".*group_created.template",
    ".*complete.template",
    ".*view_team_members.template",
    ".*group_not_found.template",
    ".*ViewUtils.*",
    ".*GroupAction.*",
  )

  val settings: Seq[Setting[_]] = Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";"),
    ScoverageKeys.coverageExcludedFiles := excludedFiles.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageMinimumStmtPerFile := 90,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
  )
}
