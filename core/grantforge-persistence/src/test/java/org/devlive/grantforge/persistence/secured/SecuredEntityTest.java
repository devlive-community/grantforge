// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecuredEntityTest
{
    @SecuredEntity(code = "sample", name = "Samples")
    static final class Sample
    {
    }

    @Test
    void marksEntitiesAndLeavesOwnershipOutUnlessDeclared()
    {
        SecuredEntity declared = Sample.class.getAnnotation(SecuredEntity.class);
        assertThat(declared.code()).isEqualTo("sample");
        assertThat(declared.owner()).isEmpty();
        assertThat(declared.unit()).isEmpty();
        assertThat(declared.unitFromOwner()).isFalse();
        assertThat(declared.tenant()).isEmpty();
    }
}
