// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.ResourceType;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Why an account may or may not use something: every way one of its roles allows it, and every denial.
 *
 * @param kind a console resource or an API permission
 * @param code its code
 * @param outcome the answer
 * @param name the resource's name, or {@code null} if the catalog does not know it
 * @param paths the ways the account's roles allow it, one per role that does
 * @param denials the roles that deny it
 */
public record AccessExplanation(AccessKind kind, String code, Outcome outcome, @Nullable String name, List<Path> paths,
        List<Denial> denials)
{
    /** Copies the lists. */
    public AccessExplanation
    {
        requireNonNull(kind, "kind");
        requireNonNull(code, "code");
        requireNonNull(outcome, "outcome");
        paths = List.copyOf(paths);
        denials = List.copyOf(denials);
    }

    /** The answer. */
    public enum Outcome
    {
        /** The account may use it. */
        ALLOWED,
        /** A role of the account denies it, which wins over any allowance. */
        DENIED,
        /** It, or something above it, is disabled: it grants nothing to anyone. */
        DISABLED,
        /** No role of the account allows it. */
        NOT_GRANTED,
        /** The catalog has no such resource or permission. */
        UNKNOWN
    }

    /** How a resource on a path follows. */
    public enum Via
    {
        /** A role grants it. */
        GRANT,
        /** A system role allows the whole module it belongs to. */
        SYSTEM_ROLE,
        /** It lies above the resource before it, which the console shows the way to. */
        ANCESTOR,
        /** The resource before it requires it. */
        DEPENDENCY
    }

    /**
     * One way the account's roles allow it: from a role the account holds, through the roles it inherits from, to a
     * resource one of them is granted, and from there to the resource asked about.
     *
     * @param roles the role held first, then each role the one before inherits from; the last one allows it
     * @param resources the resource allowed first, then each that follows from the one before; the last is the one asked about
     */
    public record Path(List<PathRole> roles, List<PathResource> resources)
    {
        /** Copies the lists. */
        public Path
        {
            roles = List.copyOf(roles);
            resources = List.copyOf(resources);
        }
    }

    /**
     * A role on a path.
     *
     * @param code its code
     * @param name its name
     * @param assignedTo for the role held, whom it is assigned to (the account, its groups, departments or positions);
     *        empty for inherited roles
     */
    public record PathRole(String code, String name, List<Subject> assignedTo)
    {
        /** Copies the list. */
        public PathRole
        {
            requireNonNull(code, "code");
            requireNonNull(name, "name");
            assignedTo = List.copyOf(assignedTo);
        }
    }

    /**
     * A resource on a path.
     *
     * @param code its code
     * @param name its name
     * @param type its type
     * @param via how it follows
     */
    public record PathResource(String code, String name, ResourceType type, Via via)
    {
        /** Checks the parts. */
        public PathResource
        {
            requireNonNull(code, "code");
            requireNonNull(name, "name");
            requireNonNull(type, "type");
            requireNonNull(via, "via");
        }
    }

    /**
     * A role that denies it.
     *
     * @param roleCode the role's code
     * @param roleName the role's name
     * @param resourceCode the resource the role is denied: the one asked about or one above it
     */
    public record Denial(String roleCode, String roleName, String resourceCode)
    {
        /** Checks the parts. */
        public Denial
        {
            requireNonNull(roleCode, "roleCode");
            requireNonNull(roleName, "roleName");
            requireNonNull(resourceCode, "resourceCode");
        }
    }
}
