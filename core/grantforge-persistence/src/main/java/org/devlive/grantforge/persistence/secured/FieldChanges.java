// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.persistence.secured;

import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Guards the secured fields of one change by one writer: a field counts as changed only if the submitted value differs from
 * the stored one and from what the writer was shown of it (its masked text, or nothing for a hidden field), so a form
 * that sends back what it showed changes nothing. Changes of read-only fields are collected and refused together.
 */
public final class FieldChanges
{
    private final FieldRules rules;
    private final long writerId;
    private final String entity;
    private final List<String> refused = new ArrayList<>();

    private FieldChanges(FieldRules rules, long writerId, String entity)
    {
        this.rules = requireNonNull(rules, "rules");
        this.writerId = writerId;
        this.entity = requireNonNull(entity, "entity");
    }

    /**
     * Starts guarding a change.
     *
     * @param rules how writers see and change fields
     * @param writerId the writer
     * @param entity the changed entity's code
     * @return the guard
     */
    public static FieldChanges of(FieldRules rules, long writerId, String entity)
    {
        return new FieldChanges(rules, writerId, entity);
    }

    /**
     * Returns the value to store for a field.
     *
     * @param field the field's code
     * @param current the stored value, or {@code null} for a new row
     * @param submitted the submitted value
     * @param <T> the value type
     * @return the submitted value if the field changes and may change; the stored value otherwise
     */
    public <T> @Nullable T take(String field, @Nullable T current, @Nullable T submitted)
    {
        FieldView view = rules.read(writerId, entity, requireNonNull(field, "field"));
        if (same(current, submitted) || (view.mode() != FieldReadMode.VISIBLE && same(view.present(current), submitted))) {
            return current;
        }
        if (rules.write(writerId, entity, field) == FieldWriteMode.READONLY) {
            refused.add(field);
            return current;
        }
        return submitted;
    }

    /**
     * Returns the read-only fields the change tried to change.
     *
     * @return their codes, in the order they were taken
     */
    public List<String> refused()
    {
        return List.copyOf(refused);
    }

    /**
     * Refuses the change if it changes read-only fields.
     *
     * @throws GrantForgeException with {@link FieldErrorCode#READONLY_CHANGED} and an issue per such field
     */
    public void requireAllowed()
    {
        if (!refused.isEmpty()) {
            throw new GrantForgeException(FieldErrorCode.READONLY_CHANGED, "read-only fields changed: " + refused)
                    .withFieldIssues(refused.stream().map(field -> FieldIssue.of(field, "error.field.readonly", field)).toList());
        }
    }

    /** Whether two values are the same, blank text counting as no value. */
    private static boolean same(@Nullable Object left, @Nullable Object right)
    {
        return Objects.equals(normal(left), normal(right));
    }

    private static @Nullable Object normal(@Nullable Object value)
    {
        if (value instanceof CharSequence text) {
            String plain = text.toString();
            return plain.isBlank() ? null : plain;
        }
        return value;
    }
}
