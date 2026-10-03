// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

/**
 * How readers see the {@link SecuredField}s of the entities, so the places that show or export fields hide and mask them
 * without depending on where field policies are kept. The authorization module provides the policy-based implementation;
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
     * Returns rules that show every field as it is, for applications without field policies.
     *
     * @return the rules
     */
    static FieldRules open()
    {
        return (accountId, entity, field) -> FieldView.VISIBLE;
    }
}
