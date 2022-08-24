# Anthology Course Evaluations with IDEA Data Portal CLI - Submit Survey Data

This utility provides an example of how to submit survey data to the Anthology Course Evaluations
with IDEA Data Portal using the API. It is a Groovy-based application that uses Gradle as the build tool.

## Building

To build this project, you will need Gradle installed and have access to the required dependencies (if connected to the
internet, they will be downloaded for you).

Once Gradle is setup, you can run the following to get the dependencies downloaded and the code compiled.
```
gradle build
```

### Running Tests

There are currently NO tests in this project.

### Running Snyk

There is a gradle plugin that provides access to the Snyk tooling. To run this, be sure to get your credentials (personal token)
from Snyk (through the Web UI) and store it in your personal gradle.properties file (usually in `~/.gradle/gradle.properties`).

Once that token is setup, you can run:
```
gradle snyk-test
```

to see what issues there are in the code base.

It is also possible to change the Snyk severity configuration on the command line or in the gradle.properties file in the root
of this project.

### Project Dependencies

This project is implemented with Groovy and uses Gradle as the build tool. Therefore, you need to be sure to install
the following:
* [Git](http://git-scm.com/downloads)
* [Java (version 6+)](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
* [Groovy](http://groovy-lang.org/)
* [Gradle](http://gradle.org/installation) (or use [SDKMAN](http://sdkman.io/) to install Gradle)
* Libraries Used
  * [Apache HttpComponents](https://hc.apache.org/index.html)
  * [Apache Commons CLI](http://mvnrepository.com/artifact/commons-cli/commons-cli)
  * [Google GSON](http://mvnrepository.com/artifact/com.google.code.gson/gson)
  * IDEA REST Models (org.ideaedu:rest-models)

## Installing

You can install the application using Gradle as well. You can run the following to install it (relative to the project root)
in build/install/idpc-submit-survey-data.
```
gradle installDist
```

## Running

Once installed, you can run using the following.
```
cd build/install/idpc-submit-survey-data/bin
./idpc-submit-survey-data -a "TestClient" -k "ABCDEFG1234567890" -iid 1029 -h reststage.ideasystem.org -p 80 -sid 12345 -sgid 54321 -t teach -v
```
This will generate random answers for the Teaching Essentials survey (-t) and submit it to the Data Portal that is hosted on
reststage.ideasystem.org (-h, and -p) using the given Data Portal credentials (-a and -k). It will associate the data with the
given institution (-iid) and use the given a source survey ID and source group ID (-sid and -sdig). It will provide verbose output (-v)
so you can see what data is pulled down (question IDs) and what data is submitted.

The following command line parameters are available:

Short | Long             | Required | Default             | Description
------|------------------|----------|---------------------|------------
v     | verbose          | No       | Off                 | Provide verbose output
s     | ssl              | No       | Off                 | Connect via SSL
h     | host             | No       | localhost           | The host that provides the Data Portal API
p     | port             | No       | 8091                | The port on the host that is listening for requests.
b     | basePath         | No       | v1                  | The path on the host.
sid   | srcID            | No       | 1                   | The source survey ID.
sgid  | srcGroupID       | No       | 2                   | The source group ID.
iid   | institutionID    | No       | 3019                | The institution ID the data is associated with.
a     | app              | Yes      | None                | The application to connect as (credentials).
k     | key              | Yes      | None                | The key to use (credentials).
t     | type             | No       | Diagnostic          | The type of survey to submit data for. This can be any valid type (diag, Diagnostic, Short, Diagnostic 2016, diag16, Learning Essentials, learn, learning, Teaching Essentials, teach, teaching, Administrator, admin, Chair).
c     | cip              | No       | 01.99               | The cip the data is associated with.
de    | demographics     | No       |                     | The number of demographic groups to select for use in the Administrator survey.
es    | extraScaled      | No       | 0                   | The number of extra scaled questions to add (and answer).
eo    | extraOpen        | No       | 0                   | The number of extra open questions to add (and answer).
ras   | num asked        | No       | 10                  | The number of respondents asked to respond.  Useful with -de (demographics) to make sure there are enough respondents in each demographic group.
ran   | num answered     | No       | 10                  | The number of respondents that answered questions.  Useful with -de (demographics) to make sure there are enough respondents in each demographic group.

## Action Items

* Test against localhost to make sure it will submit properly
* Add other static code analysis tasks to the Gradle build
* Update the package name: com.anthology.cei
* Update the docs to make it easy to checkout, build, and run
* Refactor so it no longer needs the REST Models project
* Add automated tests