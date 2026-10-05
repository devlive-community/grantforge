// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.access;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequestableRolesBodyTest
{
    @Test
    void parsesTheRoles()
    {
        RequestableRolesBody body = new RequestableRolesBody(List.of(new RequestableRolesBody.Entry(" 3 ", 30), new RequestableRolesBody.Entry("4", null)));

        assertThat(body.maxDays()).isEqualTo(Map.of(3L, 30, 4L, 0));
        assertThat(new RequestableRolesBody(null).maxDays()).isEmpty();
    }
}
