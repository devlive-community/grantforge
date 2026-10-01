// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.common.lang.Strings;
import org.springframework.security.core.AuthenticatedPrincipal;

import java.io.Serial;
import java.io.Serializable;

/**
 * The principal stored in a console session. It holds identifiers only: anything that may change (names,
 * status) is read from the database when needed.
 *
 * <p>{@link #getName()} is the account ID, which Spring Session indexes as the session's principal name, so
 * all sessions of an account can be found and revoked.
 *
 * @param accountId the signed-in account
 * @param tenantId the account's tenant, bound to every request of the session
 * @param username the login name at sign-in, for logs
 */
public record SessionUser(long accountId, long tenantId, String username)
        implements AuthenticatedPrincipal, Serializable
{
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Validates the identifiers.
     *
     * @param accountId the account; positive
     * @param tenantId the tenant; positive
     * @param username the login name; not blank
     * @throws IllegalArgumentException if a value is invalid
     */
    public SessionUser
    {
        if (accountId <= 0 || tenantId <= 0) {
            throw new IllegalArgumentException("account and tenant IDs must be positive");
        }
        username = Strings.requireNonBlank(username, "username");
    }

    @Override
    public String getName()
    {
        return Long.toString(accountId);
    }
}
