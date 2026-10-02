// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.catalog;

import org.devlive.grantforge.authz.application.DependencyView;
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.DependencySource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyResponseTest
{
    @Test
    void exposesIdsAsStrings()
    {
        assertThat(DependencyResponse.from(new DependencyView(9_007_199_254_740_993L, 2, 3, DependencyKind.OPTIONAL,
                DependencySource.DECLARED))).isEqualTo(new DependencyResponse("9007199254740993", "2", "3", DependencyKind.OPTIONAL,
                DependencySource.DECLARED));
    }
}
