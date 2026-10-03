// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.field;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.field.FieldPolicyService;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** How holders of roles see and change the secured fields. */
@RestController
public final class FieldPolicyController
{
    private final FieldPolicyService policies;

    /**
     * Creates the controller.
     *
     * @param policies the field policies
     */
    public FieldPolicyController(FieldPolicyService policies)
    {
        this.policies = requireNonNull(policies, "policies");
    }

    /**
     * Returns a role's field policies.
     *
     * @param id the role
     * @return the policies, by entity and field
     */
    @RequirePermission("system.role.read")
    @GetMapping("/api/v1/roles/{id}/field-policies")
    public List<FieldPolicyResponse> fieldPolicies(@PathVariable String id)
    {
        return policies.list(PathIds.parse(id, "role")).stream().map(FieldPolicyResponse::from).toList();
    }

    /**
     * Replaces a role's field policies.
     *
     * @param user the session's principal
     * @param id the role
     * @param body the new policies
     * @return the policies
     */
    @RequirePermission("system.role.data")
    @PutMapping("/api/v1/roles/{id}/field-policies")
    public List<FieldPolicyResponse> replace(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody FieldPoliciesRequest body)
    {
        return policies.replace(user.accountId(), PathIds.parse(id, "role"), body.toCommands()).stream()
                .map(FieldPolicyResponse::from).toList();
    }
}
