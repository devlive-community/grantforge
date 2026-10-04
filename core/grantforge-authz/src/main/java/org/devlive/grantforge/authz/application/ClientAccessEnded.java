// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import static java.util.Objects.requireNonNull;

/**
 * Published, inside the transaction of the change, when an OAuth client is deleted or disabled, so that the authorization
 * server drops what it issued to the client: its refresh tokens stop working at once.
 *
 * @param id the client's record
 * @param clientId its public identifier
 */
public record ClientAccessEnded(long id, String clientId)
{
    /** Checks the identifier. */
    public ClientAccessEnded
    {
        requireNonNull(clientId, "clientId");
    }
}
