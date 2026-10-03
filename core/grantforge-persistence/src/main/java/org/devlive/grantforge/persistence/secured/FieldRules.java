// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import java.util.Map;

/**
 * How readers see and writers change the {@link SecuredField}s of the entities, so the places that show, export or change
 * fields hide, mask and guard them without depending on where field policies are kept. The authorization module provides the policy-based implementation;
 * an application without it gets {@link #open()}. Must be called with the reader's tenant bound.
 */
@FunctionalInterface
public interface FieldRules
{
    /**
     * Returns how a reader sees a field.
     *
     * @param accountId the reader
     * @param entity the entity's code, such as {@code user}
     * @param field the field's code, such as {@code email}
     * @return the view
     */
    FieldView read(long accountId, String entity, String field);

    /**
     * Returns whether a writer may change a field.
     *
     * @param accountId the writer
     * @param entity the entity's code, such as {@code user}
     * @param field the field's code, such as {@code email}
     * @return the write mode; editable unless a policy says otherwise
     */
    default FieldWriteMode write(long accountId, String entity, String field)
    {
        return FieldWriteMode.EDITABLE;
    }

    /**
     * Returns the fields a reader does not see and change freely, for the console to hide, mask or lock them.
     *
     * @param accountId the reader
     * @return the modes of those fields, by entity and field code joined with a dot, such as {@code user.email}
     */
    default Map<String, FieldMode> restricted(long accountId)
    {
        return Map.of();
    }

    /**
     * Returns rules that show every field as it is and let every field change, for applications without field policies.
     *
     * @return the rules
     */
    static FieldRules open()
    {
        return (accountId, entity, field) -> FieldView.VISIBLE;
    }
}
