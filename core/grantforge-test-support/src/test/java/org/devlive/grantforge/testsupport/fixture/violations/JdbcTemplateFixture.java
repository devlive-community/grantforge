// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.testsupport.fixture.violations;

import org.springframework.jdbc.core.JdbcTemplate;

/** Violates NO_NATIVE_SQL through Spring JDBC. */
public class JdbcTemplateFixture
{
    private final JdbcTemplate jdbc;

    JdbcTemplateFixture(JdbcTemplate jdbc)
    {
        this.jdbc = jdbc;
    }

    JdbcTemplate jdbc()
    {
        return jdbc;
    }
}
