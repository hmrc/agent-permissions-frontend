
# agent-permissions-frontend



Granular Permissions is a feature of the Agent Services Account. This allows agent firms to control which team members can manage each client’s tax. They do this by creating ‘access groups’ of clients and team members.



Any client that is added to an access group is accessible only by the team members in the group. If a client does not belong to any access group then they would be accessible to all team members.

Each agent must meet an eligibility criteria before they can use access groups. The criteria is the agent must have at least 1 team member and 1 client and no more than a maximum number of clients.



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


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").