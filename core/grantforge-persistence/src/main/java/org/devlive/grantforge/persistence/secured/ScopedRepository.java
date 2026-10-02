// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * A repository of a {@link SecuredEntity} whose reads, counts, exports, updates and deletes go through a data scope: the
 * {@link Specification} that limits rows to those a reader's data policies allow. Lists use
 * {@link #findAll(Specification, org.springframework.data.domain.Pageable)} and its relatives with the scope combined
 * with their own filters; single rows use {@link #findWithin}, so a row outside the scope looks like one that does not
 * exist.
 *
 * @param <T> the entity
 * @param <I> its id
 */
@NoRepositoryBean
public interface ScopedRepository<T, I>
        extends JpaRepository<T, I>, JpaSpecificationExecutor<T>
{
    /**
     * Finds a row if the scope covers it.
     *
     * @param id the row's id
     * @param scope the reader's data scope
     * @return the row, or empty if it does not exist or lies outside the scope
     */
    default Optional<T> findWithin(I id, Specification<T> scope)
    {
        Specification<T> identified = (root, query, builder) -> builder.equal(root.get("id"), id);
        return findOne(scope.and(identified));
    }

    /**
     * Returns whether the scope covers a row.
     *
     * @param id the row's id
     * @param scope the reader's data scope
     * @return {@code true} if the row exists and lies inside the scope
     */
    default boolean existsWithin(I id, Specification<T> scope)
    {
        Specification<T> identified = (root, query, builder) -> builder.equal(root.get("id"), id);
        return exists(scope.and(identified));
    }

    /**
     * Finds the rows among some ids that the scope covers; the caller keeps the number of ids within what one IN clause
     * takes (see {@link org.devlive.grantforge.persistence.query.InClauseBatcher}).
     *
     * @param ids the rows' ids
     * @param scope the reader's data scope
     * @return the rows that exist and lie inside the scope, in no particular order
     */
    default List<T> findAllWithin(Collection<I> ids, Specification<T> scope)
    {
        if (ids.isEmpty()) {
            return List.of();
        }
        Specification<T> identified = (root, query, builder) -> root.get("id").in(ids);
        return findAll(scope.and(identified));
    }
}
