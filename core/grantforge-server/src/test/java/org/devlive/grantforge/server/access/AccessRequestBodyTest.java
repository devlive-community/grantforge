// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.access;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessRequestBodyTest
{
    @Test
    void exposesItsComponents()
    {
        AccessRequestBody body = new AccessRequestBody("3", "why", 7);

        assertThat(body.roleId()).isEqualTo("3");
        assertThat(body.days()).isEqualTo(7);
    }
}
