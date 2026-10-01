// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.transfer;

import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.application.IdentityErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvFilesTest
{
    @Test
    void readsUtf8AndGbkFiles()
    {
        assertThat(CsvFiles.read("a,名\n".getBytes(StandardCharsets.UTF_8))).containsExactly(List.of("a", "名"));
        assertThat(CsvFiles.read("a,名\n".getBytes(Charset.forName("GBK")))).containsExactly(List.of("a", "名"));
        assertThatThrownBy(() -> CsvFiles.read(new byte[0])).isInstanceOfSatisfying(GrantForgeException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.IMPORT_EMPTY));
        assertThatThrownBy(() -> CsvFiles.read("\"x".getBytes(StandardCharsets.UTF_8))).isInstanceOfSatisfying(
                GrantForgeException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(IdentityErrorCode.IMPORT_MALFORMED);
                    assertThat(error.getArguments()).containsExactly(1);
                });
    }

    @Test
    void namesDownloadsAfterTheirContentAndDate()
    {
        var response = CsvFiles.download("users", List.of(List.of("a")),
                Clock.fixed(Instant.parse("2026-10-01T08:00:00Z"), ZoneOffset.UTC));

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"users-2026-10-01.csv\"");
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("﻿a\r\n");
    }
}
