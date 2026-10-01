// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.DependencySource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencyViewTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresKindAndSource()
    {
        assertThat(new DependencyView(1, 2, 3, DependencyKind.REQUIRED, DependencySource.MANUAL).dependsOnId()).isEqualTo(3);
        assertThatThrownBy(() -> new DependencyView(1, 2, 3, null, DependencySource.MANUAL)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DependencyView(1, 2, 3, DependencyKind.REQUIRED, null)).isInstanceOf(NullPointerException.class);
    }
}
