#!/bin/sh
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Shared by the scripts in bin/: where the installation is, and whether its server runs. The server of an installation
# is the process whose ID is in its grantforge.pid and whose command line names this installation, so another
# GrantForge on the same machine is never taken for it.

# The variables are read by the scripts that source this file.
# shellcheck disable=SC2034
GRANTFORGE_HOME=$(cd "$(dirname "$0")/.." && pwd)
APPLICATION_NAME='org.devlive.grantforge.server.GrantForge'
PID_FILE="$GRANTFORGE_HOME/grantforge.pid"
APPLICATION_PID=

if test -n "$JAVA_HOME"; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi

# Sets APPLICATION_PID when this installation's server runs; removes a pid file that names no such process.
find_server() {
    APPLICATION_PID=
    test -f "$PID_FILE" || return 1
    candidate=$(cat "$PID_FILE" 2>/dev/null)
    case "$candidate" in
        ''|*[!0-9]*) rm -f "$PID_FILE"; return 1 ;;
    esac
    if kill -0 "$candidate" 2>/dev/null && ps -p "$candidate" -o args= 2>/dev/null | grep -F "$APPLICATION_NAME" | grep -qF "$GRANTFORGE_HOME/"; then
        APPLICATION_PID=$candidate
        return 0
    fi
    rm -f "$PID_FILE"
    return 1
}

print_basics() {
    printf "\n\tGrantForge\n"
    printf "============================================\n"
    printf "Runtime home                           | %s\n" "$GRANTFORGE_HOME"
    printf "Runtime java                           | %s\n" "$JAVA"
    printf "============================================\n\n"
}
