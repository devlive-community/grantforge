// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import static java.util.Objects.requireNonNull;

/**
 * How someone sees and changes one secured field.
 *
 * @param view how they see it
 * @param write whether they may change it
 */
public record FieldMode(FieldView view, FieldWriteMode write)
{
    /** Visible and editable, as fields are unless a policy says otherwise. */
    public static final FieldMode OPEN = new FieldMode(FieldView.VISIBLE, FieldWriteMode.EDITABLE);

    /** Checks that both parts are present. */
    public FieldMode
    {
        requireNonNull(view, "view");
        requireNonNull(write, "write");
    }
}
