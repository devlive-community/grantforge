// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.field;

import org.devlive.grantforge.authz.domain.FieldPolicy;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.jspecify.annotations.Nullable;

/**
 * How a role sees and changes one secured field.
 *
 * @param entityCode the field's entity
 * @param fieldCode the field
 * @param readMode visible, masked or hidden
 * @param maskStrategy how a masked field is masked; {@code null} unless masked
 * @param writeMode editable or read-only
 */
public record FieldPolicyView(String entityCode, String fieldCode, FieldReadMode readMode, @Nullable MaskStrategy maskStrategy,
        FieldWriteMode writeMode)
{
    /**
     * Converts a stored policy.
     *
     * @param policy the policy
     * @return the view
     */
    public static FieldPolicyView from(FieldPolicy policy)
    {
        return new FieldPolicyView(policy.getEntityCode(), policy.getFieldCode(), policy.getReadMode(), policy.getMaskStrategy(),
                policy.getWriteMode());
    }
}
