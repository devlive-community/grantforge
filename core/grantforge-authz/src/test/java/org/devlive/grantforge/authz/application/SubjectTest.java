// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.SubjectType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubjectTest
{
    @Test
    @SuppressWarnings("NullAway") // deliberately violates the non-null contract to test the guards
    void requiresTypeAndName()
    {
        assertThat(new Subject(SubjectType.GROUP, 1, "Dev", null).detail()).isNull();
        assertThatThrownBy(() -> new Subject(null, 1, "Dev", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Subject(SubjectType.GROUP, 1, null, null)).isInstanceOf(NullPointerException.class);
    }
}
