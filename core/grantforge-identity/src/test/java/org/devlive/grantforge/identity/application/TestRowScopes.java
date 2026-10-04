// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.RowScopes;
import org.springframework.data.jpa.domain.Specification;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Data scopes for service tests: every row, unless a test limits the rows of an entity for an action. */
final class TestRowScopes
        implements RowScopes
{
    private final Map<Key, Specification<?>> limits = new ConcurrentHashMap<>();

    /** Rows whose attribute has a value. */
    static <T> Specification<T> where(String attribute, Object value)
    {
        return (root, query, builder) -> builder.equal(root.get(attribute), value);
    }

    /** No row. */
    static <T> Specification<T> none()
    {
        return (root, query, builder) -> builder.disjunction();
    }

    <T> void limit(Class<T> type, DataAction action, Specification<T> rows)
    {
        limits.put(new Key(type, action), rows);
    }

    void clear()
    {
        limits.clear();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Specification<T> scope(long accountId, Class<T> type, DataAction action)
    {
        Specification<T> limit = (Specification<T>) limits.get(new Key(type, action));
        return limit == null ? Specification.unrestricted() : limit;
    }

    private record Key(Class<?> type, DataAction action)
    {
    }
}
