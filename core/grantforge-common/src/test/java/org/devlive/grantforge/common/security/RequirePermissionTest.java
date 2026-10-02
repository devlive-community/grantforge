// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.security;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.assertj.core.api.Assertions.assertThat;

class RequirePermissionTest
{
    @Test
    void codesAreDotSeparatedLowercaseWords()
    {
        assertThat("system.user.read").matches(RequirePermission.CODE);
        assertThat("platform.api-endpoint.review").matches(RequirePermission.CODE);
        assertThat("system").doesNotMatch(RequirePermission.CODE);
        assertThat("System.user").doesNotMatch(RequirePermission.CODE);
        assertThat("system..user").doesNotMatch(RequirePermission.CODE);
        assertThat("system.user." + "x".repeat(120)).doesNotMatch(RequirePermission.CODE);
    }

    @Test
    void annotationsAreReadAtRunTime()
    {
        for (Class<?> annotation : new Class<?>[] {RequirePermission.class, PublicEndpoint.class, AuthenticatedEndpoint.class}) {
            assertThat(annotation.getAnnotation(Retention.class).value()).isEqualTo(RetentionPolicy.RUNTIME);
        }
    }
}
