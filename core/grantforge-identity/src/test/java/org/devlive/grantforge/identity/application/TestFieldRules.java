// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.persistence.secured.FieldRules;
import org.devlive.grantforge.persistence.secured.FieldView;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Field rules for service tests: every field visible, unless a test says how one is seen. */
final class TestFieldRules
        implements FieldRules
{
    private final Map<String, FieldView> views = new ConcurrentHashMap<>();

    void see(String entity, String field, FieldView view)
    {
        views.put(entity + "." + field, view);
    }

    void clear()
    {
        views.clear();
    }

    @Override
    public FieldView read(long accountId, String entity, String field)
    {
        return views.getOrDefault(entity + "." + field, FieldView.VISIBLE);
    }
}
