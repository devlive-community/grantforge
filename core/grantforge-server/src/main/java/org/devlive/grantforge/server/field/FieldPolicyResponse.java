// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.field;

import org.devlive.grantforge.authz.field.FieldPolicyView;
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
 * @param maskStrategy how a masked field is masked; absent unless masked
 * @param writeMode editable or read-only
 */
public record FieldPolicyResponse(String entityCode, String fieldCode, FieldReadMode readMode, @Nullable MaskStrategy maskStrategy,
        FieldWriteMode writeMode)
{
    /**
     * Converts a policy.
     *
     * @param policy the policy
     * @return the response
     */
    public static FieldPolicyResponse from(FieldPolicyView policy)
    {
        return new FieldPolicyResponse(policy.entityCode(), policy.fieldCode(), policy.readMode(), policy.maskStrategy(),
                policy.writeMode());
    }
}
