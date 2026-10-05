// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.legacy;

import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * What the pre-rebuild database holds that GrantForge takes over: accounts, roles, menus with their HTTP methods, and
 * who holds which role and which role may use which menu. Icons, menu types, logs and settings are left behind.
 *
 * @param users the accounts
 * @param roles the roles
 * @param menus the menus, pages and buttons
 * @param methods the HTTP methods
 * @param userRoles which account holds which role
 * @param roleMenus which role may use which menu
 * @param menuMethods which HTTP methods a menu's URL answers
 */
public record LegacyData(List<User> users, List<Role> roles, List<Menu> menus, List<Method> methods, List<Link> userRoles,
        List<Link> roleMenus, List<Link> menuMethods)
{
    /** Copies the lists. */
    public LegacyData
    {
        users = List.copyOf(requireNonNull(users, "users"));
        roles = List.copyOf(requireNonNull(roles, "roles"));
        menus = List.copyOf(requireNonNull(menus, "menus"));
        methods = List.copyOf(requireNonNull(methods, "methods"));
        userRoles = List.copyOf(requireNonNull(userRoles, "userRoles"));
        roleMenus = List.copyOf(requireNonNull(roleMenus, "roleMenus"));
        menuMethods = List.copyOf(requireNonNull(menuMethods, "menuMethods"));
    }

    /**
     * An account.
     *
     * @param id its ID in the old database
     * @param name its login name
     * @param password its unsalted SHA-256 hex digest, if any
     * @param active whether it was enabled
     * @param locked whether it was locked
     * @param system whether it was a system account
     * @param email its e-mail address, if any
     */
    public record User(long id, @Nullable String name, @Nullable String password, boolean active, boolean locked, boolean system,
            @Nullable String email)
    {
    }

    /**
     * A role.
     *
     * @param id its ID in the old database
     * @param name its name
     * @param code its code
     * @param description its description
     * @param active whether it was enabled
     */
    public record Role(long id, @Nullable String name, @Nullable String code, @Nullable String description, boolean active)
    {
    }

    /**
     * A menu, page or button: {@code #} as URL marks a folder.
     *
     * @param id its ID in the old database
     * @param name its name
     * @param url its URL, {@code #} for a folder
     * @param sorted its position among its siblings
     * @param parent its parent's ID; {@code null} or 0 at the top
     * @param description its description
     * @param active whether it was enabled
     */
    public record Menu(long id, @Nullable String name, @Nullable String url, int sorted, @Nullable Long parent, @Nullable String description,
            boolean active)
    {
    }

    /**
     * An HTTP method.
     *
     * @param id its ID in the old database
     * @param method the method, such as GET
     */
    public record Method(long id, @Nullable String method)
    {
    }

    /**
     * A row of a relation table.
     *
     * @param from the first ID (user, role or menu)
     * @param to the second ID (role, menu or method)
     */
    public record Link(long from, long to)
    {
    }
}
