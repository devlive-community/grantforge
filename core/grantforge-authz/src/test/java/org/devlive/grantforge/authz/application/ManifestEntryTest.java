// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ResourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManifestEntryTest
{
    @Test
    @SuppressWarnings("NullAway") // JSON without the lists gives null, and broken JSON can omit anything
    void omittedListsBecomeEmptyAndRequiredValuesAreChecked()
    {
        ManifestEntry entry = new ManifestEntry("system", ResourceType.MODULE, "System", null, null, null, null, null);

        assertThat(entry.apis()).isEmpty();
        assertThat(entry.requires()).isEmpty();
        assertThat(entry.children()).isEmpty();
        assertThatThrownBy(() -> new ManifestEntry(null, ResourceType.MODULE, "S", null, null, null, null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ManifestEntry("system", null, "S", null, null, null, null, null))
                .hasMessage("type of system");
        assertThatThrownBy(() -> new ManifestEntry("system", ResourceType.MODULE, null, null, null, null, null, null))
                .hasMessage("name of system");
    }
}
