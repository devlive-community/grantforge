// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.devlive.grantforge.common.lang.Strings;
import org.jspecify.annotations.Nullable;

/** Checks the free texts of catalog entries. */
final class CatalogText
{
    private CatalogText()
    {
    }

    static String name(@Nullable String value, int max)
    {
        String trimmed = Strings.requireNonBlank(value, "name");
        if (trimmed.length() > max) {
            throw new IllegalArgumentException("name must be at most " + max + " characters");
        }
        return trimmed;
    }

    static @Nullable String optional(@Nullable String value, int max, String what)
    {
        String trimmed = Strings.blankToNull(value);
        if (trimmed != null && trimmed.length() > max) {
            throw new IllegalArgumentException(what + " must be at most " + max + " characters");
        }
        return trimmed;
    }
}
