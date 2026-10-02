// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import org.devlive.grantforge.authz.application.GrantMatrix;
import org.devlive.grantforge.authz.domain.GrantDerivation;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrantMatrixResponseTest
{
    @Test
    void exposesIdsAsStrings()
    {
        GrantMatrix matrix = new GrantMatrix(1, 2, true, List.of(new GrantMatrix.Grant(3, GrantEffect.ALLOW, null, true)),
                List.of(new GrantMatrix.State(4, GrantDerivation.State.IMPLIED, false, List.of(new GrantDerivation.Reason(3,
                        GrantDerivation.Via.ANCESTOR)))));

        assertThat(GrantMatrixResponse.from(matrix)).isEqualTo(new GrantMatrixResponse("1", "2", true,
                List.of(new GrantMatrixResponse.Grant("3", GrantEffect.ALLOW, null, true)),
                List.of(new GrantMatrixResponse.State("4", GrantDerivation.State.IMPLIED, false,
                        List.of(new GrantMatrixResponse.Reason("3", GrantDerivation.Via.ANCESTOR))))));
    }
}
