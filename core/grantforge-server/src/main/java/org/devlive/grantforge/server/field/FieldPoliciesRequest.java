// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.field;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.devlive.grantforge.authz.field.FieldPolicyCommand;
import org.devlive.grantforge.authz.field.FieldPolicyService;

import java.util.List;

/**
 * Every field policy of a role, replacing those it has.
 *
 * @param policies the policies, one per field at most; none removes every policy of the role
 */
public record FieldPoliciesRequest(@Size(max = FieldPolicyService.MAX_POLICIES) List<@Valid FieldPolicyRequest> policies)
{
    /** Copies the list; JSON without it gives {@code null}, which means none. */
    @SuppressWarnings("ConstantValue")
    public FieldPoliciesRequest
    {
        policies = policies == null ? List.of() : List.copyOf(policies);
    }

    /**
     * Converts the validated request.
     *
     * @return the commands
     */
    public List<FieldPolicyCommand> toCommands()
    {
        return policies.stream().map(FieldPolicyRequest::toCommand).toList();
    }
}
