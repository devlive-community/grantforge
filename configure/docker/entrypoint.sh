#!/bin/bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

set -eu

# 启动 MySQL 服务
service mysql start
for attempt in $(seq 1 60); do
    if mysqladmin --protocol=socket ping > /dev/null 2>&1; then
        break
    fi
    sleep 1
done

# Existing volumes may use password authentication for the root account.
if ! mysql --protocol=socket --user=root --execute='SELECT 1' > /dev/null 2>&1; then
    export MYSQL_PWD=${GRANTFORGE_DB_ADMIN_PASSWORD:-12345678}
fi
export GRANTFORGE_DB_NAME=${GRANTFORGE_DB_NAME:-grantforge}
if [[ ! "$GRANTFORGE_DB_NAME" =~ ^[a-zA-Z0-9_]+$ ]]; then
    echo "GRANTFORGE_DB_NAME must contain only letters, numbers and underscores" >&2
    exit 1
fi
mysql --protocol=socket --user=root <<SQL
CREATE DATABASE IF NOT EXISTS \`${GRANTFORGE_DB_NAME}\` CHARACTER SET utf8mb4;
SQL

export GRANTFORGE_DB_USER=${GRANTFORGE_DB_USER:-grantforge}
export GRANTFORGE_DB_PASSWORD=${GRANTFORGE_DB_PASSWORD:-12345678}
if [[ ! "$GRANTFORGE_DB_USER" =~ ^[a-zA-Z0-9_]+$ ]]; then
    echo "GRANTFORGE_DB_USER must contain only letters, numbers and underscores" >&2
    exit 1
fi
password_hex=$(printf '%s' "$GRANTFORGE_DB_PASSWORD" | od -An -v -tx1 | tr -d ' \n')
mysql --protocol=socket --user=root <<SQL
SET @grantforge_password = CONVERT(0x${password_hex} USING utf8mb4);
SET @grantforge_create = CONCAT('CREATE USER IF NOT EXISTS ''${GRANTFORGE_DB_USER}''@''localhost'' IDENTIFIED BY ', QUOTE(@grantforge_password));
PREPARE grantforge_user_statement FROM @grantforge_create;
EXECUTE grantforge_user_statement;
DEALLOCATE PREPARE grantforge_user_statement;
GRANT ALL PRIVILEGES ON \`${GRANTFORGE_DB_NAME}\`.* TO '${GRANTFORGE_DB_USER}'@'localhost';
SET @grantforge_create = CONCAT('CREATE USER IF NOT EXISTS ''${GRANTFORGE_DB_USER}''@''127.0.0.1'' IDENTIFIED BY ', QUOTE(@grantforge_password));
PREPARE grantforge_user_statement FROM @grantforge_create;
EXECUTE grantforge_user_statement;
DEALLOCATE PREPARE grantforge_user_statement;
GRANT ALL PRIVILEGES ON \`${GRANTFORGE_DB_NAME}\`.* TO '${GRANTFORGE_DB_USER}'@'127.0.0.1';
SQL

unset MYSQL_PWD
service nginx start

# Flyway runs versioned migrations before JPA and the API start.
exec sh ./bin/debug.sh
