// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.field;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.field.FieldPolicyCommand;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.jspecify.annotations.Nullable;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * How a role sees and changes one secured field.
 *
 * @param entityCode the field's entity, such as {@code user}
 * @param fieldCode the field, such as {@code email}
 * @param readMode visible, masked or hidden
 * @param maskStrategy how a masked field is masked; only for masked fields
 * @param writeMode editable (the default) or read-only
 */
public record FieldPolicyRequest(
        @NotBlank @Size(max = 64) @Nullable String entityCode,
        @NotBlank @Size(max = 64) @Nullable String fieldCode,
        @NotNull @Nullable FieldReadMode readMode,
        @Nullable MaskStrategy maskStrategy,
        @Nullable FieldWriteMode writeMode)
{
    /**
     * Converts the validated request.
     *
     * @return the command
     */
    public FieldPolicyCommand toCommand()
    {
        return new FieldPolicyCommand(requireNonNull(entityCode, "entityCode").strip(), requireNonNull(fieldCode, "fieldCode").strip(),
                requireNonNull(readMode, "readMode"), maskStrategy, requireNonNullElse(writeMode, FieldWriteMode.EDITABLE));
    }
}
