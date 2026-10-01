// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.audit.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditContextTest
{
    @Test
    void bindsAnOriginUntilTheScopeCloses()
    {
        RequestOrigin outer = new RequestOrigin("outer", null, null);
        RequestOrigin inner = new RequestOrigin("inner", "10.0.0.1", "Firefox");

        assertThat(AuditContext.current()).isSameAs(RequestOrigin.NONE);
        try (AuditContext.Scope ignored = AuditContext.bind(outer)) {
            try (AuditContext.Scope nested = AuditContext.bind(inner)) {
                assertThat(AuditContext.current()).isEqualTo(inner);
            }
            assertThat(AuditContext.current()).isEqualTo(outer);
        }
        assertThat(AuditContext.current()).isSameAs(RequestOrigin.NONE);
    }
}
