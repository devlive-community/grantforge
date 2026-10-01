// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogTest
{
    @Test
    void reportsInvalidValuesAsBadRequests()
    {
        assertThat(Catalog.valid(() -> "ok")).isEqualTo("ok");
        assertThatThrownBy(() -> Catalog.valid(() -> {
            throw new IllegalArgumentException("name must not be blank");
        })).isInstanceOf(GrantForgeException.class).hasMessage("name must not be blank")
                .satisfies(error -> assertThat(((GrantForgeException) error).getErrorCode()).isEqualTo(CommonErrorCode.BAD_REQUEST));
    }
}
