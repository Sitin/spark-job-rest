#!/bin/bash
# Script to start the job server
# Extra arguments will be spark-submit options, for example
#  ./server_start.sh --jars cassandra-spark-connector.jar
set -e

get_abs_script_path() {
  pushd . >/dev/null
  cd $(dirname $0)
  SCRIPTS_DIR=$(pwd)
  popd  >/dev/null
}
get_abs_script_path

APP_DIR="$(dirname "${SCRIPTS_DIR}")"
PIDFILE="${APP_DIR}/server.pid"
RESOURCE_DIR="${APP_DIR}/resources"

# From this variable depends whether server will be started in detached on in-process mode
SJR_RUN_DETACHED="${SJR_RUN_DETACHED-true}"

DRIVER_MEMORY=1g

GC_OPTS="-XX:+UseConcMarkSweepGC
         -verbose:gc -XX:+PrintGCTimeStamps -Xloggc:${APP_DIR}/gc.out
         -XX:MaxPermSize=512m
         -XX:+CMSClassUnloadingEnabled "

JAVA_OPTS="-Xmx1g -XX:MaxDirectMemorySize=512M
           -XX:+HeapDumpOnOutOfMemoryError -Djava.net.preferIPv4Stack=true
           -Dcom.sun.management.jmxremote.authenticate=false
           -Dcom.sun.management.jmxremote.ssl=false"

MAIN="spark.job.rest.server.Main"

if [ -f "${SCRIPTS_DIR}/settings.sh" ]; then
  . "${SCRIPTS_DIR}/settings.sh"
else
  echo "Missing ${SCRIPTS_DIR}/settings.sh, exiting"
  exit 1
fi

# Set deployment config overrides file path
APP_CONF_FILE="${RESOURCE_DIR}/${DEPLOY_CONF_FILE}"

# Logf4 properties file location (now supported only on the PWD)
LOG4J_PROPERTIES="${LOG4J_PROPERTIES-log4j.properties}"

# Create directories if not exist
mkdir -p "${LOG_DIR}"
mkdir -p "${JAR_PATH}"
mkdir -p "${DATABASE_ROOT_DIR}"

LOG_FILE="spark-job-rest.log"
LOGGING_OPTS="-Dlog4j.configuration=${LOG4J_PROPERTIES}
              -DLOG_DIR=${LOG_DIR}
              -DLOG_FILE=${LOG_FILE}"

# Need to explicitly include app dir in classpath so logging configs can be found
CLASSPATH="${APP_DIR}/${SJR_SERVER_JAR_NAME}:${APP_DIR}:${RESOURCE_DIR}"

# Log classpath
echo "CLASSPATH = ${CLASSPATH}" >> "${LOG_DIR}/${LOG_FILE}"

# The following should be exported in order to be accessible in Config substitutions
export SPARK_HOME
export APP_DIR
export JAR_PATH
export CONTEXTS_BASE_DIR
export DATABASE_ROOT_DIR
export CONTEXT_START_SCRIPT="${SCRIPTS_DIR}/context_start.sh"

function start_server() {
    # Start application using `spark-submit` which takes cake of computing classpaths
    "${SPARK_HOME}/bin/spark-submit" \
      --class $MAIN \
      --driver-memory $DRIVER_MEMORY \
      --conf "spark.executor.extraJavaOptions=${LOGGING_OPTS}" \
      --conf "spark.driver.extraClassPath=${CLASSPATH}" \
      --driver-java-options "${GC_OPTS} ${JAVA_OPTS} ${LOGGING_OPTS} ${CONFIG_OVERRIDES}" \
      $@ "${APP_DIR}/${SJR_SERVER_JAR_NAME}" "${APP_CONF_FILE}" \
      >> "${LOG_DIR}/${LOG_FILE}" 2>&1
}

if [ "${SJR_RUN_DETACHED}" = "true" ]; then
    start_server &
    echo $! > "${PIDFILE}"
    echo "Server started in detached mode. PID = `cat "${PIDFILE}"`"
elif [ "${SJR_RUN_DETACHED}" = "false" ]; then
    start_server
else
    echo "Wrong value for SJR_RUN_DETACHED = ${SJR_RUN_DETACHED}."
    exit -1
fi