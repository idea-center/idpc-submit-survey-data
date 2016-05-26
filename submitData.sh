#!/bin/bash
#
# submitData.sh
# by Todd Wallentine todd AT IDEAedu org
#
# This script will submit a bunch of test data to the specified IDEA Data Portal. This is
# useful in testing but should not be used in production.
#
# To use this script, you need to set the system properties (the home directory of the
# IDPC Submit Survey Data utility, the institution IDs to use when submitting data,
# the types of surveys to submit, and the starting IDs for surveys and groups). Once that
# is set, you can use command line arguments to define the application name and key, the host
# and port where the Data Portal is located, and the number of surveys to create.
#
# Example:
# ./submitData.sh IOL3 IOL3Key resthome.ideasystem.org 80 4


# ***********************************************************************************************
# The following variables are taken from the command line. This includes the Data Portal application
# name and key, the Data Portal host and port, and finally the number of surveys to submit for
# each type of survey.
APP_NAME='TestClient'
APP_KEY='TestClientAppKey'
HOST=localhost
PORT=8091
SURVEY_COUNT=1

if [ -n "$1" ]
then
    APP_NAME=$1
fi

if [ -n "$2" ]
then
    APP_KEY=$2
fi

if [ -n "$3" ]
then
    HOST=$3
fi

if [ -n "$4" ]
then
    PORT=$4
fi

if [ -n "$5" ]
then
    SURVEY_COUNT=$5
fi

# ***********************************************************************************************
# The following variables need to be configured to match the underlying systems. This includes
# where the IDEA Data Portal CLI for Submitting Survey Data is installed, the institution IDs
# that are available in the Data Portal, and the survey types to generate data for.
IDPC_HOME=build/install/idpc-submit-survey-data/bin
INSTITUTIONS=(1029)
#SURVEY_TYPES=('teaching', 'diag', 'short', 'chair', 'admin')
SURVEY_TYPES=('teaching')
START_SURVEY_ID=0
START_SURVEY_GROUP_ID=0

# ***********************************************************************************************
# Loop through the institutions, survey types, and survey count to generate sample data.
SURVEY_ID=$START_SURVEY_ID
SURVEY_GROUP_ID=$START_SURVEY_GROUP_ID
for INST_ID in ${INSTITUTIONS[@]}; do
    for SURVEY_TYPE in ${SURVEY_TYPES[@]}; do
        SURVEY_GROUP_ID=$((SURVEY_GROUP_ID + 1))
        for i in `seq 1 $SURVEY_COUNT`; do
            SURVEY_ID=$((SURVEY_ID + 1))
            $IDPC_HOME/idpc-submit-survey-data -iid $INST_ID -a '$APP_NAME' -k '$APP_KEY' -t '$SURVEY_TYPE' -h $HOST -p $PORT -sid $SURVEY_ID -sgid $SURVEY_GROUP_ID -v
        done
    done
done