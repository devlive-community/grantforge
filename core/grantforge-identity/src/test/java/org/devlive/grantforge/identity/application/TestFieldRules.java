// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.persistence.secured.FieldRules;
import org.devlive.grantforge.persistence.secured.FieldView;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Field rules for service tests: every field visible and editable, unless a test says otherwise. */
final class TestFieldRules
        implements FieldRules
{
    private final Map<String, FieldView> views = new ConcurrentHashMap<>();
    private final Set<String> readOnly = ConcurrentHashMap.newKeySet();

    void see(String entity, String field, FieldView view)
    {
        views.put(entity + "." + field, view);
    }

    void readOnly(String entity, String field)
    {
        readOnly.add(entity + "." + field);
    }

    void clear()
    {
        views.clear();
        readOnly.clear();
    }

    @Override
    public FieldView read(long accountId, String entity, String field)
    {
        return views.getOrDefault(entity + "." + field, FieldView.VISIBLE);
    }

    @Override
    public FieldWriteMode write(long accountId, String entity, String field)
    {
        return readOnly.contains(entity + "." + field) ? FieldWriteMode.READONLY : FieldWriteMode.EDITABLE;
    }
}
