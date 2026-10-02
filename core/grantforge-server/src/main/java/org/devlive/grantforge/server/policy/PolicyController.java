// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.policy;

import jakarta.validation.Valid;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.devlive.grantforge.service.policy.PolicyAdministration;
import org.devlive.grantforge.service.policy.SubjectKind;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** The policies of the services of the caller's tenant: what they cover and whom they allow, deny, mask or filter. */
@RestController
public final class PolicyController
{
    private final PolicyAdministration policies;

    /**
     * Creates the controller.
     *
     * @param policies the policies
     */
    public PolicyController(PolicyAdministration policies)
    {
        this.policies = requireNonNull(policies, "policies");
    }

    /**
     * Lists the policies of a kind of a service.
     *
     * @param id the service
     * @param type the kind; access policies unless given
     * @return the policies, by name
     */
    @RequirePermission("data.policy.read")
    @GetMapping("/api/v1/services/{id}/policies")
    public List<PolicyResponse> list(@PathVariable String id, @RequestParam(defaultValue = "ACCESS") PolicyType type)
    {
        return policies.list(PathIds.parse(id, "service"), type).stream().map(PolicyResponse::from).toList();
    }

    /**
     * Returns a policy.
     *
     * @param id the policy
     * @return the policy
     */
    @RequirePermission("data.policy.read")
    @GetMapping("/api/v1/policies/{id}")
    public PolicyResponse find(@PathVariable String id)
    {
        return PolicyResponse.from(policies.find(PathIds.parse(id, "policy")));
    }

    /**
     * Adds a policy to a service.
     *
     * @param user the session's principal
     * @param id the service
     * @param body the policy
     * @return the policy
     */
    @RequirePermission("data.policy.create")
    @PostMapping("/api/v1/services/{id}/policies")
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyResponse create(@AuthenticationPrincipal SessionUser user, @PathVariable String id, @Valid @RequestBody PolicyRequest body)
    {
        return PolicyResponse.from(policies.create(user.accountId(), PathIds.parse(id, "service"), body.kind(), body.command()));
    }

    /**
     * Changes a policy.
     *
     * @param user the session's principal
     * @param id the policy
     * @param body the policy, with the version it was changed on
     * @return the policy
     */
    @RequirePermission("data.policy.update")
    @PutMapping("/api/v1/policies/{id}")
    public PolicyResponse update(@AuthenticationPrincipal SessionUser user, @PathVariable String id, @Valid @RequestBody PolicyRequest body)
    {
        return PolicyResponse.from(policies.update(user.accountId(), PathIds.parse(id, "policy"), body.version(), body.command()));
    }

    /**
     * Removes a policy.
     *
     * @param user the session's principal
     * @param id the policy
     */
    @RequirePermission("data.policy.delete")
    @DeleteMapping("/api/v1/policies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        policies.delete(user.accountId(), PathIds.parse(id, "policy"));
    }

    /**
     * Suggests users, groups or roles for policy items.
     *
     * @param kind what to suggest
     * @param text what was typed
     * @param limit how many at most
     * @return the names
     */
    @RequirePermission("data.policy.read")
    @GetMapping("/api/v1/policy-subjects")
    public List<String> subjects(@RequestParam SubjectKind kind, @RequestParam(defaultValue = "") String text,
            @RequestParam(defaultValue = "20") int limit)
    {
        return policies.subjects(kind, text, limit);
    }
}
