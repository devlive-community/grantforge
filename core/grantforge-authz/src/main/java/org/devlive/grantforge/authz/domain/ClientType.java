// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** Whether an OAuth client can keep a secret. */
public enum ClientType
{
    /** A server-side application that keeps a client secret. */
    CONFIDENTIAL,

    /** A browser or mobile application that cannot keep a secret; it proves itself with PKCE instead. */
    PUBLIC
}
