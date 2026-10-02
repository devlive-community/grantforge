// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.authz;

import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizationChangeListenerTest
{
    private final AuthorizationChanges changes = mock(AuthorizationChanges.class);
    private final AuthorizationChangeListener listener = new AuthorizationChangeListener(changes);

    @Test
    @SuppressWarnings("NullAway") // an entity not stored yet has no tenant
    void notesTheEntitysTenantOrTheCatalog()
    {
        TenantScopedEntity scoped = mock(TenantScopedEntity.class);
        when(scoped.getTenantId()).thenReturn(7L);
        listener.changed(scoped);
        verify(changes).tenant(7);

        TenantScopedEntity unsaved = mock(TenantScopedEntity.class);
        when(unsaved.getTenantId()).thenReturn(null);
        TenantContext.runInTenant(9, () -> listener.changed(unsaved));
        verify(changes).currentTenant();

        listener.changed(new Object());
        verify(changes).catalog();

        listener.touched(new Object());
        verify(changes).watch();
    }
}
