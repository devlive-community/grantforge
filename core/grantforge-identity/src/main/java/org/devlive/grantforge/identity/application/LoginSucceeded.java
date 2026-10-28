// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;

/**
 * A fully authenticated sign-in; consumers must never substitute an editable profile email for the trusted claim.
 *
 * @param tenantId  the authenticated tenant
 * @param accountId the authenticated account
 * @param federated the verified OIDC context, absent for password sign-in
 */
public record LoginSucceeded(long tenantId, long accountId, @Nullable Federated federated)
{
    /**
     * Minimal trusted context carried through MFA; no tokens or secrets are retained.
     *
     * @param sourceCode the OIDC registration code
     * @param email      the provider's email claim
     */
    public record Federated(String sourceCode, @Nullable String email) implements Serializable
    {
    }
}
