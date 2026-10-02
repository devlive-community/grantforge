// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** How an endpoint changed since an administrator last reviewed the API catalog. */
public enum EndpointChange
{
    /** It appeared. */
    ADDED,

    /** Who may call it changed, or it came back after being removed. */
    CHANGED,

    /** It is no longer served. */
    REMOVED
}
