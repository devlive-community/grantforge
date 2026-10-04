// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

/** What is done with rows, as GrantForge's data policies name it. */
public enum DataAction
{
    /** Listing and reading rows. */
    READ,
    /** Changing rows. */
    UPDATE,
    /** Deleting rows. */
    DELETE,
    /** Exporting rows. */
    EXPORT
}
