// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.CatalogTestData;
import org.devlive.grantforge.authz.domain.DenyMode;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDetails;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogViewTest
{
    private final Resource system = Resource.create(1, null, ResourceType.MODULE, "system", CatalogTestData.details("System"), 0);
    private final Resource users = Resource.create(1, system, ResourceType.PAGE, "system.user",
            new ResourceDetails("Users", null, null, true, false, DenyMode.HIDE), 0);
    private final Resource edit = Resource.create(1, users, ResourceType.ACTION, "system.user.btn.edit", CatalogTestData.details("Edit"), 0);

    @Test
    void knowsWhatIsSwitchedOffAndChangesOnlyItsCopies()
    {
        CatalogView view = CatalogView.of(1, true, List.of(system, users, edit), List.of());
        Map<Long, Resource> byId = view.byId();

        assertThat(view.disabled()).containsExactly(users.requireId());
        assertThat(view.switchedOff(byId, system.requireId())).isFalse();
        assertThat(view.switchedOff(byId, users.requireId())).isTrue();
        assertThat(view.switchedOff(byId, edit.requireId())).isTrue();
        assertThat(view.switchedOff(byId, 42)).isFalse();

        CatalogView enabled = view.withEnabled(users.requireId(), true);
        assertThat(enabled.switchedOff(byId, edit.requireId())).isFalse();
        assertThat(enabled.withEnabled(system.requireId(), false).switchedOff(byId, edit.requireId())).isTrue();
        assertThat(view.withDependencies(List.of()).dependencies()).isEmpty();
        assertThat(view.disabled()).containsExactly(users.requireId());
    }
}
