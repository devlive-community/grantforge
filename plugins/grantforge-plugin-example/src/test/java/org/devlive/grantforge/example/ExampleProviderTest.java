// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.example;

import org.devlive.grantforge.plugin.api.BrowseEntry;
import org.devlive.grantforge.plugin.api.BrowsePage;
import org.devlive.grantforge.plugin.api.BrowseRequest;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class ExampleProviderTest
{
    private final ExampleProvider provider = new ExampleProvider();

    private static ServiceConfig config(Map<String, String> values)
    {
        return new ServiceConfig("dw", values);
    }

    @Test
    void declaresEveryKindOfPolicy()
    {
        ServiceTypeDefinition definition = provider.definition();
        assertThat(definition.name()).isEqualTo("example");
        assertThat(definition.policyTypes()).containsExactlyInAnyOrder(PolicyType.ACCESS, PolicyType.DATA_MASK, PolicyType.ROW_FILTER);
        assertThat(definition.hierarchies()).containsExactly(List.of("database", "table", "column"), List.of("path"));
        assertThat(definition.configFields()).hasSize(3);
    }

    @Test
    void checksSettingsAndConnects()
    {
        assertThat(provider.validateConfig(config(Map.of("timeout", "30")))).isEmpty();
        assertThat(provider.validateConfig(config(Map.of()))).isEmpty();
        assertThat(provider.validateConfig(config(Map.of("timeout", "601")))).singleElement()
                .satisfies(problem -> assertThat(problem.field()).isEqualTo("timeout"));
        assertThat(provider.testConnection(config(Map.of("password", ExampleProvider.PASSWORD))).status())
                .isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(provider.testConnection(config(Map.of())).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void looksUpDatabasesAndTheirTables()
    {
        ServiceConfig config = config(Map.of());
        assertThat(provider.lookup(new LookupRequest(config, "database", "", Map.of(), 10))).containsExactly("hr", "ops", "sales");
        assertThat(provider.lookup(new LookupRequest(config, "database", "s", Map.of(), 10))).containsExactly("sales");
        assertThat(provider.lookup(new LookupRequest(config, "table", "", Map.of("database", List.of("sales", "nowhere")), 1)))
                .containsExactly("orders");
        assertThat(provider.lookup(new LookupRequest(config, "table", "", Map.of(), 10))).isEmpty();
        assertThat(provider.lookup(new LookupRequest(config, "path", "", Map.of(), 10))).isEmpty();
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(config, "database", "offline", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failure -> assertThat(failure.getReason())
                        .isEqualTo(LookupException.Reason.UNREACHABLE));
    }

    @Test
    void browsesAMadeUpFileSystemPageByPage()
    {
        ServiceConfig config = config(Map.of());
        BrowsePage root = provider.browse(new BrowseRequest(config, "path", "", null, 2));
        assertThat(root.entries()).extracting(BrowseEntry::value, BrowseEntry::directory)
                .containsExactly(tuple("/README.md", false), tuple("/landing", true));
        BrowsePage rest = provider.browse(new BrowseRequest(config, "path", "/", root.nextCursor(), 2));
        assertThat(rest.entries()).extracting(BrowseEntry::value).containsExactly("/warehouse");
        assertThat(rest.nextCursor()).isNull();
        assertThat(provider.browse(new BrowseRequest(config, "path", "/warehouse/sales", null, 10)).entries())
                .singleElement().satisfies(entry -> assertThat(entry.size()).isEqualTo(1024L));
        assertThatThrownBy(() -> provider.browse(new BrowseRequest(config, "path", "/nowhere", null, 10)))
                .isInstanceOfSatisfying(LookupException.class, failure -> assertThat(failure.getReason())
                        .isEqualTo(LookupException.Reason.NOT_FOUND));
    }

    @Test
    void shipsWithTheProductVersion() throws IOException
    {
        // A built-in plugin is released with GrantForge: the build writes the product version into its descriptor.
        String expected = System.getProperty("grantforge.project.version", "");
        assertThat(expected).as("grantforge.project.version from the build").isNotBlank();
        try (InputStream descriptor = requireNonNull(getClass().getResourceAsStream("/grantforge-plugin.yaml"), "descriptor")) {
            assertThat(new String(descriptor.readAllBytes(), StandardCharsets.UTF_8).lines().filter(line -> line.startsWith("version:")))
                    .containsExactly("version: " + expected);
        }
    }
}
