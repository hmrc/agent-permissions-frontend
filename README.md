
# agent-permissions-frontend

Access groups (aka Granular Permissions) is a feature of the Agent Services Account. 
This allows agent firms to control which team members can manage each client’s tax. 
They do this by creating custom access groups of clients and team members, or tax service groups that allowlist a regime for a list of team members.

Any client that is added to an access group is accessible only by the team members in the group. If a client does not belong to any access group then they would be accessible to all team members.

Each agent must meet an eligibility criteria before they can use access groups. The criteria is the agent must have at least 1 team member and 1 client and no more than a maximum number of clients.

Team members are government gateway accounts created by an Administrator (Admin or User cred role) - so that they share the same groupId for the Agency

## Journeys & pages
- opt-in & opt-out journey
- create an access group (custom or tax service)
- manage access groups
  - rename group
  - delete group
  - manage clients in a group
  - add or remove team members in a group
- manage clients
  - view full list
  - details pages
  - update client reference
  - unassigned clients list
  - add to existing custom groups
- manage team members 
  - view full list
  - details pages
  - add to existing custom groups
- standard user (assistant) views clients they have access to and unassigned list


### FE Dependencies
The [sortable table](https://design-patterns.service.justice.gov.uk/components/sortable-table/) & [multi-select](https://design-patterns.service.justice.gov.uk/components/multi-select/) components have been adapted from [MOJ frontend](https://github.com/ministryofjustice/moj-frontend/releases) via importing compiled files - currently using version 1.4.2 (modified, will need updating)

MOJ frontend [requires jQuery](https://design-patterns.service.justice.gov.uk/get-started/setting-up-javascript/), it is included as a minified file rather than a link to external source due to security issues.


#### Pagination

We have two methods pagination, GET and POST

GET pagination is traditional, a link to the next page
POST pagination is used when we want to persist selections/deselection between pages, and/or search & filter terms.

### Running the tests

    sbt "test;IntegrationTest/test"

### Running the tests with coverage

    sbt "clean;coverageOn;test;IntegrationTest/test;coverageReport"

### Running the app locally

    sm --start AGENT_AUTHORISATION -r
    sm --stop AGENT_PERMISSIONS_FRONTEND
    sbt run

It should then be listening on port 9452

    browse http://localhost:9401/agent-services-account/manage-account  
    Note: This agent-services-account however the start of the journey for agent-permissions-frontend


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
