// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.FieldDirection;
import org.devlive.grantforge.persistence.secured.DeclaredField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class FieldAppearanceTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guard
    void needsEveryValue()
    {
        DeclaredField email = new DeclaredField("user", "email", "E-mail");
        assertThat(new FieldAppearance(email, "GET", "/api/v1/users", FieldDirection.READ).field()).isEqualTo(email);
        assertThatNullPointerException().isThrownBy(() -> new FieldAppearance(email, "GET", "/api/v1/users", null));
    }
}
