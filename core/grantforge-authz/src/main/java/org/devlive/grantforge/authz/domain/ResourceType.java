// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.domain;

import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a resource of the catalog stands for. The type decides where a resource may sit in the tree, so that
 * modules group menus and pages, pages hold their tabs and buttons, and fields belong to a data entity.
 */
public enum ResourceType
{
    /** A group of menus, pages, APIs or data entities, such as "System". */
    MODULE,

    /** A navigation entry that groups pages. */
    MENU,

    /** A screen the user opens, such as the user list. */
    PAGE,

    /** A tab within a page. */
    TAB,

    /** A button or other operation on a page or tab. */
    ACTION,

    /** A server endpoint, the actual security boundary. */
    API,

    /** A kind of business record whose rows are filtered by data permissions. */
    DATA_ENTITY,

    /** A field of a data entity, shown, masked or hidden by field permissions. */
    FIELD;

    /**
     * Returns whether a resource of this type may sit below a parent of the given type.
     *
     * @param parent the parent's type, or {@code null} for the top level
     * @return {@code true} if the placement is allowed
     */
    public boolean allowsParent(@Nullable ResourceType parent)
    {
        return switch (this) {
            case MODULE -> parent == null || parent == MODULE;
            case MENU, PAGE -> parent == null || parent == MODULE || parent == MENU;
            case TAB -> parent == PAGE || parent == TAB;
            case ACTION -> parent == PAGE || parent == TAB;
            case API, DATA_ENTITY -> parent == null || parent == MODULE;
            case FIELD -> parent == DATA_ENTITY;
        };
    }

    /**
     * Returns the types that may sit below a resource of this type.
     *
     * @return the child types, in declaration order
     */
    public Set<ResourceType> childTypes()
    {
        Set<ResourceType> types = EnumSet.noneOf(ResourceType.class);
        for (ResourceType type : values()) {
            if (type.allowsParent(this)) {
                types.add(type);
            }
        }
        return types;
    }
}
