#!/bin/sh
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

GRANTFORGE_HOME=$(pwd)
JAVA_HOME=${JAVA_HOME:-/opt/jdk}
APPLICATION_NAME='org.devlive.grantforge.server.GrantForge'
APPLICATION_PID=

job_before_echo_basic() {
    printf "\n\tJob before echo basic \n"
    printf "============================================\n"
    printf "Runtime home                           | %s\n" "$GRANTFORGE_HOME"
    printf "Runtime java home                      | %s\n" "$JAVA_HOME"
    printf "Runtime application name               | %s\n" "$APPLICATION_NAME"
    printf "============================================\n\n"
}

job_before_apply_server() {
    APPLICATION_PID=$(pgrep -f "$APPLICATION_NAME" | awk '{print $1}')
}

job_runner_checker_server() {
    printf "\n\tJob runner check server \n"
    printf "============================================\n"
    job_before_apply_server
    printf "Runtime process                        | %s\n" "$APPLICATION_PID"
    if test -z "$APPLICATION_PID"; then
        printf "Server status                          | %s\n" "stopped"
        printf "============================================\n\n"
    else
        printf "Server status                          | %s\n" "running"
        printf "============================================\n\n"
        exit
    fi
}

job_runner_debug_server() {
    printf "\n\tJob runner server \n"
    printf "============================================\n"
    printf "Server starting                        | %s\n" "$APPLICATION_NAME"
    cd "$GRANTFORGE_HOME" || exit 1
    "$JAVA_HOME"/bin/java -classpath "lib/*" "$APPLICATION_NAME" \
        --spring.config.location="$GRANTFORGE_HOME/configure/"
}

job_before_echo_basic
# shellcheck disable=SC2119
job_runner_checker_server
job_runner_debug_server
