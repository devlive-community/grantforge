// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.authz;

import jakarta.validation.Valid;
import org.devlive.grantforge.authz.application.AccessCheck;
import org.devlive.grantforge.authz.application.AuthorizationInsight;
import org.devlive.grantforge.authz.application.EffectiveAccess;
import org.devlive.grantforge.authz.data.DataScopes;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.persistence.secured.FieldRules;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** Whether accounts may use console resources and API permissions, and why. */
@RestController
@RequestMapping("/api/v1/authz")
public final class AuthorizationController
{
    private final AuthorizationInsight insight;
    private final DataScopes data;
    private final FieldRules fields;

    /**
     * Creates the controller.
     *
     * @param insight answers and explains
     * @param data what accounts' roles say about data
     * @param fields how accounts see and change the secured fields
     */
    public AuthorizationController(AuthorizationInsight insight, DataScopes data, FieldRules fields)
    {
        this.insight = requireNonNull(insight, "insight");
        this.data = requireNonNull(data, "data");
        this.fields = requireNonNull(fields, "fields");
    }

    /**
     * Lists everything an account may use now: roles, console resources, API permissions, data rules and restricted fields.
     *
     * @param user the session's principal
     * @param body the account
     * @return the access
     */
    @RequirePermission("system.authz.check")
    @PostMapping("/effective")
    public EffectiveAccessResponse effectiveAccess(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody EffectiveAccessRequest body)
    {
        long account = account(user, body.accountId());
        EffectiveAccess access = insight.effective(user.accountId(), account);
        return EffectiveAccessResponse.from(account, access, data.access(account), fields.restricted(account));
    }

    /**
     * Answers whether an account may use each of some resources and permissions now.
     *
     * @param user the session's principal
     * @param body the account and the questions
     * @return the answers
     */
    @RequirePermission("system.authz.check")
    @PostMapping("/check")
    public AccessResultsResponse checkAccess(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody AccessChecksRequest body)
    {
        long account = account(user, body.accountId());
        List<AccessCheck> checks = body.checks().stream().map(AccessCheckRequest::toCheck).toList();
        return AccessResultsResponse.from(account, insight.check(user.accountId(), account, checks));
    }

    /**
     * Explains whether an account may use a resource or permission now: every role path that allows it and every denial.
     *
     * @param user the session's principal
     * @param body the account and the question
     * @return the explanation
     */
    @RequirePermission("system.authz.explain")
    @PostMapping("/explain")
    public AccessExplanationResponse explainAccess(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody AccessExplainRequest body)
    {
        long account = account(user, body.accountId());
        return AccessExplanationResponse.from(account, insight.explain(user.accountId(), account, body.check().toCheck()));
    }

    /**
     * Works out what an account would gain and lose if some changes were made, without making them.
     *
     * @param user the session's principal
     * @param body the account and the changes
     * @return the differences
     */
    @RequirePermission("system.authz.simulate")
    @PostMapping("/simulate")
    public SimulationResponse simulateAccess(@AuthenticationPrincipal SessionUser user, @Valid @RequestBody SimulationRequest body)
    {
        long account = account(user, body.accountId());
        return SimulationResponse.from(account, insight.simulate(user.accountId(), account, body.toSimulation()));
    }

    private static long account(SessionUser user, @Nullable String accountId)
    {
        return accountId == null ? user.accountId() : PathIds.parse(accountId, "account");
    }
}
