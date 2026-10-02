// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Tidies lists of names from JSON: missing lists become empty; nulls and blanks are left out, the rest stripped. */
final class Texts
{
    private Texts()
    {
    }

    /**
     * Tidies names.
     *
     * @param values the names, or {@code null}
     * @return the stripped names without blanks or repeats, in their order
     */
    @SuppressWarnings("ConstantValue")
    static List<String> of(@Nullable Collection<String> values)
    {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).map(String::strip).filter(value -> !value.isEmpty())
                .distinct().toList();
    }

    /**
     * Copies a list from JSON, leaving out nulls.
     *
     * @param values the list, or {@code null}
     * @param <T> what it holds
     * @return the list
     */
    @SuppressWarnings("ConstantValue")
    static <T> List<T> list(@Nullable Collection<T> values)
    {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }

    /**
     * Strips a text and turns a blank one into {@code null}.
     *
     * @param value the text, or {@code null}
     * @return the text, or {@code null}
     */
    static @Nullable String optional(@Nullable String value)
    {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
