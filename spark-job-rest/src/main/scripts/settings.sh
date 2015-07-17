#!/usr/bin/env bash

CURRENT_DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
APP_DIR="$(dirname "$CURRENT_DIR")"
DEPLOY_CONFIG="${CURRENT_DIR}/deploy-settings.sh"

# Logging directory
LOG_DIR=${SJR_LOG_DIR-"${APP_DIR}/logs"}

# Extra classes:
EXTRA_CLASSPATH="${JSR_EXTRA_CLASSPATH}"

# Set proper jar path
JAR_PATH=${SJR_JAR_PATH-"${APP_DIR}/jars"}

# Set database root directory
DATABASE_ROOT_DIR=${SJR_DATABASE_ROOT_DIR-"${APP_DIR}/db"}

# Root location for contexts process directories
CONTEXTS_BASE_DIR=${SJR_CONTEXTS_BASE_DIR-"${APP_DIR}/contexts"}

# Set JAR names to avoid duplication
SJR_SERVER_JAR_NAME="spark-job-rest-server.jar"
SJR_SQL_JAR_NAME="spark-job-rest-sql.jar"

# Load optional deployment settings overrides
if [ -f "${DEPLOY_CONFIG}" ]; then
    source "${DEPLOY_CONFIG}"
fi

# File name for deployment configuration overrides
# Only eli names supported!
DEPLOY_CONF_FILE=${SJR_DEPLOY_CONF_FILE-deploy.conf}

##############################################################################
# All following variables will not be overrided by deployment settings!!!
##############################################################################

if [ -z "${SPARK_HOME}" ]; then
    SPARK_HOME="/opt/spark"
fi

if [ -z "${SPARK_CONF_HOME}" ]; then
    SPARK_CONF_HOME="${SPARK_HOME}/conf"
fi

# Pull in other env vars in spark config, such as MESOS_NATIVE_LIBRARY
if [ -f "${SPARK_CONF_HOME}/spark-env.sh" ]; then
    . "${SPARK_CONF_HOME}/spark-env.sh"
else
    echo "Warning! '${SPARK_CONF_HOME}/spark-env.sh' is not exist. Check SPARK_CONF_HOME or create the file."
fi

# Only needed for Mesos deploys
#SPARK_EXECUTOR_URI="${SPARK_HOME}/spark-1.1.0.tar.gz"

# For Mesos
CONFIG_OVERRIDES=""
if [ -n "${SPARK_EXECUTOR_URI}" ]; then
  CONFIG_OVERRIDES="-Dspark.executor.uri=${SPARK_EXECUTOR_URI} "
fi
# For Mesos/Marathon, use the passed-in port
if [ "$PORT" != "" ]; then
  CONFIG_OVERRIDES+="-Dspark.jobserver.port=${PORT} "
fi