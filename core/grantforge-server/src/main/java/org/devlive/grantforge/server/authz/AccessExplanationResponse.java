// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import org.devlive.grantforge.authz.application.AccessExplanation;
import org.devlive.grantforge.authz.application.AccessKind;
import org.devlive.grantforge.authz.application.Subject;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Why an account may or may not use something.
 *
 * @param accountId the account
 * @param kind a console resource or an API permission
 * @param code its code
 * @param outcome {@code ALLOWED}, {@code DENIED}, {@code DISABLED}, {@code NOT_GRANTED} or {@code UNKNOWN}
 * @param name the resource's name, absent if the catalog does not know it
 * @param paths every way the account's roles allow it, one per role that does
 * @param denials the roles that deny it
 */
public record AccessExplanationResponse(String accountId, AccessKind kind, String code, AccessExplanation.Outcome outcome,
        @Nullable String name, List<Path> paths, List<Denial> denials)
{
    /** Copies the lists. */
    public AccessExplanationResponse
    {
        paths = List.copyOf(paths);
        denials = List.copyOf(denials);
    }

    /**
     * Converts an explanation.
     *
     * @param accountId the account
     * @param explanation the explanation
     * @return the response
     */
    public static AccessExplanationResponse from(long accountId, AccessExplanation explanation)
    {
        return new AccessExplanationResponse(Long.toString(accountId), explanation.kind(), explanation.code(), explanation.outcome(),
                explanation.name(), explanation.paths().stream().map(Path::from).toList(),
                explanation.denials().stream().map(denial -> new Denial(denial.roleCode(), denial.roleName(), denial.resourceCode()))
                        .toList());
    }

    /**
     * One way the account's roles allow it.
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

        static Path from(AccessExplanation.Path path)
        {
            return new Path(path.roles().stream().map(role -> new PathRole(role.code(), role.name(),
                    role.assignedTo().stream().map(Holder::from).toList())).toList(),
                    path.resources().stream().map(resource -> new PathResource(resource.code(), resource.name(), resource.type(),
                            resource.via())).toList());
        }
    }

    /**
     * A role on a path.
     *
     * @param code its code
     * @param name its name
     * @param assignedTo for the role held, whom it is assigned to; empty for inherited roles
     */
    public record PathRole(String code, String name, List<Holder> assignedTo)
    {
        /** Copies the list. */
        public PathRole
        {
            assignedTo = List.copyOf(assignedTo);
        }
    }

    /**
     * Whom a role is assigned to: the account, or a group, department or position it belongs to.
     *
     * @param type what it is
     * @param id its ID
     * @param name its name
     * @param detail its login name or code, if any
     */
    public record Holder(SubjectType type, String id, String name, @Nullable String detail)
    {
        static Holder from(Subject subject)
        {
            return new Holder(subject.type(), Long.toString(subject.id()), subject.name(), subject.detail());
        }
    }

    /**
     * A resource on a path.
     *
     * @param code its code
     * @param name its name
     * @param type its type
     * @param via {@code GRANT}, {@code SYSTEM_ROLE}, {@code ANCESTOR} (above the one before) or {@code DEPENDENCY}
     *        (required by the one before)
     */
    public record PathResource(String code, String name, ResourceType type, AccessExplanation.Via via)
    {
    }

    /**
     * A role that denies it.
     *
     * @param roleCode the role's code
     * @param roleName the role's name
     * @param resourceCode the resource the role is denied
     */
    public record Denial(String roleCode, String roleName, String resourceCode)
    {
    }
}
