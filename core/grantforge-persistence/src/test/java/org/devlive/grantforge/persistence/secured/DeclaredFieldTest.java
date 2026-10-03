// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DeclaredFieldTest
{
    private record Sample(@SecuredField(entity = "user", field = "lastLoginAt", name = "Last sign-in") String at)
    {
    }

    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void namesItsCatalogResource()
    {
        DeclaredField field = DeclaredField.of(Sample.class.getRecordComponents()[0].getAnnotation(SecuredField.class));

        assertThat(field).isEqualTo(new DeclaredField("user", "lastLoginAt", "Last sign-in"));
        assertThat(field.resourceCode()).isEqualTo("entity:user.lastLoginAt");
        assertThatNullPointerException().isThrownBy(() -> new DeclaredField("user", null, "x"));
    }
}
