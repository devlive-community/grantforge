// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.GrantDerivation;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrantMatrixTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void copiesItsListsAndChecksItsParts()
    {
        List<GrantMatrix.Grant> grants = new ArrayList<>(List.of(new GrantMatrix.Grant(1, GrantEffect.ALLOW, null, true)));
        GrantMatrix matrix = new GrantMatrix(1, 2, false, grants, List.of(new GrantMatrix.State(1, GrantDerivation.State.ALLOWED, true,
                List.of())));
        grants.clear();

        assertThat(matrix.grants()).hasSize(1);
        assertThatThrownBy(() -> new GrantMatrix.Grant(1, null, null, true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GrantMatrix.State(1, null, true, List.of())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GrantDerivation.Reason(1, null)).isInstanceOf(NullPointerException.class);
    }
}
