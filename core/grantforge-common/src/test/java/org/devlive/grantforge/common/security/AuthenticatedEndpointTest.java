// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@AuthenticatedEndpoint
class AuthenticatedEndpointTest
{
    @Test
    void marksTypes()
    {
        assertThat(AuthenticatedEndpointTest.class.isAnnotationPresent(AuthenticatedEndpoint.class)).isTrue();
    }
}
