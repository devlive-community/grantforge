// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.redis.agent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * One user's ACL on one node, as {@code ACL GETUSER} shows it: its flags, its root permissions, which the operator keeps,
 * and its selectors, which the agent keeps.
 *
 * @param flags such as {@code on} and {@code nopass}
 * @param root the root permissions
 * @param selectors the selectors
 */
record UserAcl(Set<String> flags, Selector root, List<Selector> selectors)
{
    /** Copies everything. */
    UserAcl
    {
        flags = Set.copyOf(flags);
        selectors = List.copyOf(selectors);
    }

    /**
     * Reads what {@code ACL GETUSER} answers, a list of names and values.
     *
     * @param answer the answer
     * @return the user's ACL
     */
    static UserAcl of(@Nullable Object answer)
    {
        Map<String, Object> fields = pairs(answer);
        List<Selector> selectors = new ArrayList<>();
        if (fields.get("selectors") instanceof List<?> list) {
            for (Object each : list) {
                Map<String, Object> selector = pairs(each);
                selectors.add(Selector.shown(text(selector.get("commands")), text(selector.get("keys")), text(selector.get("channels"))));
            }
        }
        Set<String> flags = new TreeSet<>();
        if (fields.get("flags") instanceof List<?> list) {
            list.forEach(flag -> flags.add(String.valueOf(flag)));
        }
        return new UserAcl(flags, Selector.shown(text(fields.get("commands")), text(fields.get("keys")), text(fields.get("channels"))), selectors);
    }

    /**
     * Returns whether no selector or root permission binds the user: every command on every key.
     *
     * @return whether it may do anything
     */
    boolean superuser()
    {
        return root.commands().contains("+@all") && (root.keys().contains("~*") || root.keys().contains("%RW~*"));
    }

    /**
     * Returns whether anyone may sign in as the user without a password.
     *
     * @return whether it is on and needs no password
     */
    boolean open()
    {
        return flags.contains("on") && flags.contains("nopass");
    }

    private static Map<String, Object> pairs(@Nullable Object answer)
    {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (answer instanceof List<?> list) {
            for (int index = 0; index + 1 < list.size(); index += 2) {
                Object value = list.get(index + 1);
                if (value != null) {
                    fields.put(String.valueOf(list.get(index)), value);
                }
            }
        }
        return fields;
    }

    private static String text(@Nullable Object value)
    {
        if (value instanceof List<?> list) {
            List<String> words = new ArrayList<>();
            list.forEach(word -> words.add(String.valueOf(word)));
            return String.join(" ", words);
        }
        return value == null ? "" : value.toString();
    }
}
