#!/bin/sh
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Imports the accounts, roles and menus of a pre-rebuild (1.x) database into a tenant, as a dry run unless --apply is
# given. The server must be stopped: the import starts it once, imports, writes the report and exits. The old
# database's JDBC driver goes into drivers/. The password is read from GRANTFORGE_LEGACY_SOURCE_PASSWORD, or asked for.
#
#   bin/import-legacy.sh --source-url jdbc:mysql://old-db:3306/authx --source-user authx --tenant default [--apply]

# shellcheck source=script/bin/common.sh
. "$(dirname "$0")/common.sh"

usage() {
    printf "usage: %s --source-url URL --source-user USER --tenant CODE [--application CODE] [--report FILE] [--apply]\n" "$0" >&2
    exit 2
}

SOURCE_URL=
SOURCE_USER=
TENANT=
APPLICATION=legacy
REPORT="$GRANTFORGE_HOME/logs/legacy-import-report.json"
APPLY=false
while test $# -gt 0; do
    case "$1" in
        --source-url) test $# -ge 2 || usage; SOURCE_URL=$2; shift 2 ;;
        --source-user) test $# -ge 2 || usage; SOURCE_USER=$2; shift 2 ;;
        --tenant) test $# -ge 2 || usage; TENANT=$2; shift 2 ;;
        --application) test $# -ge 2 || usage; APPLICATION=$2; shift 2 ;;
        --report) test $# -ge 2 || usage; REPORT=$2; shift 2 ;;
        --apply) APPLY=true; shift ;;
        *) usage ;;
    esac
done
if test -z "$SOURCE_URL" || test -z "$TENANT"; then
    usage
fi

print_basics
if find_server; then
    printf "Server running, stop it first          | %s\n\n" "$APPLICATION_PID"
    exit 1
fi
if test -z "${GRANTFORGE_LEGACY_SOURCE_PASSWORD+set}" && test -t 0; then
    printf "Password of %s: " "${SOURCE_USER:-the old database}"
    stty -echo
    read -r GRANTFORGE_LEGACY_SOURCE_PASSWORD
    stty echo
    printf "\n"
fi
# Spring Boot reads the password from the environment, so it never shows in the process list.
export GRANTFORGE_LEGACY_SOURCE_PASSWORD

printf "Old database                           | %s\n" "${SOURCE_URL%%\?*}"
printf "Tenant                                 | %s\n" "$TENANT"
printf "Mode                                   | %s\n\n" "$(test "$APPLY" = true && echo apply || echo 'dry run')"
cd "$GRANTFORGE_HOME" || exit 1
mkdir -p "$GRANTFORGE_HOME/logs"
"$JAVA" -classpath "lib/*:drivers/*" "$APPLICATION_NAME" \
    --spring.config.additional-location="$GRANTFORGE_HOME/configure/" --server.port=0 \
    --grantforge.legacy.source-url="$SOURCE_URL" --grantforge.legacy.source-username="$SOURCE_USER" \
    --grantforge.legacy.tenant="$TENANT" --grantforge.legacy.application="$APPLICATION" \
    --grantforge.legacy.report="$REPORT" --grantforge.legacy.apply="$APPLY"
status=$?
if test "$status" -eq 0; then
    printf "\nImport report                          | %s\n\n" "$REPORT"
else
    printf "\nImport failed                          | %s\n\n" "$GRANTFORGE_HOME/logs/grantforge.log"
fi
exit "$status"
