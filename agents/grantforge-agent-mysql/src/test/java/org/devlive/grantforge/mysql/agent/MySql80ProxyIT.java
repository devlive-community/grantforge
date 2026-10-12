// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent;

/** The proxy in front of MySQL 8.0. */
class MySql80ProxyIT
        extends MySqlProxyContract
{
    @Override
    String image()
    {
        return "mysql:8.0";
    }
}
