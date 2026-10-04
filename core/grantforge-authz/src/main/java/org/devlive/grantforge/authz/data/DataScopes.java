// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.devlive.grantforge.authz.application.AuthorizationEvaluator;
import org.devlive.grantforge.authz.application.AuthorizationVersions;
import org.devlive.grantforge.authz.domain.ApplicationEntity;
import org.devlive.grantforge.authz.domain.DataPolicy;
import org.devlive.grantforge.authz.domain.DataPolicyRepository;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleHierarchy;
import org.devlive.grantforge.authz.domain.RoleParentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SystemRole;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.FieldIssue;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.AccountPositionRepository;
import org.devlive.grantforge.identity.domain.GroupMember;
import org.devlive.grantforge.identity.domain.GroupMemberRepository;
import org.devlive.grantforge.identity.domain.OrgMember;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.Position;
import org.devlive.grantforge.identity.domain.PositionRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserGroup;
import org.devlive.grantforge.identity.domain.UserGroupRepository;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.devlive.grantforge.persistence.secured.RowScopes;
import org.devlive.grantforge.persistence.secured.SecuredEntities;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Works out which rows readers may use: the data policies of their active roles (inherited ones included) and what system
 * roles imply, the tenant administrator every row of the tenant and the platform administrator every row. What a reader may
 * use is kept until the tenant's permissions change, as for the console permissions. Every method must be called with the
 * reader's tenant bound.
 */
