// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.service.policy;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.plugin.api.model.PolicyType;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.devlive.grantforge.plugin.host.PluginRegistry;
import org.devlive.grantforge.service.ServiceErrorCode;
import org.devlive.grantforge.service.domain.ManagedService;
import org.devlive.grantforge.service.domain.ManagedServiceRepository;
import org.devlive.grantforge.service.domain.ServicePolicy;
import org.devlive.grantforge.service.domain.ServicePolicyRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * The policies of the services of the bound tenant: listing, adding, changing and removing them. Policies are checked
 * against their service's type and must name existing users, groups and roles; every change counts towards the
 * service's policy version. Every method must be called with the actor's tenant bound.
 */
@Service
public final class PolicyAdministration
{
    /** Most names {@link #subjects} suggests. */
    public static final int MAX_SUGGESTIONS = 50;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> LABELS = new TypeReference<>()
    {
    };

    private final ServicePolicyRepository policies;
    private final ManagedServiceRepository services;
    private final PluginRegistry plugins;
    private final PolicySubjects subjects;
    private final AuditLog audit;
    private final TransactionTemplate transactions;

    /**
     * Creates the service.
     *
     * @param policies the policies of the bound tenant
     * @param services the services of the bound tenant
     * @param plugins the service types, to check policies against
     * @param subjects the users, groups and roles policies may name
     * @param audit records every change
     * @param transactionManager opens transactions
     */
    public PolicyAdministration(ServicePolicyRepository policies, ManagedServiceRepository services, PluginRegistry plugins,
            PolicySubjects subjects, AuditLog audit, PlatformTransactionManager transactionManager)
    {
        this.policies = requireNonNull(policies, "policies");
        this.services = requireNonNull(services, "services");
        this.plugins = requireNonNull(plugins, "plugins");
        this.subjects = requireNonNull(subjects, "subjects");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Lists the policies of a kind of a service.
     *
     * @param serviceId the service
     * @param type the kind
     * @return the policies, by name
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown service
     */
    public List<PolicyView> list(long serviceId, PolicyType type)
    {
        return requireNonNull(transactions.execute(status -> {
            requireService(serviceId);
            return policies.findByServiceIdAndPolicyTypeOrderByNameAsc(serviceId, type).stream().map(PolicyAdministration::view).toList();
        }));
    }

    /**
     * Returns a policy.
     *
     * @param id the policy
     * @return the policy
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public PolicyView find(long id)
    {
        return requireNonNull(transactions.execute(status -> view(require(id))));
    }

    /**
     * Adds a policy to a service.
     *
     * @param actorId the account asking
     * @param serviceId the service
     * @param type what kind of policy it is
     * @param command the policy
     * @return the policy
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}, {@link ServiceErrorCode#TYPE_UNAVAILABLE},
     *         {@link ServiceErrorCode#POLICY_INVALID} listing the inputs at fault or {@link ServiceErrorCode#POLICY_NAME_TAKEN}
     */
    public PolicyView create(long actorId, long serviceId, PolicyType type, PolicyCommand command)
    {
        ManagedService service = requireNonNull(transactions.execute(status -> requireService(serviceId)));
        check(definition(service), type, command);
        PolicyView created = write(() -> {
            ManagedService current = requireService(serviceId);
            requireFreeName(serviceId, command.name(), null);
            ServicePolicy policy = ServicePolicy.create(serviceId, type);
            describe(policy, command);
            current.policiesChanged();
            services.save(current);
            return view(policies.saveAndFlush(policy));
        });
        record(AuditAction.POLICY_CREATED, actorId, created.id(), service.getName() + ": " + created.name());
        return created;
    }

    /**
     * Changes a policy; its service and kind stay.
     *
     * @param actorId the account asking
     * @param id the policy
     * @param expectedVersion the version the change was made on, or {@code null} to change whatever is stored
     * @param command the policy
     * @return the policy
     * @throws GrantForgeException as {@link #create}, or with {@link CommonErrorCode#CONFLICT} if the policy changed since
     *         the expected version
     */
    public PolicyView update(long actorId, long id, @Nullable Long expectedVersion, PolicyCommand command)
    {
        ServicePolicy stored = requireNonNull(transactions.execute(status -> require(id)));
        ManagedService service = requireNonNull(transactions.execute(status -> requireService(stored.getServiceId())));
        check(definition(service), stored.getPolicyType(), command);
        PolicyView updated = write(() -> {
            ServicePolicy policy = require(id);
            if (expectedVersion != null && !expectedVersion.equals(policy.getVersion())) {
                throw new GrantForgeException(CommonErrorCode.CONFLICT, "policy " + id + " changed since version " + expectedVersion);
            }
            requireFreeName(policy.getServiceId(), command.name(), id);
            describe(policy, command);
            ManagedService current = requireService(policy.getServiceId());
            current.policiesChanged();
            services.save(current);
            return view(policies.saveAndFlush(policy));
        });
        record(AuditAction.POLICY_UPDATED, actorId, id, service.getName() + ": " + updated.name());
        return updated;
    }

    /**
     * Removes a policy.
     *
     * @param actorId the account asking
     * @param id the policy
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public void delete(long actorId, long id)
    {
        String reason = write(() -> {
            ServicePolicy policy = require(id);
            ManagedService service = requireService(policy.getServiceId());
            policies.delete(policy);
            service.policiesChanged();
            services.save(service);
            return service.getName() + ": " + policy.getName();
        });
        record(AuditAction.POLICY_DELETED, actorId, id, reason);
    }

    /**
     * Suggests users, groups or roles for policy items.
     *
     * @param kind what to suggest
     * @param text what was typed; blank for any
     * @param limit how many at most; at most {@value #MAX_SUGGESTIONS}
     * @return the names
     */
    public List<String> subjects(SubjectKind kind, String text, int limit)
    {
        return requireNonNull(transactions.execute(status -> subjects.suggest(kind, text.strip(),
                Math.max(1, Math.min(limit, MAX_SUGGESTIONS)))));
    }

    /** Checks a policy against the service type and the subjects it names; throws with every problem found. */
    private void check(ServiceTypeDefinition definition, PolicyType type, PolicyCommand command)
    {
        List<FieldIssue> issues = new ArrayList<>(PolicyRules.check(definition, type, command));
        PolicyDocument document = command.document();
        Map<String, List<PolicyItemSpec>> lists = new LinkedHashMap<>();
        lists.put("allow", document.allow());
        lists.put("allowExceptions", document.allowExceptions());
        lists.put("deny", document.deny());
        lists.put("denyExceptions", document.denyExceptions());
        transactions.executeWithoutResult(status -> lists.forEach((list, items) -> {
            for (int index = 0; index < items.size(); index++) {
                PolicyItemSpec item = items.get(index);
                String field = list + "[" + index + "]";
                unknown(issues, field + ".users", SubjectKind.USER, item.users());
                unknown(issues, field + ".groups", SubjectKind.GROUP,
                        item.groups().stream().filter(group -> !PolicyItemSpec.PUBLIC.equals(group)).toList());
                unknown(issues, field + ".roles", SubjectKind.ROLE, item.roles());
            }
        }));
        if (!issues.isEmpty()) {
            throw new GrantForgeException(ServiceErrorCode.POLICY_INVALID, issues.size() + " policy issues").withFieldIssues(issues);
        }
    }

    private void unknown(List<FieldIssue> issues, String field, SubjectKind kind, List<String> names)
    {
        if (names.isEmpty()) {
            return;
        }
        Set<String> missing = subjects.unknown(kind, names);
        if (!missing.isEmpty()) {
            issues.add(FieldIssue.of(field, "error.policy.subject-unknown",
                    String.join(", ", names.stream().filter(missing::contains).toList())));
        }
    }

    private ServiceTypeDefinition definition(ManagedService service)
    {
        return plugins.serviceType(service.getServiceType()).orElseThrow(() -> new GrantForgeException(
                ServiceErrorCode.TYPE_UNAVAILABLE, "no active plugin provides " + service.getServiceType(), service.getServiceType()));
    }

    private void requireFreeName(long serviceId, String name, @Nullable Long except)
    {
        policies.findByServiceIdAndName(serviceId, name).filter(other -> except == null || other.requireId() != except).ifPresent(other -> {
            throw new GrantForgeException(ServiceErrorCode.POLICY_NAME_TAKEN, "policy name taken", name);
        });
    }

    private ManagedService requireService(long id)
    {
        return services.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no service " + id));
    }

    private ServicePolicy require(long id)
    {
        return policies.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no policy " + id));
    }

    private static void describe(ServicePolicy policy, PolicyCommand command)
    {
        policy.describe(command.name(), command.description(), command.priority(), command.enabled(),
                JSON.writeValueAsString(command.labels()), JSON.writeValueAsString(command.document()));
    }

    private static PolicyView view(ServicePolicy policy)
    {
        return new PolicyView(policy.requireId(), policy.getServiceId(), policy.getName(), policy.getDescription(),
                policy.getPolicyType(), policy.getPriority(), policy.isEnabled(), read(policy.getLabels(), json -> JSON.readValue(json, LABELS)),
                read(policy.getBody(), json -> JSON.readValue(json, PolicyDocument.class)),
                requireNonNullElse(policy.getVersion(), 0L), requireNonNullElse(policy.getUpdatedAt(), Instant.EPOCH));
    }

    private static <T> T read(String json, Function<String, T> parse)
    {
        try {
            return parse.apply(json);
        }
        catch (JacksonException broken) {
            throw new IllegalStateException("stored policy is not valid JSON", broken);
        }
    }

    private <T> T write(Supplier<T> change)
    {
        try {
            return requireNonNull(transactions.execute(status -> change.get()));
        }
        catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException race) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "policy changed concurrently", race);
        }
    }

    private void record(AuditAction action, long actorId, long policyId, String reason)
    {
        audit.record(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(policyId), reason));
    }
}
