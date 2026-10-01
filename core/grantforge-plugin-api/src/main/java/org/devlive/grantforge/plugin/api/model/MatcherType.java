// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.plugin.api.model;

/** How a policy's values for one resource level are compared with the resource a request names. */
public enum MatcherType
{
    /** Equal values only. */
    EXACT,

    /** {@code *} matches any run of characters and {@code ?} one character. */
    WILDCARD,

    /** Slash-separated paths with wildcards; a value may also cover everything below it (recursive). */
    PATH,

    /** A regular expression the whole value must match; for types whose resources need it. */
    REGEX
}
