// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessPageTest
{
    @Test
    void keepsItsOwnCopyOfTheEvents()
    {
        List<AccessEventView> views = new ArrayList<>();
        AccessPage page = new AccessPage(views, null);
        views.add(null);
        assertThat(page.events()).isEmpty();
        assertThat(page.next()).isNull();
    }
}
