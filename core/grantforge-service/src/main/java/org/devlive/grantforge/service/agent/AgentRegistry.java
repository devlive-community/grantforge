// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.service.domain.ManagedService;
import org.devlive.grantforge.service.domain.ManagedServiceRepository;
import org.devlive.grantforge.service.domain.ServiceAgent;
import org.devlive.grantforge.service.domain.ServiceAgentRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Keeps track of the agents of the bound tenant's services: each heartbeat registers or updates the agent and tells
 * it the service's current policy version. An agent that missed {@value #MISSED_HEARTBEATS} heartbeats counts as
 * silent.
 */
@Service
public final class AgentRegistry
{
    /** Heartbeats an agent may miss before it counts as silent. */
    public static final int MISSED_HEARTBEATS = 3;

    private final ServiceAgentRepository agents;
    private final ManagedServiceRepository services;
    private final TransactionTemplate transactions;
    private final Duration refresh;
    private final Clock clock;

    /**
     * Creates the registry.
     *
     * @param agents the agents
     * @param services the services of the bound tenant
     * @param transactionManager opens transactions
     * @param refresh how often agents report and look for new policies
     * @param clock the current time
     * @throws IllegalArgumentException if the interval is not positive
     */
    public AgentRegistry(ServiceAgentRepository agents, ManagedServiceRepository services, PlatformTransactionManager transactionManager,
            @Value("${grantforge.agents.refresh-interval:30s}") Duration refresh, Clock clock)
    {
        this.agents = requireNonNull(agents, "agents");
        this.services = requireNonNull(services, "services");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        if (refresh.isNegative() || refresh.isZero()) {
            throw new IllegalArgumentException("grantforge.agents.refresh-interval must be positive");
        }
        this.refresh = refresh;
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Records a heartbeat of an agent, registering it the first time.
     *
     * @param credential who the agent is
     * @param report what it says about itself
     * @param clientIp where the heartbeat came from
     * @return the service's policy version and when to report again
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} if the service is gone
     */
    public Heartbeat heartbeat(AgentCredential credential, AgentReport report, @Nullable String clientIp)
    {
        Instant now = clock.instant();
        try {
            return record(credential, report, clientIp, now);
        }
        catch (DataIntegrityViolationException race) {
            // Two heartbeats of a new agent crossed: the other registered it, so this one updates it.
            return record(credential, report, clientIp, now);
        }
    }

    private Heartbeat record(AgentCredential credential, AgentReport report, @Nullable String clientIp, Instant now)
    {
        return requireNonNull(transactions.execute(status -> {
            ManagedService service = services.findById(credential.serviceId())
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no service " + credential.serviceId()));
            ServiceAgent agent = agents.findByServiceIdAndInstance(credential.serviceId(), report.instance())
                    .orElseGet(() -> ServiceAgent.register(credential.serviceId(), report.instance()));
            agent.heartbeat(credential.tokenId(), report.host(), report.agentVersion(), report.appliedPolicyVersion(), clientIp, now);
            agents.saveAndFlush(agent);
            return new Heartbeat(service.getPolicyVersion(), refresh.toSeconds());
        }));
    }

    /**
     * Lists the agents of a service.
     *
     * @param serviceId the service
     * @return the agents by name, with how they are doing
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public List<AgentView> list(long serviceId)
    {
        Instant silentBefore = clock.instant().minus(refresh.multipliedBy(MISSED_HEARTBEATS));
        return requireNonNull(transactions.execute(status -> {
            ManagedService service = services.findById(serviceId)
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no service " + serviceId));
            return agents.findByServiceIdOrderByInstanceAsc(serviceId).stream().map(agent -> new AgentView(agent.requireId(), serviceId,
                    agent.getInstance(), agent.getHost(), agent.getAgentVersion(), agent.getAppliedPolicyVersion(), agent.getClientIp(),
                    agent.getLastSeenAt(), status(agent, service.getPolicyVersion(), silentBefore))).toList();
        }));
    }

    /**
     * Forgets an agent, such as one that was taken out of service; it registers again with its next heartbeat.
     *
     * @param serviceId the service
     * @param agentId the registration
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public void forget(long serviceId, long agentId)
    {
        transactions.executeWithoutResult(status -> {
            ServiceAgent agent = agents.findById(agentId).filter(found -> found.getServiceId() == serviceId)
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no agent " + agentId));
            agents.delete(agent);
        });
    }

    private static AgentStatus status(ServiceAgent agent, long policyVersion, Instant silentBefore)
    {
        if (agent.getLastSeenAt().isBefore(silentBefore)) {
            return AgentStatus.SILENT;
        }
        return Objects.equals(agent.getAppliedPolicyVersion(), policyVersion) ? AgentStatus.CURRENT : AgentStatus.OUTDATED;
    }
}
