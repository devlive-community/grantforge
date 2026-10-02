// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.agent;

import jakarta.persistence.criteria.Predicate;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.service.domain.AccessEvent;
import org.devlive.grantforge.service.domain.AccessEventRepository;
import org.devlive.grantforge.service.domain.ManagedServiceRepository;
import org.devlive.grantforge.service.domain.ServicePolicy;
import org.devlive.grantforge.service.domain.ServicePolicyRepository;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * The access events agents report: stored in batches, each event once however often a batch is sent, read per service
 * newest first a page at a time, and purged after the retention period ({@code grantforge.access-audit.retention}, 90
 * days unless configured).
 */
@Service
public final class AccessAudit
{
    /** Most events in one batch. */
    public static final int MAX_BATCH = 1000;

    /** Most events in one page. */
    public static final int MAX_PAGE = 200;

    /** How many old events one purge round removes. */
    static final int PURGE_BATCH = 500;

    private static final Logger LOG = LoggerFactory.getLogger(AccessAudit.class);
    private static final String SORT_TIME = "occurredAt";

    private final AccessEventRepository events;
    private final ManagedServiceRepository services;
    private final ServicePolicyRepository policies;
    private final TransactionTemplate transactions;
    private final Duration retention;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param events the access events
     * @param services the services of the bound tenant
     * @param policies their policies, to name deciding policies
     * @param transactionManager opens transactions
     * @param retention how long events are kept
     * @param clock the current time
     * @throws IllegalArgumentException if the retention period is not positive
     */
    public AccessAudit(AccessEventRepository events, ManagedServiceRepository services, ServicePolicyRepository policies,
            PlatformTransactionManager transactionManager, @Value("${grantforge.access-audit.retention:90d}") Duration retention,
            Clock clock)
    {
        this.events = requireNonNull(events, "events");
        this.services = requireNonNull(services, "services");
        this.policies = requireNonNull(policies, "policies");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        if (retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("grantforge.access-audit.retention must be positive");
        }
        this.retention = retention;
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Stores a batch of events an agent reported; events stored before are skipped, so agents may send a batch again
     * when they are unsure it arrived. Must be called with the agent's tenant bound.
     *
     * @param agent the agent's credential
     * @param agentInstance the name the agent gives itself
     * @param batch the events, at most {@value #MAX_BATCH}, each named uniquely by the agent
     * @return what became of them
     * @throws IllegalArgumentException if the batch is too large
     */
    public Ingested record(AgentCredential agent, String agentInstance, List<AccessEvent.Fields> batch)
    {
        if (batch.size() > MAX_BATCH) {
            throw new IllegalArgumentException("at most " + MAX_BATCH + " events per batch");
        }
        Instant oldest = clock.instant().minus(retention);
        Map<String, AccessEvent.Fields> fresh = new LinkedHashMap<>();
        int expired = 0;
        for (AccessEvent.Fields fields : batch) {
            if (fields.occurredAt().isBefore(oldest)) {
                expired++;
            }
            else {
                fresh.putIfAbsent(fields.eventId(), fields);
            }
        }
        int repeated = batch.size() - expired - fresh.size();
        try {
            return store(agent, agentInstance, fresh, repeated, expired);
        }
        catch (DataIntegrityViolationException concurrent) {
            // The same batch arrived twice at once; the second attempt skips what the first stored.
            return store(agent, agentInstance, fresh, repeated, expired);
        }
    }

    private Ingested store(AgentCredential agent, String agentInstance, Map<String, AccessEvent.Fields> fresh, int repeated, int expired)
    {
        return requireNonNull(transactions.execute(status -> {
            if (!services.existsById(agent.serviceId())) {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no service " + agent.serviceId());
            }
            Set<String> known = fresh.isEmpty() ? Set.of() : new HashSet<>(events.findKnownEventIds(agent.serviceId(), fresh.keySet()));
            List<AccessEvent> rows = fresh.values().stream().filter(fields -> !known.contains(fields.eventId()))
                    .map(fields -> AccessEvent.of(agent.serviceId(), agentInstance, fields)).toList();
            events.saveAllAndFlush(rows);
            return new Ingested(rows.size(), repeated + known.size(), expired);
        }));
    }

    /**
     * Reads the events of a service, newest first. Must be called with the tenant bound.
     *
     * @param serviceId the service
     * @param query the filters
     * @param limit how many at most; at most {@value #MAX_PAGE}
     * @param cursor where to go on from, as an earlier page's {@link AccessPage#next()} returned, or {@code null} to start
     * @return the events and the cursor of the next page
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown service or
     *         {@link CommonErrorCode#BAD_REQUEST} for a malformed cursor
     */
    public AccessPage search(long serviceId, AccessQuery query, int limit, @Nullable String cursor)
    {
        int size = Math.max(1, Math.min(limit, MAX_PAGE));
        Position after = cursor == null || cursor.isBlank() ? null : Position.decode(cursor);
        return requireNonNull(transactions.execute(status -> {
            if (!services.existsById(serviceId)) {
                throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no service " + serviceId);
            }
            Specification<AccessEvent> where = matching(serviceId, query, after);
            List<AccessEvent> found = events.findBy(where, fluent -> fluent.sortBy(Sort.by(Sort.Order.desc(SORT_TIME), Sort.Order.desc("id")))
                    .limit(size + 1).all());
            List<AccessEvent> page = found.subList(0, Math.min(size, found.size()));
            Set<Long> policyIds = page.stream().map(event -> event.fields().policyId()).filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, String> names = policyIds.isEmpty() ? Map.of() : policies.findAllById(policyIds).stream()
                    .filter(policy -> policy.getServiceId() == serviceId)
                    .collect(Collectors.toMap(ServicePolicy::requireId, ServicePolicy::getName));
            List<AccessEventView> views = page.stream().map(event -> new AccessEventView(event.requireId(), event.getAgentInstance(),
                    event.fields(), policyName(names, event.fields().policyId()))).toList();
            AccessEvent last = page.isEmpty() ? null : page.get(page.size() - 1);
            return new AccessPage(views, found.size() > size && last != null ? new Position(last.getOccurredAt(), last.requireId()).encode()
                    : null);
        }));
    }

    /**
     * Purges the events of every tenant older than the retention period, a batch at a time.
     *
     * @return how many were removed
     */
    public int purge()
    {
        Instant before = clock.instant().minus(retention);
        int removed = 0;
        int round;
        do {
            round = requireNonNull(TenantContext.callAsSystem(() -> transactions.execute(status -> {
                List<Long> ids = events.findIdsBefore(before, PageRequest.of(0, PURGE_BATCH));
                return ids.isEmpty() ? 0 : events.removeAll(ids);
            })));
            removed += round;
        }
        while (round == PURGE_BATCH);
        if (removed > 0) {
            LOG.info("Purged {} access events older than {}", removed, before);
        }
        return removed;
    }

    private static @Nullable String policyName(Map<Long, String> names, @Nullable Long policyId)
    {
        return policyId == null ? null : names.get(policyId);
    }

    private static Specification<AccessEvent> matching(long serviceId, AccessQuery query, @Nullable Position after)
    {
        return (root, criteria, builder) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(builder.equal(root.get("serviceId"), serviceId));
            String user = blankToNull(query.user());
            if (user != null) {
                where.add(builder.like(builder.lower(root.get("userName")), contains(user)));
            }
            String resource = blankToNull(query.resource());
            if (resource != null) {
                where.add(builder.like(builder.lower(root.get("resourcePath")), contains(resource)));
            }
            String accessType = blankToNull(query.accessType());
            if (accessType != null) {
                where.add(builder.equal(root.get("accessType"), accessType));
            }
            if (query.outcome() != null) {
                where.add(builder.equal(root.get("outcome"), query.outcome()));
            }
            if (query.from() != null) {
                where.add(builder.greaterThanOrEqualTo(root.get(SORT_TIME), query.from()));
            }
            if (query.until() != null) {
                where.add(builder.lessThan(root.get(SORT_TIME), query.until()));
            }
            if (after != null) {
                where.add(builder.or(builder.lessThan(root.get(SORT_TIME), after.occurredAt()),
                        builder.and(builder.equal(root.get(SORT_TIME), after.occurredAt()), builder.lessThan(root.get("id"), after.id()))));
            }
            return builder.and(where.toArray(Predicate[]::new));
        };
    }

    private static @Nullable String blankToNull(@Nullable String value)
    {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String contains(String text)
    {
        return "%" + text.toLowerCase(Locale.ROOT).replace("\\", "").replace("%", "").replace("_", "") + "%";
    }

    /** Where a page ended: the time and id of its last event. */
    record Position(Instant occurredAt, long id)
    {
        String encode()
        {
            return Base64.getUrlEncoder().withoutPadding().encodeToString((occurredAt + "~" + id).getBytes(StandardCharsets.UTF_8));
        }

        static Position decode(String cursor)
        {
            try {
                String text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int split = text.indexOf('~');
                return new Position(Instant.parse(text.substring(0, split)), Long.parseLong(text.substring(split + 1)));
            }
            catch (IllegalArgumentException | DateTimeParseException | IndexOutOfBoundsException malformed) {
                throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, "malformed cursor", malformed);
            }
        }
    }
}
