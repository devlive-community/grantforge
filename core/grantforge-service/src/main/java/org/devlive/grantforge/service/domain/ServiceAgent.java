// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.devlive.grantforge.persistence.tenant.TenantScopedEntity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * An agent of a service, as its last heartbeat described it: where it runs, which version it is and which policy
 * version it applies.
 */
@Entity
@Table(name = "gf_agent")
public class ServiceAgent
        extends TenantScopedEntity
{
    @Column(name = "service_id", nullable = false, updatable = false)
    private long serviceId;

    @Column(name = "instance", nullable = false, updatable = false, length = 128)
    private String instance = "";

    @Column(name = "token_id")
    private @Nullable Long tokenId;

    @Column(name = "host", length = 255)
    private @Nullable String host;

    @Column(name = "agent_version", length = 64)
    private @Nullable String agentVersion;

    @Column(name = "applied_policy_version")
    private @Nullable Long appliedPolicyVersion;

    @Column(name = "client_ip", length = 64)
    private @Nullable String clientIp;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.EPOCH;

    /** For JPA. */
    protected ServiceAgent()
    {
    }

    /**
     * Registers an agent.
     *
     * @param serviceId the service it enforces the policies of
     * @param instance the name the agent gives itself, unique within the service
     * @return the agent; report its heartbeat before saving
     */
    public static ServiceAgent register(long serviceId, String instance)
    {
        ServiceAgent agent = new ServiceAgent();
        agent.serviceId = serviceId;
        agent.instance = requireNonNull(instance, "instance");
        return agent;
    }

    /**
     * Records a heartbeat.
     *
     * @param token the token the agent signed in with
     * @param newHost where the agent runs
     * @param version the agent's version
     * @param applied the policy version it applies, or {@code null} if it has none yet
     * @param ip the address the heartbeat came from
     * @param now when
     */
    public void heartbeat(long token, @Nullable String newHost, @Nullable String version, @Nullable Long applied, @Nullable String ip,
            Instant now)
    {
        this.tokenId = token;
        this.host = newHost;
        this.agentVersion = version;
        this.appliedPolicyVersion = applied;
        this.clientIp = ip;
        this.lastSeenAt = requireNonNull(now, "now");
    }

    /**
     * Returns the service.
     *
     * @return the service's id
     */
    public long getServiceId()
    {
        return serviceId;
    }

    /**
     * Returns the name the agent gives itself.
     *
     * @return the instance name
     */
    public String getInstance()
    {
        return instance;
    }

    /**
     * Returns the token the agent last signed in with.
     *
     * @return the token's id, or {@code null}
     */
    public @Nullable Long getTokenId()
    {
        return tokenId;
    }

    /**
     * Returns where the agent runs.
     *
     * @return the host, or {@code null}
     */
    public @Nullable String getHost()
    {
        return host;
    }

    /**
     * Returns the agent's version.
     *
     * @return the version, or {@code null}
     */
    public @Nullable String getAgentVersion()
    {
        return agentVersion;
    }

    /**
     * Returns the policy version the agent applies.
     *
     * @return the version, or {@code null} if it has none yet
     */
    public @Nullable Long getAppliedPolicyVersion()
    {
        return appliedPolicyVersion;
    }

    /**
     * Returns the address the last heartbeat came from.
     *
     * @return the address, or {@code null}
     */
    public @Nullable String getClientIp()
    {
        return clientIp;
    }

    /**
     * Returns when the agent last reported.
     *
     * @return the moment
     */
    public Instant getLastSeenAt()
    {
        return lastSeenAt;
    }
}
