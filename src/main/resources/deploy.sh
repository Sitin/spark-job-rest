#!/usr/bin/env bash

set -e

CMD=$1
ARG1=$2

CDIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
PROJECT_DIR="${CDIR}/../../.."

# By default we deploy in local mode
SJR_IS_REMOTE_DEPLOY=${SJR_IS_REMOTE_DEPLOY-false}

SJR_PACKAGE_NAME=spark-job-rest-server.zip
SJR_EXTRAS_PACKAGE_NAME=spark-job-rest-sql.zip

#
# Following paths will be set to defaults if empty
#
SJR_PACKAGE_PATH="${SJR_PACKAGE_PATH}"
SJR_EXTRAS_PATH="${SJR_EXTRAS_PATH}"
SJR_DEPLOY_PATH="${SJR_DEPLOY_PATH}"

# Remote deploy varibles
SJR_REMOTE_DEPLOY_PATH="${SJR_REMOTE_DEPLOY_PATH}"   # Overrides SJR_DEPLOY_PATH in case of remote deploy
SJR_DEPLOY_KEY="${SJR_DEPLOY_KEY}"                   # Empty by default
SJR_DEPLOY_HOST="${SJR_DEPLOY_HOST}"                 # Empty for local deploy

CONFIGURATION_IS_SET="false"

function setup_defaults() {
    #
    # Default deployment path is "spark-job-rest" under current directory
    #
    if [ -z "${SJR_DEPLOY_PATH}" ]; then
        SJR_DEPLOY_PATH="${CDIR}/spark-job-rest"
    fi

    #
    # Default package path is a bundle under project directory.
    # If id doesn't exists then package looked up in the same directory as deploy script
    #
    DEFAULT_PACKAGE_PATH=${PROJECT_DIR}/spark-job-rest/target/scala-2.10/${SJR_PACKAGE_NAME}
    if [ ! -f "${DEFAULT_PACKAGE_PATH}" ]; then
        DEFAULT_PACKAGE_PATH="${CDIR}/${SJR_PACKAGE_NAME}"
    fi

    # Set empty package path to default
    if [ -z "${SJR_PACKAGE_PATH}" ]; then
        SJR_PACKAGE_PATH="${DEFAULT_PACKAGE_PATH}"
    fi

    # Absence of server package is an error
    if [ ! -f "${SJR_PACKAGE_PATH}" ]; then
        echo "Server package path is not accessible. Last tried: '${SJR_PACKAGE_PATH}'"
        exit -1
    fi

    #
    # The rules for extras package is the same as for server package: first project path, next current directory.
    #
    DEFAULT_EXTRAS_PACKAGE_PATH=${PROJECT_DIR}/spark-job-rest-sql/target/scala-2.10/${SJR_EXTRAS_PACKAGE_NAME}
    if [ ! -f "${DEFAULT_EXTRAS_PACKAGE_PATH}" ]; then
        DEFAULT_EXTRAS_PACKAGE_PATH="${CDIR}/${SJR_EXTRAS_PACKAGE_NAME}"
    fi

    # Set empty extras package path to default
    if [ -z "${SJR_EXTRAS_PATH}" ]; then
        SJR_EXTRAS_PATH="${DEFAULT_EXTRAS_PACKAGE_PATH}"
    fi

    # If extras package is set but not exists we must exit with error
    if [ ! "${SJR_EXTRAS_PATH}" = "" ]; then
        if [ ! -f "${SJR_EXTRAS_PATH}" ]; then
            echo "Extras package path is set but file is not exists: '${SJR_EXTRAS_PATH}'"
            exit -1
        fi
    fi
}

function setup_remote() {
    SSH_KEY_EXPRESSION=""
    if [ ! -z "${SJR_DEPLOY_KEY}" ]; then
        echo "Using SSH key from '${SJR_DEPLOY_KEY}'"
        SSH_KEY_EXPRESSION="-i ${SJR_DEPLOY_KEY}"
    fi

    if [ -z "${SJR_DEPLOY_HOST}" ]; then
        echo "Spark-Job-REST deployment host is not defined. Set 'SJR_DEPLOY_HOST' before running this script."
        exit -1
    fi

    # Override deploy path in remote mode
    if [ ! -z "${SJR_REMOTE_DEPLOY_PATH}" ]; then
        SJR_DEPLOY_PATH="${SJR_REMOTE_DEPLOY_PATH}"
    fi
}

function setup() {
    if [ "${CONFIGURATION_IS_SET}" = "false" ]; then
        CONFIGURATION_IS_SET="true"
        setup_defaults
        if [ "${SJR_IS_REMOTE_DEPLOY}" = "true" ]; then
            setup_remote
        else
            SJR_DEPLOY_HOST="localhost"
        fi
    fi
}

