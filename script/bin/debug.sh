#!/bin/sh
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Runs the server in the foreground, logging to the console as well, until it is stopped with Ctrl+C.

# shellcheck source=script/bin/common.sh
. "$(dirname "$0")/common.sh"

print_basics
if find_server; then
    printf "Server already running                 | %s\n\n" "$APPLICATION_PID"
    exit 1
fi

cd "$GRANTFORGE_HOME" || exit 1
exec "$JAVA" -classpath "lib/*:drivers/*" "$APPLICATION_NAME" \
    --spring.config.additional-location="$GRANTFORGE_HOME/configure/"
