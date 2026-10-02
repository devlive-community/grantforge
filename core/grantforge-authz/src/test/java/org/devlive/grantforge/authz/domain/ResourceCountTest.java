// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceCountTest
{
    @Test
    void holdsTheCount()
    {
        assertThat(new ResourceCount(7, 3)).isEqualTo(new ResourceCount(7, 3)).extracting(ResourceCount::resources).isEqualTo(3L);
    }
}
