// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.ManifestEntry;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsoleManifestLoaderTest
{
    private final ConsoleManifestLoader loader = new ConsoleManifestLoader(JsonMapper.builder().build());

    @Test
    void readsTheConsoleManifestFromTheClasspath()
    {
        List<ManifestEntry> entries = loader.load().flatten();

        assertThat(entries).extracting(ManifestEntry::code).contains("system", "system.user", "system.user.btn.edit", "platform.api");
        ManifestEntry users = entries.stream().filter(entry -> "system.user".equals(entry.code())).findFirst().orElseThrow();
        assertThat(users.type()).isEqualTo(ResourceType.PAGE);
        assertThat(users.route()).isEqualTo("/admin/users");
        assertThat(users.nameKey()).isEqualTo("titles.users");
        assertThat(users.apis()).contains("system.user.read");
    }

    @Test
    void reportsMissingAndBrokenManifests()
    {
        assertThatThrownBy(() -> loader.load("permissions/missing.json")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is missing");
        assertThatThrownBy(() -> loader.load("permissions/broken.json")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be read");
    }
}
