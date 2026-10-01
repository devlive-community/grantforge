// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicationTest
{
    @Test
    void normalizesCodeNameAndDescription()
    {
        Application application = Application.create(" CRM ", " 客户管理 ", "  ");

        assertThat(application.getCode()).isEqualTo("crm");
        assertThat(application.getName()).isEqualTo("客户管理");
        assertThat(application.getDescription()).isNull();
        assertThat(application.isBuiltin()).isFalse();
        assertThat(application.markBuiltin().isBuiltin()).isTrue();

        application.describe("CRM", " Customers ");
        assertThat(application.getName()).isEqualTo("CRM");
        assertThat(application.getDescription()).isEqualTo("Customers");
    }

    @Test
    void refusesInvalidValues()
    {
        assertThatThrownBy(() -> Application.create("9crm", "CRM", null)).hasMessageContaining("code");
        assertThatThrownBy(() -> Application.create("crm app", "CRM", null)).hasMessageContaining("code");
        assertThatThrownBy(() -> Application.create("crm", " ", null)).hasMessageContaining("name");
        assertThatThrownBy(() -> Application.create("crm", "x".repeat(Application.NAME_MAX + 1), null))
                .hasMessageContaining("at most");
        assertThatThrownBy(() -> Application.create("crm", "CRM", "x".repeat(Application.DESCRIPTION_MAX + 1)))
                .hasMessageContaining("description");
    }
}
