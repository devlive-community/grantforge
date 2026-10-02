// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogTextTest
{
    @Test
    void namesAreRequiredAndOptionalTextsMayBeBlank()
    {
        assertThat(CatalogText.name(" A ", 3)).isEqualTo("A");
        assertThatThrownBy(() -> CatalogText.name(null, 3)).hasMessageContaining("name");
        assertThatThrownBy(() -> CatalogText.name("abcd", 3)).hasMessageContaining("at most 3");
        assertThat(CatalogText.optional(" ", 3, "note")).isNull();
        assertThat(CatalogText.optional(null, 3, "note")).isNull();
        assertThat(CatalogText.optional(" ab ", 3, "note")).isEqualTo("ab");
        assertThatThrownBy(() -> CatalogText.optional("abcd", 3, "note")).hasMessage("note must be at most 3 characters");
    }
}
