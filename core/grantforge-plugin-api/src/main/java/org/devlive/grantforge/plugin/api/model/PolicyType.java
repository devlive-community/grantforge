// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

/** The kinds of policy a service type can support. */
public enum PolicyType
{
    /** Allows or denies access types on resources; every service type supports it. */
    ACCESS,

    /** Masks values (for example columns) when they are read; see {@link DataMaskDefinition}. */
    DATA_MASK,

    /** Restricts which rows a user reads; see {@link RowFilterDefinition}. */
    ROW_FILTER
}
