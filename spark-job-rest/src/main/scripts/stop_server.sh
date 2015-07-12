#!/bin/bash

get_abs_script_path() {
  pushd . >/dev/null
  cd $(dirname $0)
  SCRIPTS_DIR=$(pwd)
  popd  >/dev/null
}
get_abs_script_path

APP_DIR="$(dirname "$0")"
PIDFILE="${APP_DIR}/server.pid"

if [ -f "${PIDFILE}" ]; then
    pid="$(cat "${PIDFILE}")"
    proc="$(ps axu | grep "$pid" | grep spark-job-rest-server.jar | awk '{print $2}')"
    if [ -n "$proc" ]; then
        echo "Killing pid $proc"
        kill -9 $proc
        rm -f "${PIDFILE}"
    else
        echo "Pid $pid does not exist or it's not for spark-job-rest."
    fi
else
echo "Pid file ${PIDFILE} was not found"
fi

