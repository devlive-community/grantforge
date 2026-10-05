// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.legacy;

import org.devlive.grantforge.common.lang.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/**
 * Settings of an import of the old database ({@code grantforge.legacy.*}); the server imports at start-up and exits
 * when {@code source-url} is set, which {@code script/bin/import-legacy.sh} does.
 *
 * @param sourceUrl the JDBC URL of the old database
 * @param sourceUsername the account to read it with
 * @param sourcePassword its password, best given as {@code GRANTFORGE_LEGACY_SOURCE_PASSWORD}
 * @param tenant the code of the tenant to fill; required
 * @param application the code of the catalog application the menus go to; created if it does not exist
 * @param apply {@code true} to write; by default a dry run reports what would be written
 * @param report where to write the report as JSON
 */
@ConfigurationProperties("grantforge.legacy")
public record LegacyImportProperties(
        @Nullable String sourceUrl,
        @Nullable String sourceUsername,
        @Nullable String sourcePassword,
        @Nullable String tenant,
        @DefaultValue("legacy") String application,
        boolean apply,
        @DefaultValue("legacy-import-report.json") Path report)
{
    /** Treats blank values as absent. */
    public LegacyImportProperties
    {
        sourceUrl = Strings.blankToNull(sourceUrl);
        sourceUsername = Strings.blankToNull(sourceUsername);
        tenant = Strings.blankToNull(tenant);
    }
}
