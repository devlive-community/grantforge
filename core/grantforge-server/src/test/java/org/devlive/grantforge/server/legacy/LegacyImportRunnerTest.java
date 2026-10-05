// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.legacy;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LegacyImportRunnerTest
{
    private static final String SOURCE = "jdbc:h2:mem:legacy-runner;MODE=MySQL;DB_CLOSE_DELAY=-1";

    @TempDir
    Path folder;

    private final LegacyImporter importer = mock(LegacyImporter.class);

    @BeforeAll
    static void loadSource() throws SQLException
    {
        try (Connection connection = DriverManager.getConnection(SOURCE); Statement statement = connection.createStatement()) {
            statement.execute("RUNSCRIPT FROM 'classpath:/legacy/authx.sql' CHARSET 'UTF-8'");
        }
    }

    private static LegacyImportProperties properties(String url, String tenant, boolean apply, Path report)
    {
        return new LegacyImportProperties(url, null, null, tenant, "legacy", apply, report);
    }

    @Test
    void importsWritesTheReportAndExits() throws IOException
    {
        LegacyReport report = new LegacyReport(true, "jdbc:h2:mem:legacy-runner", "acme", "legacy");
        when(importer.run(any(), eq("acme"), eq("legacy"), eq(true), eq("jdbc:h2:mem:legacy-runner"))).thenReturn(report);
        Path file = folder.resolve("reports/report.json");
        List<Integer> codes = new ArrayList<>();

        new LegacyImportRunner(properties(SOURCE, "acme", true, file), importer, codes::add).run(new DefaultApplicationArguments());

        assertThat(codes).containsExactly(0);
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(report.json());
        verify(importer).run(any(), anyString(), anyString(), anyBoolean(), anyString());
    }

    @Test
    void failsWithoutATenant()
    {
        LegacyImportRunner runner = new LegacyImportRunner(new LegacyImportProperties(SOURCE, null, null, null, "legacy", false,
                folder.resolve("report.json")), importer, code -> { });

        assertThat(runner.importOnce()).isEqualTo(1);
        verifyNoInteractions(importer);
    }

    @Test
    void failsWhenTheSourceIsNotAnOldDatabase()
    {
        LegacyImportRunner runner = new LegacyImportRunner(properties("jdbc:h2:mem:legacy-empty", "acme", false, folder.resolve("report.json")),
                importer, code -> { });

        assertThat(runner.importOnce()).isEqualTo(1);
        verifyNoInteractions(importer);
    }

    @Test
    void failsWhenTheReportCannotBeWritten() throws IOException
    {
        when(importer.run(any(), anyString(), anyString(), anyBoolean(), anyString())).thenReturn(new LegacyReport(false, "x", "acme", "legacy"));
        Path blocked = Files.writeString(folder.resolve("blocked"), "a file, not a folder");
        LegacyImportRunner runner = new LegacyImportRunner(properties(SOURCE, "acme", false, blocked.resolve("report.json")), importer,
                code -> { });

        assertThat(runner.importOnce()).isEqualTo(1);
    }
}
