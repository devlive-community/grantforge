// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.legacy;

import org.devlive.grantforge.server.legacy.persistence.dialect.LegacyDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.IntConsumer;

import static java.util.Objects.requireNonNull;

/**
 * Imports the old database once the server has started, writes the report, and ends the process: with 0 when the import
 * (or dry run) went through, 1 when it failed, which writes nothing, or its report could not be saved. The old
 * database's JDBC driver is found in {@code drivers/}, as the server's own are.
 */
public final class LegacyImportRunner
        implements ApplicationRunner
{
    private static final Logger LOG = LoggerFactory.getLogger(LegacyImportRunner.class);

    private final LegacyImportProperties properties;
    private final LegacyImporter importer;
    private final IntConsumer exit;

    /**
     * Creates the runner.
     *
     * @param properties the settings
     * @param importer the importer
     * @param exit ends the process with an exit code
     */
    public LegacyImportRunner(LegacyImportProperties properties, LegacyImporter importer, IntConsumer exit)
    {
        this.properties = requireNonNull(properties, "properties");
        this.importer = requireNonNull(importer, "importer");
        this.exit = requireNonNull(exit, "exit");
    }

    @Override
    public void run(ApplicationArguments args)
    {
        exit.accept(importOnce());
    }

    /**
     * Imports, or reports what importing would do.
     *
     * @return the exit code
     */
    int importOnce()
    {
        String url = properties.sourceUrl();
        String tenant = properties.tenant();
        if (url == null || tenant == null) {
            LOG.error("Set grantforge.legacy.source-url and grantforge.legacy.tenant to import the old database");
            return 1;
        }
        String source = LegacyDatabase.describe(url);
        LegacyReport report;
        try {
            LegacyData data;
            try (Connection connection = DriverManager.getConnection(url, properties.sourceUsername(), properties.sourcePassword())) {
                connection.setReadOnly(true);
                data = LegacyDatabase.read(connection);
            }
            report = importer.run(data, tenant, properties.application(), properties.apply(), source);
        }
        catch (SQLException | RuntimeException failure) {
            LOG.error("Importing {} failed; nothing was written", source, failure);
            return 1;
        }
        LOG.info("{}", report.summary());
        if (!properties.apply()) {
            LOG.info("Nothing was written; run again with --apply to import");
        }
        Path file = properties.report().toAbsolutePath();
        try {
            Path folder = file.getParent();
            if (folder != null) {
                Files.createDirectories(folder);
            }
            Files.writeString(file, report.json(), StandardCharsets.UTF_8);
        }
        catch (IOException failure) {
            LOG.error("The report could not be written to {}", file, failure);
            return 1;
        }
        LOG.info("Report: {}", file);
        return 0;
    }
}
