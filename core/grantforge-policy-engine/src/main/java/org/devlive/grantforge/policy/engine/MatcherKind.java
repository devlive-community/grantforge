// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

/** How a policy's values for one resource level are compared with a requested value; {@code *} always matches. */
public enum MatcherKind
{
    /** Equal values only. */
    EXACT,

    /** {@code *} matches any run of characters and {@code ?} one character. */
    WILDCARD,

    /**
     * Slash-separated paths: {@code *} matches within one segment and {@code ?} one character of it; a recursive
     * value also covers everything below it. Trailing slashes do not count.
     */
    PATH,

    /** A regular expression the whole value must match. */
    REGEX
}
