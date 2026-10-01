// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.role;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.RoleAssignmentService;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** Who has which role in the signed-in user's tenant. */
@RestController
public final class RoleAssignmentController
{
    private final RoleAssignmentService assignments;

    /**
     * Creates the controller.
     *
     * @param assignments the tenant's role assignments
     */
    public RoleAssignmentController(RoleAssignmentService assignments)
    {
        this.assignments = requireNonNull(assignments, "assignments");
    }

    /**
     * Lists who has a role.
     *
     * @param user the session's principal
     * @param id the role
     * @return the assignments
     */
    @RequirePermission("system.role.read")
    @GetMapping("/api/v1/roles/{id}/assignments")
    public List<AssignmentResponse> list(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return assignments.list(user.accountId(), PathIds.parse(id, "role")).stream().map(AssignmentResponse::from).toList();
    }

    /**
     * Gives a role to an account, group, department or position.
     *
     * @param user the session's principal
     * @param id the role
     * @param body who gets it, how long and how widely
     * @return the assignment
     */
    @RequirePermission("system.role.assign")
    @PostMapping("/api/v1/roles/{id}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResponse assign(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody AssignmentRequest body)
    {
        return AssignmentResponse.from(assignments.assign(user.accountId(), PathIds.parse(id, "role"),
                requireNonNull(body.subjectType(), "subjectType"), PathIds.parse(String.valueOf(body.subjectId()).trim(), "subject"),
                body.terms()));
    }

    /**
     * Changes how long and how widely an assignment applies.
     *
     * @param user the session's principal
     * @param id the assignment
     * @param body the new terms
     * @return the assignment
     */
    @RequirePermission("system.role.assign")
    @PutMapping("/api/v1/role-assignments/{id}")
    public AssignmentResponse change(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody AssignmentTermsRequest body)
    {
        return AssignmentResponse.from(assignments.change(user.accountId(), PathIds.parse(id, "assignment"), body.terms()));
    }

    /**
     * Takes a role from its subject.
     *
     * @param user the session's principal
     * @param id the assignment
     */
    @RequirePermission("system.role.assign")
    @DeleteMapping("/api/v1/role-assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        assignments.remove(user.accountId(), PathIds.parse(id, "assignment"));
    }

    /**
     * Returns the roles an account has, directly and through its groups, departments and positions.
     *
     * @param user the session's principal
     * @param id the account
     * @return the roles, active ones first
     */
    @RequirePermission("system.user.read")
    @GetMapping("/api/v1/users/{id}/roles")
    public List<EffectiveRoleResponse> rolesOf(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        return assignments.rolesOf(user.accountId(), PathIds.parse(id, "account")).stream().map(EffectiveRoleResponse::from)
                .toList();
    }
}
