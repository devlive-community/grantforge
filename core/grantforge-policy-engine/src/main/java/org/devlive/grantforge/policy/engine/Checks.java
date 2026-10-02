// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Argument checks and defensive copies for the model, in Java 8. */
final class Checks
{
    private Checks()
    {
    }

    static <T> T notNull(@Nullable T value, String what)
    {
        if (value == null) {
            throw new NullPointerException(what + " is required");
        }
        return value;
    }

    static String text(@Nullable String value, String what)
    {
        String checked = notNull(value, what);
        if (blank(checked)) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
        return checked;
    }

    private static boolean blank(String value)
    {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isWhitespace(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    static <T> List<T> list(@Nullable Collection<? extends T> values, String what)
    {
        List<T> copy = new ArrayList<>();
        for (T value : notNull(values, what)) {
            copy.add(notNull(value, "element of " + what));
        }
        return Collections.unmodifiableList(copy);
    }

    static Set<String> texts(@Nullable Collection<String> values, String what)
    {
        Set<String> copy = new LinkedHashSet<>();
        for (String value : notNull(values, what)) {
            copy.add(text(value, "element of " + what));
        }
        return Collections.unmodifiableSet(copy);
    }

    static <V> Map<String, V> map(@Nullable Map<String, ? extends V> values, String what)
    {
        Map<String, V> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ? extends V> entry : notNull(values, what).entrySet()) {
            copy.put(text(entry.getKey(), "key of " + what), notNull(entry.getValue(), "value of " + what));
        }
        return Collections.unmodifiableMap(copy);
    }
}
