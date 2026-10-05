// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A page of matching accounts with the number of all matches.
 *
 * @param rows the accounts of the page, the newest first
 * @param total how many accounts match
 */
public record UserPage(List<UserRow> rows, long total)
{
    /** Copies the rows. */
    public UserPage
    {
        rows = List.copyOf(requireNonNull(rows, "rows"));
    }
}
