// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;

/**
 * Builds the account search from only the filters that are set (binding {@code null} to "{@code :x is null}"
 * fails on some databases), as a DTO projection with the primary department in one query.
 */
final class UserSearchRepositoryImpl
        implements UserSearchRepository
{
    private static final String PROJECTION = "select new org.devlive.grantforge.identity.domain.UserRow(a.id, a.username,"
            + " a.displayName, a.email, a.status, a.lockedUntil, a.systemAccount, a.mustChangePassword, a.lastLoginAt,"
            + " a.createdAt, o.id, o.name) from UserAccount a"
            + " left join OrgMember m on m.accountId = a.id and m.primaryUnit = true"
            + " left join OrgUnit o on o.id = m.orgUnitId";

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
    public List<UserRow> search(UserCriteria criteria, Instant now, long offset, int limit)
    {
        Map<String, Object> parameters = new HashMap<>();
        TypedQuery<UserRow> query = entities.createQuery(PROJECTION + where(criteria, now, parameters)
                + " order by a.createdAt desc, a.id desc", UserRow.class);
        parameters.forEach(query::setParameter);
        return query.setFirstResult(toIntExact(offset)).setMaxResults(limit).getResultList();
    }

    @Override
    public long count(UserCriteria criteria, Instant now)
    {
        Map<String, Object> parameters = new HashMap<>();
        TypedQuery<Long> query = entities.createQuery("select count(a) from UserAccount a"
                + where(criteria, now, parameters), Long.class);
        parameters.forEach(query::setParameter);
        return query.getSingleResult();
    }

    private static String where(UserCriteria criteria, Instant now, Map<String, Object> parameters)
    {
        StringBuilder where = new StringBuilder(512).append(" where 1 = 1");
        String text = criteria.text();
        if (text != null) {
            where.append(" and (a.usernameNorm like :text or lower(a.displayName) like :text or lower(a.email) like :text)");
            parameters.put("text", "%" + text + "%");
        }
        UserState state = criteria.state();
        if (state == UserState.ACTIVE) {
            where.append(" and a.status = :status and (a.lockedUntil is null or a.lockedUntil <= :now)");
            parameters.put("status", AccountStatus.ACTIVE);
            parameters.put("now", now);
        }
        else if (state == UserState.DISABLED) {
            where.append(" and a.status = :status");
            parameters.put("status", AccountStatus.DISABLED);
        }
        else if (state == UserState.LOCKED) {
            where.append(" and a.lockedUntil > :now");
            parameters.put("now", now);
        }
        String path = criteria.orgUnitPath();
        Long unit = criteria.orgUnitId();
        if (path != null) {
            // Members of the department and of every department below it: the path prefix matches the subtree.
            where.append(" and exists (select m2.id from OrgMember m2, OrgUnit o2 where m2.accountId = a.id"
                    + " and o2.id = m2.orgUnitId and o2.path like :path)");
            parameters.put("path", path + "%");
        }
        else if (unit != null) {
            where.append(" and exists (select m2.id from OrgMember m2 where m2.accountId = a.id and m2.orgUnitId = :unit)");
            parameters.put("unit", unit);
        }
        return where.toString();
    }
}
