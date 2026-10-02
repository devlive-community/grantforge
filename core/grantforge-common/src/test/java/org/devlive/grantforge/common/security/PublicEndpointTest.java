// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicEndpointTest
{
    @PublicEndpoint
    void open()
    {
    }

    @Test
    void marksMethods() throws NoSuchMethodException
    {
        assertThat(PublicEndpointTest.class.getDeclaredMethod("open").isAnnotationPresent(PublicEndpoint.class)).isTrue();
    }
}
