import sbt.Setting
import scoverage.ScoverageKeys
import sbt.*
import sbt.Keys.*
object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq(
    "<empty>",
    "Reverse.*",
    "app.assets.*",
    "prod.*",
    ".*Routes.*",
    "testOnly.*",
    "testOnlyDoNotUseInAppConf.*"
  )

  private val excludedFiles: Seq[String] = Seq(
    ".*.template",
    ".*ViewUtils.*",
    ".*FilterUtils.*",
    ".*GroupAction.*",
    ".*ClientAction.*",
    ".*TeamMemberAction.*",
    ".*TimeoutController.*",
  )

  val settings: Seq[Setting[?]] = Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";"),
    ScoverageKeys.coverageExcludedFiles := excludedFiles.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 85,
    ScoverageKeys.coverageMinimumBranchTotal:= 85,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
  )
}
