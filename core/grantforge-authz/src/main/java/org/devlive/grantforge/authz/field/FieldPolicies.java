// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.field;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.devlive.grantforge.authz.application.AuthorizationEvaluator;
import org.devlive.grantforge.authz.application.AuthorizationVersions;
import org.devlive.grantforge.authz.domain.FieldPolicy;
import org.devlive.grantforge.authz.domain.FieldPolicyRepository;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.persistence.secured.FieldMode;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldRules;
import org.devlive.grantforge.persistence.secured.FieldView;
import org.devlive.grantforge.persistence.secured.FieldWriteMode;
import org.devlive.grantforge.persistence.secured.MaskStrategy;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Works out how readers see and writers change the secured fields from the field policies of their active roles
 * (inherited ones included). Of the roles that mention a field, the most revealing policy wins: visible before masked
 * before hidden, and of masks the one listed first; the field may change if any of them lets it. A field no role mentions
 * is visible and editable, and the tenant and platform administrators see and change every field. What a reader sees is
 * kept until the tenant's permissions change.
 */
@Service
public final class FieldPolicies
        implements FieldRules
{
    /** How long a reader's fields are kept at most. */
    static final Duration MAX_AGE = Duration.ofMinutes(10);

    private static final Set<String> SEE_EVERYTHING = Set.of(SystemRole.PLATFORM_ADMIN.code(), SystemRole.TENANT_ADMIN.code());

    private final AuthorizationEvaluator evaluator;
    private final AuthorizationVersions versions;
    private final RoleRepository roles;
    private final FieldPolicyRepository policies;
    private final TransactionTemplate transactions;
    private final Cache<Key, Cached> cache = Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(MAX_AGE).build();

    /**
     * Creates the rules.
     *
     * @param evaluator the readers' active roles
     * @param versions the permission versions, to know when what is kept is out of date
     * @param roles the roles
     * @param policies the field policies
     * @param transactionManager opens transactions
     */
    public FieldPolicies(AuthorizationEvaluator evaluator, AuthorizationVersions versions, RoleRepository roles,
            FieldPolicyRepository policies, PlatformTransactionManager transactionManager)
    {
        this.evaluator = requireNonNull(evaluator, "evaluator");
        this.versions = requireNonNull(versions, "versions");
        this.roles = requireNonNull(roles, "roles");
        this.policies = requireNonNull(policies, "policies");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        transactions.setReadOnly(true);
    }

    /**
     * Returns how a reader sees a field; without a bound tenant there is no reader to limit, and the field is visible.
     *
     * @param accountId the reader
     * @param entity the entity's code
     * @param field the field's code
     * @return the view
     */
    @Override
    public FieldView read(long accountId, String entity, String field)
    {
        OptionalLong tenant = TenantContext.currentTenantId();
        if (tenant.isEmpty()) {
            return FieldView.VISIBLE;
        }
        return fields(accountId, tenant.getAsLong()).getOrDefault(key(entity, field), FieldMode.OPEN).view();
    }

    /**
     * Returns whether a writer may change a field; without a bound tenant there is no writer to limit, and it may.
     *
     * @param accountId the writer
     * @param entity the entity's code
     * @param field the field's code
     * @return the write mode
     */
    @Override
    public FieldWriteMode write(long accountId, String entity, String field)
    {
        OptionalLong tenant = TenantContext.currentTenantId();
        if (tenant.isEmpty()) {
            return FieldWriteMode.EDITABLE;
        }
        return fields(accountId, tenant.getAsLong()).getOrDefault(key(entity, field), FieldMode.OPEN).write();
    }

    /**
     * Returns the fields a reader does not see and change freely; none without a bound tenant.
     *
     * @param accountId the reader
     * @return their modes, by entity and field code joined with a dot
     */
    @Override
    public Map<String, FieldMode> restricted(long accountId)
    {
        OptionalLong tenant = TenantContext.currentTenantId();
        if (tenant.isEmpty()) {
            return Map.of();
        }
        return fields(accountId, tenant.getAsLong()).entrySet().stream().filter(entry -> !entry.getValue().equals(FieldMode.OPEN))
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String key(String entity, String field)
    {
        return entity + "." + field;
    }

    private Map<String, FieldMode> fields(long accountId, long tenant)
    {
        return requireNonNull(transactions.execute(status -> {
            AuthorizationVersions.Versions current = versions.current(tenant);
            Key key = new Key(tenant, accountId);
            Cached cached = cache.getIfPresent(key);
            if (cached != null && cached.versions().equals(current)) {
                return cached.fields();
            }
            Map<String, FieldMode> computed = compute(accountId);
            cache.put(key, new Cached(computed, current));
            return computed;
        }));
    }

    private Map<String, FieldMode> compute(long accountId)
    {
        Set<String> roleCodes = Set.copyOf(evaluator.snapshot(accountId).roles());
        if (roleCodes.isEmpty() || roleCodes.stream().anyMatch(SEE_EVERYTHING::contains)) {
            return Map.of();
        }
        List<Long> roleIds = roles.findByCodeIn(roleCodes).stream().filter(Role::isEnabled).map(Role::requireId).toList();
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        return policies.findByRoleIdIn(roleIds).stream()
                .collect(Collectors.groupingBy(policy -> key(policy.getEntityCode(), policy.getFieldCode())))
                .entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> merge(entry.getValue())));
    }

    /** How the policies one field has among a reader's roles combine. */
    static FieldMode merge(List<FieldPolicy> mentions)
    {
        boolean editable = mentions.isEmpty() || mentions.stream().anyMatch(policy -> policy.getWriteMode() == FieldWriteMode.EDITABLE);
        FieldWriteMode write = editable ? FieldWriteMode.EDITABLE : FieldWriteMode.READONLY;
        return new FieldMode(view(mentions), write);
    }

    /** The most revealing of the policies one field has among a reader's roles. */
    private static FieldView view(List<FieldPolicy> mentions)
    {
        FieldReadMode mode = mentions.stream().map(FieldPolicy::getReadMode).min(Comparator.naturalOrder()).orElse(FieldReadMode.VISIBLE);
        if (mode != FieldReadMode.MASKED) {
            return mode == FieldReadMode.VISIBLE ? FieldView.VISIBLE : FieldView.HIDDEN;
        }
        MaskStrategy mask = mentions.stream().map(FieldPolicy::getMaskStrategy).filter(Objects::nonNull).min(Comparator.naturalOrder())
                .orElse(MaskStrategy.FULL);
        return FieldView.masked(mask);
    }

    /** A reader in a tenant. */
    private record Key(long tenant, long accountId)
    {
    }

    /** How a reader sees and changes the fields, with the versions it was worked out at. */
    private record Cached(Map<String, FieldMode> fields, AuthorizationVersions.Versions versions)
    {
    }
}
