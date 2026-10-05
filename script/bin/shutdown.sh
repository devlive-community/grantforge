#!/bin/sh
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Stops the server: asks it to shut down (it finishes requests in progress) and stops it hard only after
# GRANTFORGE_STOP_TIMEOUT seconds (default 30).

# shellcheck source=script/bin/common.sh
. "$(dirname "$0")/common.sh"

print_basics
if ! find_server; then
    printf "Server status                          | %s\n\n" "stopped"
    exit 0
fi

printf "Server stopping                        | %s\n" "$APPLICATION_PID"
kill -TERM "$APPLICATION_PID"
waited=0
timeout=$GRANTFORGE_STOP_TIMEOUT
test -n "$timeout" || timeout=30
while kill -0 "$APPLICATION_PID" 2>/dev/null && test "$waited" -lt "$timeout"; do
    sleep 1
    waited=$((waited + 1))
done
if kill -0 "$APPLICATION_PID" 2>/dev/null; then
    printf "Server did not stop in time            | %s\n" "killing it"
    kill -KILL "$APPLICATION_PID"
fi
rm -f "$PID_FILE"
printf "Server stopped                         | %s\n\n" "$APPLICATION_PID"
