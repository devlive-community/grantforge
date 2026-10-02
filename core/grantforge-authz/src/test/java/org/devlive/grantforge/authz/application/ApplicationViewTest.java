// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.Application;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationViewTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresCodeAndName()
    {
        assertThat(new ApplicationView(1, "crm", "CRM", null, false, 2).resources()).isEqualTo(2);
        assertThatThrownBy(() -> new ApplicationView(1, null, "CRM", null, false, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ApplicationView(1, "crm", null, null, false, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ApplicationView.from(Application.create("crm", "CRM", null), 0))
                .as("not saved yet").isInstanceOf(IllegalStateException.class);
    }
}
