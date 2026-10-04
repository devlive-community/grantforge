// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.springframework.data.jpa.domain.Specification;

/**
 * Which rows of a {@link SecuredEntity} a reader may use, so modules that own entities limit their lists and changes
 * without depending on where data policies are kept. The authorization module provides the policy-based implementation;
 * an application without it gets {@link #unrestricted()}, as no policy can limit its rows. Every method must be called with
 * the reader's tenant bound.
 */
// Its one method is generic, so no lambda can implement it.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface RowScopes
{
    /**
     * Returns the rows of an entity a reader may use for an action.
     *
     * @param accountId the reader
     * @param type the entity class, a {@link SecuredEntity}
     * @param action the action
     * @param <T> the entity class
     * @return the scope, to combine with the query's own filters
     * @throws IllegalArgumentException if the class is not a secured entity
     */
    <T> Specification<T> scope(long accountId, Class<T> type, DataAction action);

    /**
     * Returns a row if a reader may use it for an action, as details, updates and deletes need before they touch it.
     *
     * @param accountId the reader
     * @param repository the entity's repository
     * @param type the entity class
     * @param action the action
     * @param id the row
     * @param <T> the entity class
     * @param <I> its id
     * @return the row
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} if the row does not exist or lies outside the scope,
     *         so its existence does not leak
     */
    default <T, I> T requireWithin(long accountId, ScopedRepository<T, I> repository, Class<T> type, DataAction action, I id)
    {
        return repository.findWithin(id, scope(accountId, type, action))
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no " + type.getSimpleName() + " " + id));
    }

    /**
     * Returns scopes that cover every row, for applications without data policies.
     *
     * @return the scopes
     */
    static RowScopes unrestricted()
    {
        return Unrestricted.INSTANCE;
    }

    /** Covers every row. */
    final class Unrestricted
            implements RowScopes
    {
        private static final Unrestricted INSTANCE = new Unrestricted();

        private Unrestricted()
        {
        }

        @Override
        public <T> Specification<T> scope(long accountId, Class<T> type, DataAction action)
        {
            return Specification.unrestricted();
        }
    }
}
