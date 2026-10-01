// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** Who may call an endpoint, as its annotation declares. */
public enum EndpointAccess
{
    /** Anyone, signed in or not. */
    PUBLIC,

    /** Every signed-in user. */
    AUTHENTICATED,

    /** Users granted the endpoint's permission. */
    PERMISSION
}
