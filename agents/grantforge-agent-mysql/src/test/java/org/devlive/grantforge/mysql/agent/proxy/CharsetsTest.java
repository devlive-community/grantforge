// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.proxy;

import org.apache.shardingsphere.database.exception.mysql.exception.UnknownCollationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class CharsetsTest
{
    @Test
    void readsCommonHandshakeCollations()
    {
        assertThat(Charsets.byCollation(45)).isEqualTo(StandardCharsets.UTF_8);
        assertThat(Charsets.byCollation(33)).isEqualTo(StandardCharsets.UTF_8);
        assertThat(Charsets.byCollation(8)).isEqualTo(Charsets.byName("latin1"));
    }

    @Test
    void resolvesSetNamesAliasesWithoutCaseSensitivity()
    {
        assertThat(Charsets.byName("UTF8MB4")).isEqualTo(StandardCharsets.UTF_8);
        assertThat(Charsets.byName("utf8")).isEqualTo(StandardCharsets.UTF_8);
        assertThat(Charsets.byName("utf8mb3")).isEqualTo(StandardCharsets.UTF_8);
        assertThat(Charsets.byName("ascii")).isEqualTo(StandardCharsets.US_ASCII);
    }

    @Test
    void unknownCharsetsCannotSilentlyChangeHowSqlIsRead()
    {
        assertThat(Charsets.byName("utf8mb4_unknown_suffix")).isNull();
        assertThat(Charsets.byName("not-a-mysql-character-set")).isNull();
        assertThatExceptionOfType(UnknownCollationException.class).isThrownBy(() -> Charsets.byCollation(65535));
    }
}
