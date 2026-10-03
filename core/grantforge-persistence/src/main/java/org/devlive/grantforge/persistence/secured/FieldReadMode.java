// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

/** How a reader sees a secured field, from most to least revealing. */
public enum FieldReadMode
{
    /** The value as it is. */
    VISIBLE,

    /** The value with most of it hidden, such as {@code a***@example.com}. */
    MASKED,

    /** Nothing: the field is left out. */
    HIDDEN
}
