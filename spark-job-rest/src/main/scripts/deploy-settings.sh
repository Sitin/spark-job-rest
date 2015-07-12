#!/usr/bin/env bash

DEPLOY_EXTRA_CLASSPATH="/opt/cloudera/parcels/CDH/jars/hadoop-aws-2.6.0-cdh5.4.2.jar:/opt/cloudera/parcels/CDH/jars/guava-12.0.1.jar"

if [ "${EXTRA_CLASSPATH}" = "" ]; then
    EXTRA_CLASSPATH="${DEPLOY_EXTRA_CLASSPATH}"
else
    EXTRA_CLASSPATH="${EXTRA_CLASSPATH}:${DEPLOY_EXTRA_CLASSPATH}"
fi

JAVA_OPTS="${JAVA_OPTS}
           -Dspray.can.server.parsing.max-content-length=100m"