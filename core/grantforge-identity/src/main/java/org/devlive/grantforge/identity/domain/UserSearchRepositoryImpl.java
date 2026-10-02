// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.devlive.grantforge.persistence.query.IdOrder;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;

/**
 * Builds the account search from only the filters that are set, combined with the reader's data scope: the page of ids
 * comes from a criteria query, the rows with the primary department from one DTO projection.
 */
final class UserSearchRepositoryImpl
        implements UserSearchRepository
{
    private static final String PROJECTION = "select new org.devlive.grantforge.identity.domain.UserRow(a.id, a.username,"
            + " a.displayName, a.email, a.status, a.lockedUntil, a.systemAccount, a.mustChangePassword, a.lastLoginAt,"
            + " a.createdAt, o.id, o.name) from UserAccount a"
            + " left join OrgMember m on m.accountId = a.id and m.primaryUnit = true"
            + " left join OrgUnit o on o.id = m.orgUnitId"
            + " where a.id in :ids";

    private final EntityManager entities;

    /**
     * Creates the search.
     *
     * @param entities the shared, transaction-bound entity manager
     */
    UserSearchRepositoryImpl(EntityManager entities)
    {
        this.entities = requireNonNull(entities, "entities");
    }

    @Override
    public List<UserRow> search(UserCriteria criteria, Specification<UserAccount> scope, Instant now, long offset, int limit)
    {
        CriteriaBuilder builder = entities.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<UserAccount> account = query.from(UserAccount.class);
        query.select(account.get("id")).where(where(criteria, scope, now, account, query, builder))
                .orderBy(builder.desc(account.get("createdAt")), builder.desc(account.get("id")));
        List<Long> ids = entities.createQuery(query).setFirstResult(toIntExact(offset)).setMaxResults(limit).getResultList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return IdOrder.arrange(ids, entities.createQuery(PROJECTION, UserRow.class).setParameter("ids", ids).getResultList(),
                UserRow::id);
    }

    @Override
    public long count(UserCriteria criteria, Specification<UserAccount> scope, Instant now)
    {
        CriteriaBuilder builder = entities.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<UserAccount> account = query.from(UserAccount.class);
        query.select(builder.count(account)).where(where(criteria, scope, now, account, query, builder));
        return entities.createQuery(query).getSingleResult();
    }

    private static Predicate where(UserCriteria criteria, Specification<UserAccount> scope, Instant now,
            Root<UserAccount> account, CriteriaQuery<?> query, CriteriaBuilder builder)
    {
        List<Predicate> all = new ArrayList<>();
        String text = criteria.text();
        if (text != null) {
            String pattern = "%" + text + "%";
            all.add(builder.or(builder.like(account.get("usernameNorm"), pattern),
                    builder.like(builder.lower(account.get("displayName")), pattern),
                    builder.like(builder.lower(account.get("email")), pattern)));
        }
        Path<Instant> lockedUntil = account.get("lockedUntil");
        UserState state = criteria.state();
        if (state == UserState.ACTIVE) {
            all.add(builder.equal(account.get("status"), AccountStatus.ACTIVE));
            all.add(builder.or(builder.isNull(lockedUntil), builder.lessThanOrEqualTo(lockedUntil, now)));
        }
        else if (state == UserState.DISABLED) {
            all.add(builder.equal(account.get("status"), AccountStatus.DISABLED));
        }
        else if (state == UserState.LOCKED) {
            all.add(builder.greaterThan(lockedUntil, now));
        }
        String path = criteria.orgUnitPath();
        Long unit = criteria.orgUnitId();
        if (path != null || unit != null) {
            all.add(builder.exists(membership(path, unit, account, query, builder)));
        }
        Predicate scoped = scope.toPredicate(account, query, builder);
        if (scoped != null) {
            all.add(scoped);
        }
        return builder.and(all.toArray(Predicate[]::new));
    }

    /** Memberships of the account in the department, or in it and every department below it when the path is given. */
    private static Subquery<Long> membership(@Nullable String path, @Nullable Long unit, Root<UserAccount> account, CriteriaQuery<?> query,
            CriteriaBuilder builder)
    {
        Subquery<Long> members = query.subquery(Long.class);
        Root<OrgMember> member = members.from(OrgMember.class);
        members.select(member.get("id"));
        Predicate own = builder.equal(member.get("accountId"), account.get("id"));
        if (path == null) {
            return members.where(own, builder.equal(member.get("orgUnitId"), unit));
        }
        // The path prefix matches the subtree.
        Root<OrgUnit> department = members.from(OrgUnit.class);
        return members.where(own, builder.equal(department.get("id"), member.get("orgUnitId")),
                builder.like(department.get("path"), path + "%"));
    }
}