@Service
public final class DataScopes
        implements RowScopes
{
    /** How long a reader's rules are kept at most. */
    static final Duration MAX_AGE = Duration.ofMinutes(10);

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Long>> IDS = new TypeReference<>()
    {
    };

    private final AuthorizationEvaluator evaluator;
    private final AuthorizationVersions versions;
    private final Sources sources;
    private final SecuredEntities entities;
    private final DataEntities directory;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final EntityManager entityManager;
    private final Cache<Key, Cached> cache = Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(MAX_AGE).build();

    /**
     * Creates the service.
     *
     * @param evaluator the readers' active roles
     * @param versions the permission versions, to know when what is kept is out of date
     * @param sources where readers and policies are read from
     * @param entities the secured entities
     * @param directory every entity policies may name, applications' too
     * @param entityManagerFactory counts rows for previews
     * @param transactionManager opens transactions
     * @param clock the current time
     */
    public DataScopes(AuthorizationEvaluator evaluator, AuthorizationVersions versions, Sources sources, SecuredEntities entities,
            DataEntities directory, EntityManagerFactory entityManagerFactory, PlatformTransactionManager transactionManager, Clock clock)
    {
        this.evaluator = requireNonNull(evaluator, "evaluator");
        this.versions = requireNonNull(versions, "versions");
        this.sources = requireNonNull(sources, "sources");
        this.entities = requireNonNull(entities, "entities");
        this.directory = requireNonNull(directory, "directory");
        this.entityManager = SharedEntityManagerCreator.createSharedEntityManager(requireNonNull(entityManagerFactory, "entityManagerFactory"));
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        transactions.setReadOnly(true);
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Returns the reader and what their roles say about data.
     *
     * @param accountId the reader
     * @return the access
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} if the account does not exist
     */
    public DataAccess access(long accountId)
    {
        long tenant = TenantContext.requireTenantId();
        return requireNonNull(transactions.execute(status -> {
            AuthorizationVersions.Versions current = versions.current(tenant);
            Key key = new Key(tenant, accountId);
            Cached cached = cache.getIfPresent(key);
            if (cached != null && cached.versions().equals(current)) {
                return cached.access();
            }
            DataAccess computed = compute(accountId, tenant);
            cache.put(key, new Cached(computed, current));
            return computed;
        }));
    }

    /**
     * Returns what a reader's roles say about the data entities of an application, for the application to apply itself:
     * the rules under the entities' own codes, and the departments the reader belongs to with every department below them,
     * as the application cannot walk GrantForge's department tree.
     *
     * @param accountId the reader
     * @param applicationCode the application's code
     * @return the access
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} if the account does not exist
     */
    public ApplicationAccess accessIn(long accountId, String applicationCode)
    {
        DataAccess access = access(accountId);
        String prefix = applicationCode + ApplicationEntity.SEPARATOR;
        Map<DataAccess.Key, DataAccess.Rules> rules = new HashMap<>();
        access.rules().forEach((key, value) -> {
            if (key.entityCode().startsWith(prefix)) {
                rules.put(new DataAccess.Key(key.entityCode().substring(prefix.length()), key.action()), value);
            }
        });
        List<Long> below = requireNonNull(transactions.execute(status -> sources.unitsBelow(access.subject().orgUnitPaths())));
        return new ApplicationAccess(access.subject(), below, rules);
    }

    /**
     * Returns the rows of an entity a reader may use for an action.
     *
     * @param accountId the reader
     * @param type the entity class, a {@link org.devlive.grantforge.persistence.secured.SecuredEntity}
     * @param action the action
     * @param <T> the entity class
     * @return the scope, to combine with the query's own filters
     * @throws IllegalArgumentException if the class is not a secured entity
     */
    @Override
    public <T> Specification<T> scope(long accountId, Class<T> type, DataAction action)
    {
        SecuredEntityDefinition entity = entities.find(type)
                .orElseThrow(() -> new IllegalArgumentException(type.getName() + " is not a secured entity"));
        DataAccess access = access(accountId);
        return DataScopeSpecifications.of(entity, access.rules(entity.code(), action), access.subject(), clock.instant());
    }

    /**
     * Counts the rows of an entity a user would see for an action with only one role, its inherited roles included, and
     * those they see now, to try a role's data policies before assigning it.
     *
     * @param roleId the role
     * @param accountId the user
     * @param entityCode the entity
     * @param action the action
     * @return both counts
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown role, account or entity
     */
    public DataPreview preview(long roleId, long accountId, String entityCode, DataAction action)
    {
        SecuredEntityDefinition entity = directory.find(entityCode)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no secured entity " + entityCode));
        if (DataEntities.ofAnApplication(entity)) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, "rows of " + entityCode + " live in their application")
                    .withFieldIssues(List.of(FieldIssue.of("entityCode", "error.data.preview-console-only")));
        }
        DataAccess current = access(accountId);
        Instant now = clock.instant();
        return requireNonNull(transactions.execute(status -> {
            Sources.Chain chain = sources.chain(roleId);
            DataAccess alone = new DataAccess(current.subject(), rules(chain.codes(), chain.policies()));
            return new DataPreview(count(entity, alone, action, now), count(entity, current, action, now));
        }));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private long count(SecuredEntityDefinition entity, DataAccess access, DataAction action, Instant now)
    {
        Specification specification = DataScopeSpecifications.of(entity, access.rules(entity.code(), action), access.subject(), now);
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root root = query.from(entity.type());
        query.select(builder.count(root)).where(specification.toPredicate(root, query, builder));
        return entityManager.createQuery(query).getSingleResult();
    }

    private DataAccess compute(long accountId, long tenant)
    {
        DataSubject subject = sources.subject(accountId, tenant);
        Set<String> roleCodes = Set.copyOf(evaluator.snapshot(accountId).roles());
        return new DataAccess(subject, rules(roleCodes, sources.policies(roleCodes)));
    }

    /** The rules of roles: what system roles among them imply, and their data policies. */
    private Map<DataAccess.Key, DataAccess.Rules> rules(Set<String> roleCodes, List<DataPolicy> rolePolicies)
    {
        Map<DataAccess.Key, List<DataRule>> allow = new HashMap<>();
        Map<DataAccess.Key, List<DataRule>> deny = new HashMap<>();
        DataScope implied = roleCodes.contains(SystemRole.PLATFORM_ADMIN.code()) ? DataScope.ALL
                : roleCodes.contains(SystemRole.TENANT_ADMIN.code()) ? DataScope.TENANT : null;
        if (implied != null) {
            // System roles reach the console's own entities only, never an application's.
            for (SecuredEntityDefinition entity : directory.console()) {
                for (DataAction action : DataAction.values()) {
                    add(allow, entity.code(), action, DataRule.of(implied));
                }
            }
        }
        Map<String, SecuredEntityDefinition> known = rolePolicies.isEmpty() ? Map.of() : directory.all();
        for (DataPolicy policy : rolePolicies) {
            SecuredEntityDefinition entity = known.get(policy.getEntityCode());
            if (entity == null) {
                continue;
            }
            DataRule rule = rule(policy, entity);
            boolean denying = policy.getEffect() == GrantEffect.DENY;
            if (rule == null) {
                // A condition the entity no longer fits: it allows nothing, and denies everything rather than nothing.
                if (!denying) {
                    continue;
                }
                rule = DataRule.of(DataScope.ALL);
            }
            add(denying ? deny : allow, entity.code(), policy.getAction(), rule);
        }
        Set<DataAccess.Key> keys = new HashSet<>(allow.keySet());
        keys.addAll(deny.keySet());
        return keys.stream().collect(Collectors.toMap(Function.identity(),
                key -> new DataAccess.Rules(allow.getOrDefault(key, List.of()), deny.getOrDefault(key, List.of()))));
    }

    private static void add(Map<DataAccess.Key, List<DataRule>> rules, String entityCode, DataAction action, DataRule rule)
    {
        rules.computeIfAbsent(new DataAccess.Key(entityCode, action), key -> new ArrayList<>()).add(rule);
    }

    /** The rule of a policy, or {@code null} if its stored condition no longer fits the entity. */
    private static @Nullable DataRule rule(DataPolicy policy, SecuredEntityDefinition entity)
    {
        String stored = policy.getCondition();
        Condition condition = null;
        if (stored != null) {
            condition = ConditionCodec.read(stored, entity, "condition").condition();
            if (condition == null) {
                return null;
            }
        }
        return new DataRule(policy.getScope(), condition, ids(policy.getOrgUnitIds()));
    }

    private static List<Long> ids(@Nullable String json)
    {
        if (json == null) {
            return List.of();
        }
        try {
            return JSON.readValue(json, IDS);
        }
        catch (JacksonException broken) {
            throw new IllegalStateException("stored department ids are not valid JSON", broken);
        }
    }

    /** A reader in a tenant. */
    private record Key(long tenant, long accountId)
    {
    }

    /** What a reader may use, with the versions it was worked out at. */
    private record Cached(DataAccess access, AuthorizationVersions.Versions versions)
    {
    }

    /** Where readers and their roles' policies are read from. */
    @Service
    public static final class Sources
    {
        private final UserAccountRepository accounts;
        private final OrgMemberRepository orgMembers;
        private final OrgUnitRepository units;
        private final GroupMemberRepository groupMembers;
        private final UserGroupRepository groups;
        private final AccountPositionRepository holdings;
        private final PositionRepository positions;
        private final RoleRepository roles;
        private final RoleParentRepository parents;
        private final DataPolicyRepository policies;

        /**
         * Creates the sources.
         *
         * @param accounts the accounts
         * @param orgMembers department memberships
         * @param units the departments
         * @param groupMembers group memberships
         * @param groups the groups
         * @param holdings positions held
         * @param positions the positions
         * @param roles the roles
         * @param parents which roles inherit from which, for previews
         * @param policies the data policies
         */
        public Sources(UserAccountRepository accounts, OrgMemberRepository orgMembers, OrgUnitRepository units,
                GroupMemberRepository groupMembers, UserGroupRepository groups, AccountPositionRepository holdings,
                PositionRepository positions, RoleRepository roles, RoleParentRepository parents, DataPolicyRepository policies)
        {
            this.accounts = requireNonNull(accounts, "accounts");
            this.orgMembers = requireNonNull(orgMembers, "orgMembers");
            this.units = requireNonNull(units, "units");
            this.groupMembers = requireNonNull(groupMembers, "groupMembers");
            this.groups = requireNonNull(groups, "groups");
            this.holdings = requireNonNull(holdings, "holdings");
            this.positions = requireNonNull(positions, "positions");
            this.roles = requireNonNull(roles, "roles");
            this.parents = requireNonNull(parents, "parents");
            this.policies = requireNonNull(policies, "policies");
        }

        DataSubject subject(long accountId, long tenant)
        {
            UserAccount account = accounts.findById(accountId)
                    .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no account " + accountId));
            List<Long> unitIds = orgMembers.findByAccount(accountId).stream().map(OrgMember::getOrgUnitId).distinct().sorted().toList();
            List<String> paths = unitIds.isEmpty() ? List.of() : units.findAllById(unitIds).stream().map(OrgUnit::getPath).sorted().toList();
            List<Long> groupIds = groupMembers.findByAccountId(accountId).stream().map(GroupMember::getGroupId).toList();
            List<String> groupCodes = groupIds.isEmpty() ? List.of()
                    : groups.findAllById(groupIds).stream().map(UserGroup::getCode).sorted().toList();
            List<Long> positionIds = holdings.findByAccountId(accountId).stream().map(holding -> holding.getPositionId()).toList();
            List<String> positionCodes = positionIds.isEmpty() ? List.of()
                    : positions.findAllById(positionIds).stream().map(Position::getCode).sorted().toList();
            return new DataSubject(accountId, tenant, account.getUsername(), unitIds, paths, groupCodes, positionCodes);
        }

        /** The departments at or below some paths, of the bound tenant. */
        List<Long> unitsBelow(List<String> paths)
        {
            if (paths.isEmpty()) {
                return List.of();
            }
            return units.findTree().stream().filter(unit -> paths.stream().anyMatch(path -> unit.getPath().startsWith(path)))
                    .map(OrgUnit::requireId).sorted().toList();
        }

        /** A role with the enabled roles it inherits from, and their data policies. */
        Chain chain(long roleId)
        {
            Role role = roles.findById(roleId).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no role " + roleId));
            Set<Long> ids = new HashSet<>(Set.of(roleId));
            ids.addAll(new RoleHierarchy(parents.findAll()).ancestors(roleId).keySet());
            List<Role> chained = roles.findAllById(ids).stream().filter(found -> found.requireId() == role.requireId() || found.isEnabled())
                    .toList();
            Set<String> codes = chained.stream().map(Role::getCode).collect(Collectors.toSet());
            return new Chain(codes, policies.findByRoleIdIn(chained.stream().map(Role::requireId).toList()));
        }

        /**
         * Roles and their data policies.
         *
         * @param codes the roles' codes
         * @param policies their policies
         */
        record Chain(Set<String> codes, List<DataPolicy> policies)
        {
        }

        List<DataPolicy> policies(Set<String> roleCodes)
        {
            if (roleCodes.isEmpty()) {
                return List.of();
            }
            List<Long> roleIds = roles.findByCodeIn(roleCodes).stream().filter(Role::isEnabled).map(Role::requireId).toList();
            return roleIds.isEmpty() ? List.of() : policies.findByRoleIdIn(roleIds);
        }
    }
}
