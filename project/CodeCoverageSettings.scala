import sbt.Keys.parallelExecution
import sbt.{Setting, Test}
import scoverage.ScoverageKeys

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

  val settings: Seq[Setting[_]] = Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";"),
    ScoverageKeys.coverageExcludedFiles := excludedFiles.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageMinimumStmtPerFile := 90,
    ScoverageKeys.coverageMinimumBranchTotal:= 88,
    ScoverageKeys.coverageMinimumBranchPerFile:= 65, //this should really be increased asap some files are letting the file team down :D
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
  )
}
