// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.identity.domain.OrgUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrgUnitViewTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void copiesTheUnit()
    {
        OrgUnit hq = OrgUnit.create(null, "hq", "HQ", 0);
        OrgUnit sales = OrgUnit.create(hq, "sales", "Sales", 2);

        assertThat(OrgUnitView.from(sales)).isEqualTo(new OrgUnitView(sales.requireId(), hq.requireId(), "sales", "Sales", 2, 1));
        assertThatThrownBy(() -> new OrgUnitView(1, null, null, "n", 0, 0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OrgUnitView(1, null, "c", null, 0, 0)).isInstanceOf(NullPointerException.class);
    }
}
