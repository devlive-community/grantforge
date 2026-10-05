// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** What completing a round did to a reviewed assignment. */
public enum ReviewOutcome
{
    /** The assignment stays. */
    KEPT,

    /** The assignment was removed. */
    REVOKED,

    /** The assignment was to be removed but had been removed in the meantime. */
    GONE
}
