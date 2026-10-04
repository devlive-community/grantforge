// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationEntityTest
{
    @Test
    void describesItself()
    {
        ApplicationEntity order = ApplicationEntity.create(5, "shop:order");
        List<DataField> fields = List.of(new DataField("total", "Total", DataFieldType.NUMBER, List.of()));
        order.describe("Orders", true, false, fields);

        assertThat(order.getApplicationId()).isEqualTo(5);
        assertThat(order.getCode()).isEqualTo("shop:order");
        assertThat(order.getName()).isEqualTo("Orders");
        assertThat(order.isOwned()).isTrue();
        assertThat(order.isUnitBased()).isFalse();
        assertThat(order.getFields()).isEqualTo(fields);
        order.describe("Orders", false, true, List.of());
        assertThat(order.getFields()).isEmpty();
        assertThat(order.isUnitBased()).isTrue();
    }
}
