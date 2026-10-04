// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.security;

import org.devlive.grantforge.persistence.secured.FieldMode;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.jspecify.annotations.Nullable;

/**
 * How the signed-in user sees and changes one secured field.
 *
 * @param readMode visible, masked or hidden
 * @param maskStrategy how a masked field is masked; absent unless masked
 * @param writeMode editable or read-only
 */
public record FieldModeResponse(FieldReadMode readMode, @Nullable MaskStrategy maskStrategy, FieldWriteMode writeMode)
{
    /**
     * Converts a mode.
     *
     * @param mode the mode
     * @return the response
     */
    public static FieldModeResponse from(FieldMode mode)
    {
        return new FieldModeResponse(mode.view().mode(), mode.view().mask(), mode.write());
    }
}
