// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql;

/** The MySQL plugin against MySQL 8.0. */
class MySql80PluginIT
        extends MySqlPluginContract
{
    @Override
    String image()
    {
        return "mysql:8.0";
    }
}
