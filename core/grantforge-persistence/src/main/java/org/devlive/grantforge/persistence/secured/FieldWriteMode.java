// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

/** Whether a writer may change a secured field. */
public enum FieldWriteMode
{
    /** The field may be changed. */
    EDITABLE,

    /** The field keeps its value; requests that change it are refused. */
    READONLY
}
