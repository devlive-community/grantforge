// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClickHouseAgentMainTest
{
    @TempDir
    Path work;

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void loadsUtf8SettingsAndOnlyOverridesGrantForgeKeys() throws IOException
    {
        Path settings = Files.writeString(work.resolve("agent.properties"), "grantforge.instance=代理一\n"
                + "grantforge.sync.interval.ms=10000\nclickhouse.test.option=from-file\n", StandardCharsets.UTF_8);
        String port = System.getProperty("grantforge.sync.interval.ms");
        String instance = System.getProperty("grantforge.instance");
        String other = System.getProperty("clickhouse.test.option");
        try {
            System.clearProperty("grantforge.instance");
            System.setProperty("grantforge.sync.interval.ms", "20000");
            System.setProperty("clickhouse.test.option", "from-system");
            Map<String, String> values = ClickHouseAgentMain.settings(settings.toString());
            assertThat(values).containsEntry("grantforge.instance", "代理一")
                    .containsEntry("grantforge.sync.interval.ms", "20000").containsEntry("clickhouse.test.option", "from-file");
        }
        finally {
            restore("grantforge.sync.interval.ms", port);
            restore("grantforge.instance", instance);
            restore("clickhouse.test.option", other);
        }
    }

    @Test
    void reportsAnUnreadableConfigurationInsteadOfUsingDefaults()
    {
        assertThatThrownBy(() -> ClickHouseAgentMain.settings(work.resolve("missing.properties").toString()))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void readsTheFilteredVersionShippedWithTheAgent()
    {
        assertThat(ClickHouseAgentMain.version()).matches("\\d+\\.\\d+\\.\\d+(?:[-.][A-Za-z0-9]+)*");
    }

    private static void restore(String key, @Nullable String value)
    {
        if (value == null) {
            System.clearProperty(key);
        }
        else {
            System.setProperty(key, value);
        }
    }
}
