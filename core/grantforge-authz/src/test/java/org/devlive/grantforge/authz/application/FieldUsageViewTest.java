// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.FieldDirection;
import org.devlive.grantforge.authz.domain.FieldUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FieldUsageViewTest
{
    @Test
    void copiesAUsage()
    {
        FieldUsage usage = mock(FieldUsage.class);
        when(usage.getHttpMethod()).thenReturn("GET");
        when(usage.getPathPattern()).thenReturn("/api/v1/users");
        when(usage.getDirection()).thenReturn(FieldDirection.READ);

        assertThat(FieldUsageView.from(usage)).isEqualTo(new FieldUsageView("GET", "/api/v1/users", FieldDirection.READ));
    }
}
