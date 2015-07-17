#!/bin/bash
# Script to start the job server
set -e

get_abs_script_path() {
  pushd . >/dev/null
  cd $(dirname $0)
  SCRIPTS_DIR=$(pwd)
  popd  >/dev/null
}
get_abs_script_path

APP_DIR="$(dirname "${SCRIPTS_DIR}")"
RESOURCE_DIR="${APP_DIR}/resources"

jarsForClasspath=$1
contextName=$2
contextId=$3
sparkMaster=$4
xmxMemory=$5
processDir=$6
masterHost=$7
masterPort=$8

echo "jarsForClasspath = ${jarsForClasspath}"
echo "contextName      = ${contextName}"
echo "contextId        = ${contextId}"
echo "sparkMaster      = ${sparkMaster}"
echo "xmxMemory        = ${xmxMemory}"
echo "processDir       = ${processDir}"
echo "masterHost       = ${masterHost}"
echo "masterPort       = ${masterPort}"


GC_OPTS="-XX:+UseConcMarkSweepGC
         -verbose:gc -XX:+PrintGCTimeStamps -Xloggc:${processDir}/gc.out
         -XX:MaxPermSize=512m
         -XX:+CMSClassUnloadingEnabled"

JAVA_OPTS="-Xmx$xmxMemory -XX:MaxDirectMemorySize=512M
           -XX:+HeapDumpOnOutOfMemoryError -Djava.net.preferIPv4Stack=true
           -Dcom.sun.management.jmxremote.authenticate=false
           -Dcom.sun.management.jmxremote.ssl=false"

MAIN="spark.job.rest.server.MainContext"

if [ -f "${SCRIPTS_DIR}/settings.sh" ]; then
  . "${SCRIPTS_DIR}/settings.sh"
else
  echo "Missing ${SCRIPTS_DIR}/settings.sh, exiting"
  exit 1
fi

# Set deployment config overrides file path
APP_CONF_FILE="${RESOURCE_DIR}/${DEPLOY_CONF_FILE}"

mkdir -p $LOG_DIR

# Log directory for Java options
JAVA_LOG_DIR="${LOG_DIR}"
# Logf4 properties file location (now supported only on the PWD)
LOG4J_PROPERTIES="${LOG4J_PROPERTIES-log4j.properties}"

# Override setting for `yarn-cluster` mode
if [ "${sparkMaster}" = "yarn-cluster" ]; then
    # Set log dirrectory to current\
    JAVA_LOG_DIR="."
    # Lookup for configuration overrides in current directory
    APP_CONF_FILE="${DEPLOY_CONF_FILE}"
fi

LOG_FILE="$contextName.log"
LOGGING_OPTS="-Dlog4j.configuration=${LOG4J_PROPERTIES}
              -DLOG_DIR=${JAVA_LOG_DIR}
              -DLOG_FILE=${LOG_FILE}"

# Need to explicitly include app dir in classpath so logging configs can be found
CLASSPATH="${APP_DIR}/spark-job-rest-server.jar:${APP_DIR}:${RESOURCE_DIR}:${jarsForClasspath}"

# Replace ":" with commas in classpath
JARS=`echo "${jarsForClasspath}" | sed -e 's/:/,/g'`

# Include extra classpath if not empty
if [ ! "${EXTRA_CLASSPATH}" = "" ]; then
    EXTRA_JARS=`echo "${EXTRA_CLASSPATH}" | sed -e 's/:/,/g'`
    JARS="${JARS},${EXTRA_JARS}"
fi

# Prepend with SQL extras if exists
SQL_EXTRAS="${APP_DIR}/${SJR_SQL_JAR_NAME}"
if [ -f "${SQL_EXTRAS}" ]; then
    CLASSPATH="${SQL_EXTRAS}:${CLASSPATH}"
    JARS="${SQL_EXTRAS},${JARS}"
fi

# Context application settings
PROGRAM_ARGUMENTS="${APP_CONF_FILE} ${contextName} ${contextId} ${masterHost} ${masterPort}"

# Files to submit
FILES="${RESOURCE_DIR}/deploy.conf,${APP_DIR}/${LOG4J_PROPERTIES}"

# Log classpath and jars
echo "CLASSPATH         = ${CLASSPATH}" >> "${LOG_DIR}/${LOG_FILE}"
echo "JARS              = ${JARS}" >> "${LOG_DIR}/${LOG_FILE}"
echo "PROGRAM_ARGUMENTS = ${PROGRAM_ARGUMENTS}" >> "${LOG_DIR}/${LOG_FILE}"
echo "FILES             = ${FILES}" >> "${LOG_DIR}/${LOG_FILE}"

# Create context process directory
mkdir -p "${processDir}"

cd "${processDir}"

# Start application using `spark-submit` which takes cake of computing classpaths
"${SPARK_HOME}/bin/spark-submit" \
  --verbose \
  --class $MAIN \
  --master "${sparkMaster}" \
  --driver-memory $xmxMemory \
  --conf "spark.executor.extraJavaOptions=${LOGGING_OPTS}" \
  --conf "spark.driver.extraClassPath=${CLASSPATH}" \
  --driver-java-options "${GC_OPTS} ${JAVA_OPTS} ${LOGGING_OPTS} ${CONFIG_OVERRIDES}" \
  --jars "${JARS}" \
  --files "${FILES}" \
  "${APP_DIR}/${SJR_SERVER_JAR_NAME}" ${PROGRAM_ARGUMENTS} \
  >> "${LOG_DIR}/${LOG_FILE}" 2>&1
