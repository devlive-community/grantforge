// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Whom a policy item is about and what it says: the access types it allows or denies, or for masking and row filtering
 * policies how it masks or filters. The group {@value #PUBLIC} stands for everyone.
 *
 * @param users user names
 * @param groups group codes
 * @param roles role codes
 * @param accessTypes the access types
 * @param conditions conditions that must all hold
 * @param maskType how a masking item masks; only for masking policies
 * @param maskValue the expression of a custom masking, if the mask type takes one
 * @param rowFilter the filter of a row filtering item, in the target system's language
 */
public record PolicyItemSpec(List<String> users, List<String> groups, List<String> roles, List<String> accessTypes,
        List<ConditionValues> conditions, @Nullable String maskType, @Nullable String maskValue, @Nullable String rowFilter)
{
    /** The group every user belongs to. */
    public static final String PUBLIC = "public";

    /** Tidies the names. */
    public PolicyItemSpec
    {
        users = List.copyOf(Texts.of(users));
        groups = List.copyOf(Texts.of(groups));
        roles = List.copyOf(Texts.of(roles));
        accessTypes = List.copyOf(Texts.of(accessTypes));
        conditions = List.copyOf(Texts.list(conditions));
        maskType = Texts.optional(maskType);
        maskValue = Texts.optional(maskValue);
        rowFilter = Texts.optional(rowFilter);
    }

    /**
     * Describes an access item.
     *
     * @param users user names
     * @param groups group codes
     * @param accessTypes the access types
     * @return the item
     */
    public static PolicyItemSpec access(List<String> users, List<String> groups, List<String> accessTypes)
    {
        return new PolicyItemSpec(users, groups, List.of(), accessTypes, List.of(), null, null, null);
    }
}
