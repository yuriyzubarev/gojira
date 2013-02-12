# gojira

A Clojure command line tool (CLI) designed to extract data from JIRA via [JIRA REST API](http://docs.atlassian.com/jira/REST/latest/) and build reports.

Text only reports is the only format currently supported.

## Usage

The project is in ins infancy and therefore all examples are shown through 'lein'.

	$ lein run
	Usage: lein run [switches] [snapshot|flow]

	Switches               Default     Desc
	--------               -------     ----
	-h, --no-help, --help  false       Show help
	-u, --user             	           Username
	-p, --password                     Password
	-s, --sprint                       Sprint ID
	-j, --jira-api-url     	           JIRA API URL

## Samples

Tabs are used to separate values - to be pasted in a spreadsheet.

### Snapshot

Layout

	url	status	story points	epic	key and summary
	
Output

	https://jira/jira/rest/api/2/issue/74765   Open    8   World Peace    WP-0001 Breed white doves
	https://jira/jira/rest/api/2/issue/74540   In Progress 13   World Peace    WP-0002 Stop military complex
	https://jira/jira/rest/api/2/issue/74539   Closed  5   World Peace    Eliminate taxes

### Flow

	https://jira/jira/rest/api/2/issue/74765   Open    8   World Peace    WP-0001 Breed white doves	
	https://jira/jira/rest/api/2/issue/74540   In Progress 13   World Peace    WP-0002 Stop military complex
	Open	In Progress	2013-01-01T00:00:00.000-0800
	https://jira/jira/rest/api/2/issue/74539   Closed  5   World Peace    Eliminate taxes
	Open	In Progress	2013-01-04T00:00:00.000-0800
	In Progress	In Test	2013-01-09T00:00:00.000-0800
	In Test	Closed	2013-01-23T00:00:00.000-0800	

## License

Copyright © 2013 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
