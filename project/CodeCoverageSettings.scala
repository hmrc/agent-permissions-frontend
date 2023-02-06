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
    "views.html.timeout",
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
    ".*manage_clients_list.template",
    ".*client_not_found.template",
    ".*team_member_not_found.template",
    ".*manage_team_members.template",
    ".*client_details.template",
    ".*confirm_added.template",
    ".*group_not_found.template",
    ".*unassigned_clients_list.template",
    ".*PaginationUtil.*",
    ".*ViewUtils.*",
    ".*GroupAction.*",
    ".*ClientAction.*",
    ".*TeamMemberAction.*",
    ".*TimeoutController.*",
    ".*select_paginated_clients.template",
    ".*review_update_tax_group_team_members.template",
    ".*review_update_team_members.template",
    ".*review_members_paginated.template",
    ".*review_clients_paginated.template",
    ".*GET_pagination.template",
    ".*POST_pagination.template",
    ".*pagination_li.template",
    ".*search_clients.template"
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
