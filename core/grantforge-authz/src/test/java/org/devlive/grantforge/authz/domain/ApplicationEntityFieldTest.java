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

class ApplicationEntityFieldTest
{
    @Test
    void keepsTheFieldWithItsChoices()
    {
        DataField status = new DataField("status", "Status", DataFieldType.CHOICE, List.of("OPEN", "PAID"));
        DataField total = new DataField("total", "Total", DataFieldType.NUMBER, List.of());

        assertThat(ApplicationEntityField.of(status).toField()).isEqualTo(status);
        assertThat(ApplicationEntityField.of(total).toField()).isEqualTo(total);
    }
}
