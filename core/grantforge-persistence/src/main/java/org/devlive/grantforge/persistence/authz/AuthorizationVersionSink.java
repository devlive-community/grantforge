// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.authz;

import java.util.Collection;

/**
 * Takes note that permissions of some scopes may have changed: {@link AuthorizationChanges#CATALOG} for the shared
 * catalog, or {@link AuthorizationChanges#tenantScope(long)} for one tenant. Called just before the changing
 * transaction commits, within it, so the note commits together with the change.
 */
@FunctionalInterface
public interface AuthorizationVersionSink
{
    /**
     * Notes changes.
     *
     * @param scopes the scopes whose permissions may have changed, each once
     */
    void changed(Collection<String> scopes);
}