function exec_remote() {
    setup
    ssh -i "${SJR_DEPLOY_KEY}" "${SJR_DEPLOY_HOST}" "$1"
}

function exec_local() {
    setup
    eval "$1"
}

function exec_cmd() {
    if [ "$SJR_IS_REMOTE_DEPLOY" = "true" ]; then
        exec_remote "$1"
    else
        exec_local "$1"
    fi
}

function stop_server() {
    echo "Stopping server"
    exec_cmd "if [ -d ${SJR_DEPLOY_PATH}/bin/stop_server.sh ]; then ${SJR_DEPLOY_PATH}/bin/stop_server.sh; fi"
    exec_cmd "pkill -f 'java.*spark-job-rest-server.jar'" || true
}

function delete_server() {
    echo "Removing server"
    setup
    exec_cmd "rm -rf ${SJR_DEPLOY_PATH}"
}

function upload_files() {
    if [ "${SJR_IS_REMOTE_DEPLOY}" = "true" ]; then
        echo "Upload files"
        scp "${SSH_KEY_EXPRESSION}" "$SJR_PACKAGE_PATH" "${SJR_DEPLOY_HOST}":"/tmp/"
        scp "${SSH_KEY_EXPRESSION}" "$SJR_EXTRAS_PATH" "${SJR_DEPLOY_HOST}":"/tmp/"
    fi
}

function extract_command() {
    echo "unzip -o \"${1}\" -d \"${SJR_DEPLOY_PATH}\" && find \"${SJR_DEPLOY_PATH}\"/**/*.sh -exec chmod +x {} \;"
}

function extract_package() {
    echo "Extract from archives"
    exec_cmd "mkdir -p ${SJR_DEPLOY_PATH}"
    if [ "${SJR_IS_REMOTE_DEPLOY}" = "true" ]; then
        exec_remote "`extract_command /tmp/${SJR_PACKAGE_NAME}`"
        exec_remote "`extract_command /tmp/${SJR_EXTRAS_PACKAGE_NAME}`"
    else
        exec_local "`extract_command ${SJR_PACKAGE_PATH}`"
        exec_local "`extract_command ${SJR_EXTRAS_PATH}`"
    fi
}

function remove_server() {
    echo "Removing instance at ${SJR_DEPLOY_HOST}:${SJR_DEPLOY_PATH}"
    stop_server
    delete_server
}

function install_server() {
    echo "Deploing to ${SJR_DEPLOY_HOST}:${SJR_DEPLOY_PATH}"
    remove_server
    upload_files
    extract_package
}

function start_server() {
    echo "Run server"
    exec_cmd "${SJR_DEPLOY_PATH}/bin/start_server.sh"
}

function server_log() {
    echo "Spark-Job-REST main log:"
    exec_cmd "tail -f ${SJR_DEPLOY_PATH}/logs/spark-job-rest.log"
}

function server_log_context() {
    CONTEXT_NAME=$ARG1
    echo "Spark-Job-REST '${CONTEXT_NAME}' log:"
    exec_cmd "tail -f ${SJR_DEPLOY_PATH}/logs/${CONTEXT_NAME}.log"
}

function show_help() {
    echo "Spark-Job-REST deployment tool"
    echo "Usage: deploy.sh [deploy|install|start|stop|restart|log|log-context <context>|debug|debug-resolved]"
}

function show_vars() {
    echo "SJR_DEPLOY_PATH        = ${SJR_DEPLOY_PATH}"
    echo "SJR_DEPLOY_HOST        = ${SJR_DEPLOY_HOST}"
    echo "SJR_DEPLOY_KEY         = ${SJR_DEPLOY_KEY}"
    echo "SJR_PACKAGE_PATH       = ${SJR_PACKAGE_PATH}"
    echo "SJR_EXTRAS_PATH        = ${SJR_EXTRAS_PATH}"
    echo "SJR_IS_REMOTE_DEPLOY   = ${SJR_IS_REMOTE_DEPLOY}"
    echo "SJR_REMOTE_DEPLOY_PATH = ${SJR_REMOTE_DEPLOY_PATH}"
}

function main() {
    case "$CMD" in
    deploy) setup
        install_server
        start_server
        ;;
    install) setup
        install_server
        ;;
    remove) setup
        remove_server
        ;;
    stop) setup
        stop_server
        ;;
    start) setup
        start_server
        ;;
    restart) setup
        stop_server
        start_server
        ;;
    log) setup
        server_log
        ;;
    log-context) setup
        server_log_context
        ;;
    debug) show_vars
        ;;
    debug-resolved) setup
        show_vars
        ;;
    help) show_help
        ;;
    *) show_help
        ;;
    esac
}

main