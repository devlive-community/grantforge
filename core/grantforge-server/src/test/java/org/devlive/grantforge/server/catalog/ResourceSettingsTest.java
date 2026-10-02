// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceSettingsTest
{
    @Test
    void omittedFlagsMeanVisibleEnabledAndHidden()
    {
        assertThat(ResourceSettings.of("Page", null, null, null, null, null))
                .isEqualTo(new ResourceDetails("Page", null, null, true, true, DenyMode.HIDE));
        assertThat(ResourceSettings.of("Page", "d", "/p", false, true, DenyMode.DISABLE))
                .isEqualTo(new ResourceDetails("Page", "d", "/p", false, true, DenyMode.DISABLE));
    }
}
