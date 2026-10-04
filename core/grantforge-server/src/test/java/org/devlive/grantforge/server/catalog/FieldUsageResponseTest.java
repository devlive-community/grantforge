// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.FieldUsageView;
import org.devlive.grantforge.authz.domain.FieldDirection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldUsageResponseTest
{
    @Test
    void copiesAUsage()
    {
        assertThat(FieldUsageResponse.from(new FieldUsageView("PUT", "/api/v1/users/{id}", FieldDirection.WRITE)))
                .isEqualTo(new FieldUsageResponse("PUT", "/api/v1/users/{id}", FieldDirection.WRITE));
    }
}
