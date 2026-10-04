// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import org.devlive.grantforge.persistence.secured.DataField;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * An entity of an application as the application declares it.
 *
 * @param code the entity's own code, without the application's
 * @param name what its rows are
 * @param owned whether rows belong to an account, so "own rows" applies
 * @param unitBased whether rows belong to a department, so department scopes apply
 * @param fields the fields conditions may test
 */
public record EntityDeclaration(String code, String name, boolean owned, boolean unitBased, List<DataField> fields)
{
    /** Checks and copies the parts. */
    public EntityDeclaration
    {
        requireNonNull(code, "code");
        requireNonNull(name, "name");
        fields = List.copyOf(fields);
    }
}
