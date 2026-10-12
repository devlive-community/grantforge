// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefusalTest
{
    @Test
    void reportsMySqlStatesThatClientsUseForPermissionAndSyntaxErrors()
    {
        for (int code : List.of(Refusal.DATABASE_DENIED, Refusal.TABLE_DENIED, Refusal.COLUMN_DENIED, Refusal.NOT_ALLOWED,
                Refusal.CANNOT_PARSE)) {
            Refusal refusal = new Refusal(code, "statement refused");
            assertThat(refusal.code()).isEqualTo(code);
            assertThat(refusal.state()).isEqualTo("42000");
        }
        assertThat(new Refusal(Refusal.NO_DATABASE, "select a database").state()).isEqualTo("3D000");
        assertThat(new Refusal(9999, "internal refusal").state()).isEqualTo("HY000");
    }

    @Test
    void preservesTheFailureForLogsWithoutPuttingItsSecretsInTheClientMessage()
    {
        IllegalStateException cause = new IllegalStateException("jdbc:mysql://private-host/?password=secret");
        Refusal refusal = new Refusal(Refusal.NOT_ALLOWED, "GrantForge cannot check the statement", cause);

        assertThat(refusal.getCause()).isSameAs(cause);
        assertThat(refusal.getMessage()).isEqualTo("GrantForge cannot check the statement").doesNotContain("private-host", "secret");
        assertThat(new Refusal(Refusal.TABLE_DENIED, "table denied").getCause()).isNull();
    }
}
