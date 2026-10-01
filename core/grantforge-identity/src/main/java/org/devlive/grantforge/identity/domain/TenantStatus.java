// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

/** Lifecycle state of a tenant. */
public enum TenantStatus
{
    /** Users of the tenant can sign in. */
    ACTIVE,
    /** Nobody of the tenant can sign in; data is kept. */
    SUSPENDED
}
