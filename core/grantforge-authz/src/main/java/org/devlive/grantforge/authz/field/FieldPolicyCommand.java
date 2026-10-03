// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.field;

import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * How a role sees and changes one secured field.
 *
 * @param entityCode the field's entity, such as {@code user}
 * @param fieldCode the field, such as {@code email}
 * @param readMode visible, masked or hidden
 * @param maskStrategy how a masked field is masked; {@code null} unless masked
 * @param writeMode editable or read-only
 */
public record FieldPolicyCommand(String entityCode, String fieldCode, FieldReadMode readMode, @Nullable MaskStrategy maskStrategy,
        FieldWriteMode writeMode)
{
    /** Checks that the required values are present. */
    public FieldPolicyCommand
    {
        requireNonNull(entityCode, "entityCode");
        requireNonNull(fieldCode, "fieldCode");
        requireNonNull(readMode, "readMode");
        requireNonNull(writeMode, "writeMode");
    }
}
