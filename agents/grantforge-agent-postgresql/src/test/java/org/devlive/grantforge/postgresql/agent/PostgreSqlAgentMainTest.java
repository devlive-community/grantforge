// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostgreSqlAgentMainTest
{
    @Test
    void readsTheFilteredVersionShippedWithTheAgent()
    {
        assertThat(PostgreSqlAgentMain.version()).matches("\\d+\\.\\d+\\.\\d+(?:[-.][A-Za-z0-9]+)*");
    }
}
