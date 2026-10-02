// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.agent;

import jakarta.validation.Valid;
import org.devlive.grantforge.common.security.RequirePermission;
import org.devlive.grantforge.server.security.SessionUser;
import org.devlive.grantforge.server.web.PathIds;
import org.devlive.grantforge.service.agent.AgentRegistry;
import org.devlive.grantforge.service.agent.AgentTokens;
import org.devlive.grantforge.service.agent.SnapshotSigner;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Objects.requireNonNull;

/** The console's view of agents: their tokens, how each agent is doing, and the key snapshots are signed with. */
@RestController
public final class AgentAdministrationController
{
    private final AgentTokens tokens;
    private final AgentRegistry registry;
    private final SnapshotSigner signer;

    /**
     * Creates the controller.
     *
     * @param tokens the agent tokens
     * @param registry the agents
     * @param signer the signing key
     */
    public AgentAdministrationController(AgentTokens tokens, AgentRegistry registry, SnapshotSigner signer)
    {
        this.tokens = requireNonNull(tokens, "tokens");
        this.registry = requireNonNull(registry, "registry");
        this.signer = requireNonNull(signer, "signer");
    }

    /**
     * Lists the agents of a service.
     *
     * @param id the service
     * @return the agents by name
     */
    @RequirePermission("data.agent.read")
    @GetMapping("/api/v1/services/{id}/agents")
    public List<AgentResponse> agents(@PathVariable String id)
    {
        return registry.list(PathIds.parse(id, "service")).stream().map(AgentResponse::from).toList();
    }

    /**
     * Forgets an agent; it registers again with its next heartbeat.
     *
     * @param id the service
     * @param agentId the agent
     */
    @RequirePermission("data.agent.manage")
    @DeleteMapping("/api/v1/services/{id}/agents/{agentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forget(@PathVariable String id, @PathVariable String agentId)
    {
        registry.forget(PathIds.parse(id, "service"), PathIds.parse(agentId, "agent"));
    }

    /**
     * Lists the agent tokens of a service.
     *
     * @param id the service
     * @return the tokens, newest first
     */
    @RequirePermission("data.agent.read")
    @GetMapping("/api/v1/services/{id}/agent-tokens")
    public List<AgentTokenResponse> tokens(@PathVariable String id)
    {
        return tokens.list(PathIds.parse(id, "service")).stream().map(AgentTokenResponse::from).toList();
    }

    /**
     * Issues an agent token.
     *
     * @param user the session's principal
     * @param id the service
     * @param body the token's name and expiry
     * @return the token with its secret, shown this once
     */
    @RequirePermission("data.agent.manage")
    @PostMapping("/api/v1/services/{id}/agent-tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedTokenResponse issue(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @Valid @RequestBody AgentTokenRequest body)
    {
        return IssuedTokenResponse.from(tokens.issue(user.accountId(), PathIds.parse(id, "service"), String.valueOf(body.name()),
                body.expiresAt()));
    }

    /**
     * Revokes an agent token.
     *
     * @param user the session's principal
     * @param id the token
     */
    @RequirePermission("data.agent.manage")
    @PostMapping("/api/v1/agent-tokens/{id}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal SessionUser user, @PathVariable String id)
    {
        tokens.revoke(user.accountId(), PathIds.parse(id, "agent token"));
    }

    /**
     * Returns the public key policy snapshots are signed with, to configure agents with.
     *
     * @return the key
     */
    @RequirePermission("data.agent.read")
    @GetMapping("/api/v1/policy-signing-key")
    public SigningKeyResponse signingKey()
    {
        return SigningKeyResponse.from(signer.publicKey());
    }
}
