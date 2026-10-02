// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** One policy value of one resource level, compiled once: decides whether a (normalized) requested value matches. */
abstract class ValueMatcher
{
    /** Matches every value. */
    static final ValueMatcher ANY = new ValueMatcher("")
    {
        @Override
        boolean matches(String value)
        {
            return true;
        }
    };

    private final String literalPrefix;

    ValueMatcher(String literalPrefix)
    {
        this.literalPrefix = literalPrefix;
    }

    /**
     * Returns what every matching value starts with, for the prefix index; empty when nothing is certain.
     *
     * @return the prefix, normalized like requested values
     */
    final String literalPrefix()
    {
        return literalPrefix;
    }

    abstract boolean matches(String value);

    /**
     * Normalizes a value of a level, as policy values and requested values are compared: lower case for
     * case-insensitive levels, and paths without trailing slashes.
     */
    static String normalize(ResourceLevel level, String value)
    {
        String normalized = level.caseSensitive() ? value : value.toLowerCase(Locale.ROOT);
        if (level.matcher() == MatcherKind.PATH) {
            int end = normalized.length();
            while (end > 1 && normalized.charAt(end - 1) == '/') {
                end--;
            }
            normalized = normalized.substring(0, end);
        }
        return normalized;
    }

    static ValueMatcher compile(ResourceLevel level, String value, boolean recursive)
    {
        if (ResourceSpec.ANY.equals(value)) {
            return ANY;
        }
        if (recursive && level.matcher() != MatcherKind.PATH) {
            throw new IllegalArgumentException("only path levels can be recursive, not " + level.name());
        }
        String normalized = normalize(level, value);
        switch (level.matcher()) {
            case EXACT:
                return new Exact(normalized);
            case WILDCARD:
                return new Glob(normalized, glob(normalized, ".*", "."), false);
            case PATH:
                return new Glob(normalized, glob(normalized, "[^/]*", "[^/]"), recursive);
            default:
                return regex(level, value);
        }
    }

    static String prefix(String normalized)
    {
        int end = 0;
        while (end < normalized.length() && normalized.charAt(end) != '*' && normalized.charAt(end) != '?') {
            end++;
        }
        return normalized.substring(0, end);
    }

    private static Pattern glob(String normalized, String many, String one)
    {
        StringBuilder regex = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (current == '*' || current == '?') {
                if (literal.length() > 0) {
                    regex.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(current == '*' ? many : one);
            }
            else {
                literal.append(current);
            }
        }
        if (literal.length() > 0) {
            regex.append(Pattern.quote(literal.toString()));
        }
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }

    private static ValueMatcher regex(ResourceLevel level, String value)
    {
        final Pattern pattern;
        try {
            pattern = Pattern.compile(value, level.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }
        catch (PatternSyntaxException invalid) {
            throw new IllegalArgumentException("not a regular expression for level " + level.name() + ": " + value, invalid);
        }
        return new ValueMatcher("")
        {
            @Override
            boolean matches(String requested)
            {
                return pattern.matcher(requested).matches();
            }
        };
    }

    /** Equal values only. */
    private static final class Exact
            extends ValueMatcher
    {
        private final String value;

        Exact(String value)
        {
            super(value);
            this.value = value;
        }

        @Override
        boolean matches(String requested)
        {
            return value.equals(requested);
        }
    }

    /** A glob over the whole value; a recursive path glob also matches every path below a matching one. */
    private static final class Glob
            extends ValueMatcher
    {
        private final Pattern pattern;
        private final boolean recursive;

        Glob(String normalized, Pattern pattern, boolean recursive)
        {
            super(prefix(normalized));
            this.pattern = pattern;
            this.recursive = recursive;
        }

        @Override
        boolean matches(String requested)
        {
            if (pattern.matcher(requested).matches()) {
                return true;
            }
            if (!recursive) {
                return false;
            }
            // A recursive path covers its descendants: some ancestor of the requested path must match.
            for (int slash = requested.lastIndexOf('/'); slash >= 0; slash = requested.lastIndexOf('/', slash - 1)) {
                String ancestor = slash == 0 ? "/" : requested.substring(0, slash);
                if (pattern.matcher(ancestor).matches()) {
                    return true;
                }
                if (slash == 0) {
                    break;
                }
            }
            return false;
        }
    }
}
