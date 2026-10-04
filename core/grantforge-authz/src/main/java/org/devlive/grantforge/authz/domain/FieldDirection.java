// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

/** Whether an API returns a secured field or accepts it. */
public enum FieldDirection
{
    /** The API returns the field, so read permissions apply. */
    READ,

    /** The API accepts the field, so write permissions apply. */
    WRITE
}
