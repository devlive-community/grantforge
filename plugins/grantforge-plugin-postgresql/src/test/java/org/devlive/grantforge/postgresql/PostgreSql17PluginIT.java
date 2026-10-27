// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql;

/** The PostgreSQL plugin against PostgreSQL 17.11. */
class PostgreSql17PluginIT
        extends PostgreSqlPluginContract
{
    @Override
    String version()
    {
        return "17.11";
    }
}
