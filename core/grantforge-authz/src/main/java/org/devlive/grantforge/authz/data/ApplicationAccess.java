// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * What a reader's roles say about the data entities of an application, which applies the rules to its own rows.
 *
 * @param subject the reader
 * @param orgUnitsAndBelow the reader's departments and every department below them, for "my departments and below"
 * @param rules the rules by the entities' own codes, without the application's
 */
public record ApplicationAccess(DataSubject subject, List<Long> orgUnitsAndBelow, Map<DataAccess.Key, DataAccess.Rules> rules)
{
    /** Checks and copies the parts. */
    public ApplicationAccess
    {
        requireNonNull(subject, "subject");
        orgUnitsAndBelow = List.copyOf(orgUnitsAndBelow);
        rules = Map.copyOf(rules);
    }
}
