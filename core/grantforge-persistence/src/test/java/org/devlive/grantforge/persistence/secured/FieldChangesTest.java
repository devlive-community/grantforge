// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldChangesTest
{
    private final Map<String, FieldView> views = new HashMap<>();
    private final Map<String, FieldWriteMode> writes = new HashMap<>();
    private final FieldRules rules = new FieldRules()
    {
        @Override
        public FieldView read(long accountId, String entity, String field)
        {
            return views.getOrDefault(field, FieldView.VISIBLE);
        }

        @Override
        public FieldWriteMode write(long accountId, String entity, String field)
        {
            return writes.getOrDefault(field, FieldWriteMode.EDITABLE);
        }
    };

    @Test
    void takesWhatChangesAndMayChange()
    {
        FieldChanges changes = FieldChanges.of(rules, 7, "user");
        assertThat(changes.take("email", "alice@acme.io", "alice@lab.io")).isEqualTo("alice@lab.io");
        assertThat(changes.take("email", null, "alice@lab.io")).isEqualTo("alice@lab.io");
        assertThat(changes.take("email", "alice@acme.io", null)).isNull();
        changes.requireAllowed();
        assertThat(changes.refused()).isEmpty();
    }

    @Test
    void whatTheWriterWasShownChangesNothing()
    {
        views.put("email", FieldView.masked(MaskStrategy.EMAIL));
        views.put("lastLoginAt", FieldView.HIDDEN);
        writes.put("email", FieldWriteMode.READONLY);
        writes.put("lastLoginAt", FieldWriteMode.READONLY);
        FieldChanges changes = FieldChanges.of(rules, 7, "user");
        Instant at = Instant.parse("2026-10-02T08:00:00Z");
        assertThat(changes.take("email", "alice@acme.io", "a***@acme.io")).isEqualTo("alice@acme.io");
        assertThat(changes.take("lastLoginAt", at, null)).isEqualTo(at);
        assertThat(changes.take("email", "alice@acme.io", "alice@acme.io")).isEqualTo("alice@acme.io");
        assertThat(changes.take("email", null, " ")).isNull();
        changes.requireAllowed();
    }

    @Test
    void refusesEveryReadOnlyFieldTogether()
    {
        writes.put("email", FieldWriteMode.READONLY);
        writes.put("phone", FieldWriteMode.READONLY);
        FieldChanges changes = FieldChanges.of(rules, 7, "user");
        assertThat(changes.take("email", "alice@acme.io", "alice@lab.io")).isEqualTo("alice@acme.io");
        assertThat(changes.take("phone", null, "13812345678")).isNull();
        assertThat(changes.take("name", "Alice", "Alicia")).isEqualTo("Alicia");
        assertThat(changes.refused()).containsExactly("email", "phone");
        assertThatThrownBy(changes::requireAllowed).isInstanceOfSatisfying(GrantForgeException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(FieldErrorCode.READONLY_CHANGED);
            assertThat(error.getFieldIssues()).extracting(FieldIssue::field).containsExactly("email", "phone");
        });
    }
}
