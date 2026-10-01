// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ResourceType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleManifestTest
{
    static ManifestEntry entry(String code, ResourceType type, List<String> apis, List<String> requires, ManifestEntry... children)
    {
        return new ManifestEntry(code, type, code, null, null, apis, requires, List.of(children));
    }

    @Test
    @SuppressWarnings("NullAway") // JSON without the list gives null
    void flattensDepthFirst()
    {
        ConsoleManifest manifest = new ConsoleManifest(List.of(
                entry("system", ResourceType.MODULE, List.of(), List.of(),
                        entry("users", ResourceType.PAGE, List.of(), List.of(), entry("users.edit", ResourceType.ACTION, List.of(), List.of())),
                        entry("groups", ResourceType.PAGE, List.of(), List.of())),
                entry("platform", ResourceType.MODULE, List.of(), List.of())));

        assertThat(manifest.flatten()).extracting(ManifestEntry::code)
                .containsExactly("system", "users", "users.edit", "groups", "platform");
        assertThat(new ConsoleManifest(null).resources()).isEmpty();
    }
}
