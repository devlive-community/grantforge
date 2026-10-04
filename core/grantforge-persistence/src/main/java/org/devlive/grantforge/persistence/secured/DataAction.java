// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

/** What a reader does with rows of a secured entity, which data policies allow or deny. */
public enum DataAction
{
    /** See the rows in lists and details. */
    READ,
    /** Change the rows. */
    UPDATE,
    /** Delete the rows. */
    DELETE,
    /** Export the rows to files. */
    EXPORT
}
