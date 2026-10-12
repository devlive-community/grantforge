// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

/**
 * What MySQL statements need and how the proxy changes them: {@link org.devlive.grantforge.mysql.agent.sql.SqlAnalyzer}
 * finds the accesses, {@link org.devlive.grantforge.mysql.agent.sql.QueryGuard} decides them and applies masks and row
 * filters.
 */
@NullMarked
package org.devlive.grantforge.mysql.agent.sql;

import org.jspecify.annotations.NullMarked;
