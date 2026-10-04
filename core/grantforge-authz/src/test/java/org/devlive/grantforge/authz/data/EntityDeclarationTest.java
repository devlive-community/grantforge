// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.persistence.secured.DataField;
import org.devlive.grantforge.persistence.secured.DataFieldType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntityDeclarationTest
{
    @Test
    void copiesItsFields()
    {
        List<DataField> fields = new ArrayList<>(List.of(new DataField("total", "Total", DataFieldType.NUMBER, List.of())));
        EntityDeclaration order = new EntityDeclaration("order", "Orders", true, false, fields);
        fields.clear();

        assertThat(order.fields()).hasSize(1);
    }
}
