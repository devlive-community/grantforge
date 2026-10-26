// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql;

/** The PostgreSQL plugin against PostgreSQL 15.19. */
class PostgreSql15PluginIT
        extends PostgreSqlPluginContract
{
    @Override
    String version()
    {
        return "15.19";
    }
}
