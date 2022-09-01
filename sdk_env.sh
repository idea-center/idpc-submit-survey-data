#!/bin/bash
#
# sdk_env.sh
# by Todd Wallentine twallentine AT anthology com
#
# This script will setup the command line development environment
# for the project. To get started, source this script to set
# environment variables in your shell.
#
# source ./sdk_env.sh

JAVA_VERSION='11.0.14-zulu'
GROOVY_VERSION='4.0.4'
GRADLE_VERSION='7.5.1'

# Setup the Java home on Mac OS X
sdk use java ${JAVA_VERSION}
if [[ "$OSTYPE" == "darwin"* ]]; then
    export JAVA_HOME=`/usr/libexec/java_home -v 1.8`;
fi
echo "JAVA_HOME=${JAVA_HOME}"

# Setup the Groovy version using SDKMan
sdk use groovy ${GROOVY_VERSION}

# Setup the Gradle version using SDKMan
sdk use gradle ${GRADLE_VERSION}