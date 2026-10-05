#!/bin/sh
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Starts the server in the background; its log is logs/grantforge.log, and logs/console.out keeps what happens before
# logging starts. Run from anywhere: the installation is the folder above this script.

# shellcheck source=script/bin/common.sh
. "$(dirname "$0")/common.sh"

print_basics
if find_server; then
    printf "Server already running                 | %s\n\n" "$APPLICATION_PID"
    exit 0
fi

cd "$GRANTFORGE_HOME" || exit 1
mkdir -p "$GRANTFORGE_HOME/logs"
nohup "$JAVA" -classpath "lib/*:drivers/*" "$APPLICATION_NAME" \
    --spring.config.additional-location="$GRANTFORGE_HOME/configure/" > "$GRANTFORGE_HOME/logs/console.out" 2>&1 &
echo $! > "$PID_FILE"
sleep 5
if find_server; then
    printf "Server started                         | %s\n" "$APPLICATION_PID"
    printf "Server log                             | %s\n" "$GRANTFORGE_HOME/logs/grantforge.log"
    printf "First start                            | %s\n\n" "the setup token is printed in the server log"
else
    printf "Server start failed                    | %s\n\n" "$GRANTFORGE_HOME/logs/console.out"
    exit 1
fi
