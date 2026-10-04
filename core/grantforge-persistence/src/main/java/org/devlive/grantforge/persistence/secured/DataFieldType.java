// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

/** What kind of value a filterable field holds, which decides the comparisons conditions may use. */
public enum DataFieldType
{
    /** Text: equals, contains, starts with. */
    TEXT,
    /** A number: equals and orders. */
    NUMBER,
    /** True or false. */
    BOOLEAN,
    /** One of a fixed set of values. */
    CHOICE,
    /** A moment: before and after. */
    TIME
}
