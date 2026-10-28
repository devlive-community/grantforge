// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.datasource.domain.DataSource;
import org.devlive.grantforge.datasource.domain.DataSourceRepository;
import org.devlive.grantforge.datasource.domain.DirectoryAccountRepository;
import org.devlive.grantforge.datasource.domain.DirectoryDepartment;
import org.devlive.grantforge.datasource.domain.DirectoryDepartmentRepository;
import org.devlive.grantforge.datasource.provider.DirectoryProvider;
import org.devlive.grantforge.identity.application.LoginSucceeded;
import org.devlive.grantforge.identity.application.SecretBox;
import org.devlive.grantforge.identity.domain.ExternalIdentityRepository;
import org.devlive.grantforge.identity.domain.IdentitySourceRepository;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.RowScopes;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Independent organization data sources. No identity-source configuration or authentication rules are modified.
 */
@Service
public final class DataSourceService
{
    private static final Logger LOG = LoggerFactory.getLogger(DataSourceService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final DataSourceRepository sources;
    private final DirectoryDepartmentRepository departments;
    private final DirectoryAccountRepository bindings;
    private final IdentitySourceRepository identities;
    private final ExternalIdentityRepository external;
    private final OrgUnitRepository units;
    private final RowScopes scopes;
    private final Map<String, DirectoryProvider> providers;
    private final DirectoryWriter writer;
    private final SecretBox secrets;
    private final AuditLog audit;
    private final Clock clock;
    private final TransactionTemplate transactions;

    /**
     * Assembles provider adapters and existing tenant infrastructure.
     *
     * @param sources     data sources
     * @param departments external mappings
     * @param bindings    pending and stable login bindings
     * @param identities  existing authentication sources
     * @param external    existing OIDC account links
     * @param units       existing organization units
     * @param scopes      existing organization data permissions
     * @param providers   available SDK adapters
     * @param writer      atomic organization reconciler
     * @param secrets     existing credential encryption
     * @param audit       audit log
     * @param clock       current time
     * @param manager     transaction manager
     */
    public DataSourceService(DataSourceRepository sources, DirectoryDepartmentRepository departments, DirectoryAccountRepository bindings, IdentitySourceRepository identities,
                             ExternalIdentityRepository external, OrgUnitRepository units, RowScopes scopes, List<DirectoryProvider> providers, DirectoryWriter writer,
                             SecretBox secrets, AuditLog audit, Clock clock, PlatformTransactionManager manager)
    {
        this.sources = sources;
        this.departments = departments;
        this.bindings = bindings;
        this.identities = identities;
        this.external = external;
        this.units = units;
        this.scopes = scopes;
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(DirectoryProvider::type, provider -> provider));
        this.writer = writer;
        this.secrets = secrets;
        this.audit = audit;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Lists tenant configurations without credentials.
     *
     * @return the sources
     */
    public List<DataSourceView> list()
    {
        return requireNonNull(transactions.execute(status -> sources.findAllByOrderByIdAsc().stream().map(this::view).toList()));
    }

    /**
     * Resolves source names for departments already authorized by the organization service.
     *
     * @param unitIds visible department IDs
     * @return department ID to current source name
     */
    public Map<Long, String> departmentSourceNames(Collection<Long> unitIds)
    {
        if (unitIds.isEmpty()) {
            return Map.of();
        }
        return requireNonNull(transactions.execute(status -> {
            List<Long> ids = new ArrayList<>(unitIds);
            List<DirectoryDepartment> mappings = new ArrayList<>();
            for (int start = 0; start < ids.size(); start += 500) {
                mappings.addAll(departments.findByOrgUnitIdIn(ids.subList(start, Math.min(start + 500, ids.size()))));
            }
            List<Long> sourceIds = mappings.stream().map(DirectoryDepartment::getSourceId).distinct().toList();
            Map<Long, String> sourceNames = new HashMap<>();
            for (int start = 0; start < sourceIds.size(); start += 500) {
                sources.findAllById(sourceIds.subList(start, Math.min(start + 500, sourceIds.size())))
                        .forEach(source -> sourceNames.put(source.requireId(), source.getName()));
            }
            Map<Long, String> result = new HashMap<>();
            mappings.forEach(mapping -> {
                String name = sourceNames.get(mapping.getSourceId());
                if (name != null) {
                    result.put(mapping.getOrgUnitId(), name);
                }
            });
            return result;
        }));
    }

    /**
     * Creates a data source with a unique organization authority per OIDC source.
     *
     * @param actor   the administrator
     * @param command the configuration
     * @return the saved configuration
     */
    public DataSourceView create(long actor, DataSourceCommand command)
    {
        if (!command.code().matches("[a-z][a-z0-9-]{1,63}")) {
            throw DataSourceErrors.invalid("code must be 2-64 lowercase letters, digits or hyphens");
        }
        return mutate(() -> {
            DataSource source = DataSource.create(command.code(), command.type());
            configure(actor, source, command);
            sources.saveAndFlush(source);
            record(AuditAction.DATA_SOURCE_CREATED, AuditOutcome.SUCCESS, actor, source, "data source created");
            return view(source);
        });
    }

    /**
     * Updates mutable settings; initialized scope and account authority cannot be retargeted.
     *
     * @param actor   the administrator
     * @param id      the data source
     * @param command the configuration
     * @return the saved configuration
     */
    public DataSourceView update(long actor, long id, DataSourceCommand command)
    {
        return mutate(() -> {
            DataSource source = lock(id);
            idle(source);
            configure(actor, source, command);
            record(AuditAction.DATA_SOURCE_UPDATED, AuditOutcome.SUCCESS, actor, source, "data source updated");
            return view(source);
        });
    }

    /**
     * Removes only unused configurations; never removes departments that may hold grants.
     *
     * @param actor the administrator
     * @param id    the source
     */
    public void delete(long actor, long id)
    {
        transactions.executeWithoutResult(status -> {
            DataSource source = lock(id);
            idle(source);
            if (!departments.findBySourceId(id).isEmpty()) {
                throw DataSourceErrors.conflict("data source owns managed departments");
            }
            record(AuditAction.DATA_SOURCE_DELETED, AuditOutcome.SUCCESS, actor, source, "unused data source deleted");
            sources.delete(source);
        });
    }

    /**
     * Exercises the same complete SDK fetch as synchronization, without directory writes.
     *
     * @param id the source
     */
    public void test(long id)
    {
        DataSource source = requireNonNull(transactions.execute(status -> lock(id)));
        provider(source.getType()).fetch(settings(source), secret(source));
    }

    /**
     * Fetches remotely outside the write transaction, then atomically applies the fenced snapshot.
     *
     * @param actor the administrator, or null for a scheduled job
     * @param id    the source
     * @return the completed report
     */
    @SuppressWarnings({"PMD.AvoidInstanceofChecksInCatchClause", "PMD.PreserveStackTrace"}) // Release the lease for every failure; expose only a safe validation message.
    public DirectoryReport sync(@Nullable Long actor, long id)
    {
        String token = UUID.randomUUID().toString();
        DataSource initial = requireNonNull(transactions.execute(status -> {
            DataSource source = lock(id);
            idle(source);
            if (!source.getEnabled()) {
                throw DataSourceErrors.conflict("data source disabled");
            }
            source.claimSync(token, clock.instant().plus(Duration.ofMinutes(15)));
            return source;
        }));
        try {
            DirectoryProvider.Snapshot snapshot = provider(initial.getType()).fetch(settings(initial), secret(initial));
            return requireNonNull(transactions.execute(status -> {
                DataSource source = lock(id);
                if (!source.ownsSync(token, clock.instant()) || !source.getEnabled() || !source.getSettings().equals(initial.getSettings())) {
                    throw DataSourceErrors.conflict("data source configuration or lease changed");
                }
                String directoryScope = source.getDirectoryScope();
                if (directoryScope != null && !directoryScope.equals(snapshot.scope())) {
                    throw DataSourceErrors.unavailable("authorization scope changed; restore the approved scope before syncing");
                }
                DirectoryReport report = writer.apply(source, snapshot);
                source.approveScope(snapshot.scope());
                source.synced(clock.instant(), report.summary());
                source.releaseSync(token);
                record(AuditAction.DATA_SOURCE_SYNCED, AuditOutcome.SUCCESS, actor, source, report.summary());
                return report;
            }));
        }
        catch (RuntimeException failed) {
            transactions.executeWithoutResult(status -> {
                DataSource source = lock(id);
                if (source.ownsSync(token, clock.instant())) {
                    source.synced(clock.instant(), "failed; previous directory snapshot retained");
                    record(AuditAction.DATA_SOURCE_SYNCED, AuditOutcome.FAILURE, actor, source, "sync failed; previous directory snapshot retained");
                    source.releaseSync(token);
                }
            });
            if (failed instanceof IllegalArgumentException) {
                throw DataSourceErrors.invalid("directory snapshot exceeds local organization limits");
            }
            throw failed;
        }
    }

    /**
     * Receives only fully completed logins. Local updates commit before the login response; failures cannot reject authentication.
     *
     * @param event the trusted login context
     */
    @EventListener
    public void onLogin(LoginSucceeded event)
    {
        if (event.federated() == null) {
            return;
        }
        TenantContext.runInTenant(event.tenantId(), () -> {
            try {
                transactions.executeWithoutResult(status -> {
                    var identity = external.findByAccountId(event.accountId()).orElse(null);
                    if (identity == null) {
                        return;
                    }
                    var authSource = identities.findById(identity.getSourceId()).orElse(null);
                    if (authSource == null || !authSource.getCode().equals(requireNonNull(event.federated()).sourceCode())) {
                        return;
                    }
                    DataSource candidate = sources.findByIdentitySourceId(identity.getSourceId()).orElse(null);
                    if (candidate == null) {
                        return;
                    }
                    DataSource source = lock(candidate.requireId());
                    if (!source.getEnabled() || !source.getLoginUpdates()) {
                        return;
                    }
                    boolean linked = writer.login(source, event.accountId(), requireNonNull(event.federated()).email());
                    record(AuditAction.DATA_SOURCE_ACCOUNT_LINKED, linked ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE, event.accountId(), source,
                        linked ? "employee linked; memberships reconciled" : "binding pending: missing, ambiguous or already bound mailbox");
                });
            }
            catch (RuntimeException failed) {
                LOG.warn("Organization update failed after authenticated login for account {}; login remains valid", event.accountId());
                audit.record(new AuditRecord(AuditAction.DATA_SOURCE_ACCOUNT_LINKED, AuditOutcome.FAILURE, event.tenantId(), event.accountId(),
                    null, null, "organization update failed; previous memberships retained"));
            }
        });
    }

    /**
     * Runs independent organization schedules; a failing provider does not stop the others.
     */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void syncDue()
    {
        List<DataSource> all = TenantContext.callAsSystem(() -> sources.findAllByOrderByIdAsc());
        for (DataSource source : all) {
            Integer interval = source.getSyncIntervalMinutes();
            var lastSyncedAt = source.getLastSyncedAt();
            if (!source.getEnabled() || interval == null || source.syncRunning(clock.instant())
                || (lastSyncedAt != null && lastSyncedAt.plus(Duration.ofMinutes(interval)).isAfter(clock.instant()))) {
                continue;
            }
            try {
                TenantContext.callInTenant(requireNonNull(source.getTenantId()), () -> sync(null, source.requireId()));
            }
            catch (RuntimeException failed) {
                LOG.warn("Organization sync failed for data source {}; previous snapshot retained", source.getCode());
            }
        }
    }

    private void configure(long actor, DataSource source, DataSourceCommand command)
    {
        if (command.name().isBlank() || command.name().length() > 128 || !source.getType().equals(command.type())
            || !source.getCode().equals(command.code())) {
            throw DataSourceErrors.invalid("invalid name or immutable data source code/type");
        }
        var identity = identities.findById(command.identitySourceId()).orElse(null);
        if ((identity == null && (source.getId() == null || source.getIdentitySourceId() != command.identitySourceId()))
                || (identity != null && identity.getType() != IdentitySourceType.OIDC)) {
            throw DataSourceErrors.invalid("login linking requires an OIDC identity source");
        }
        Map<String, String> valid = provider(command.type()).validate(command.settings());
        String config = JSON.writeValueAsString(valid);
        Integer interval = command.syncIntervalMinutes();
        if (config.length() > 4000 || (interval != null && (interval < 15 || interval > 10080))) {
            throw DataSourceErrors.invalid("invalid settings or sync interval; use 15-10080 minutes");
        }
        DataSource other = sources.findByIdentitySourceId(command.identitySourceId()).orElse(null);
        if (other != null && !Objects.equals(other.getId(), source.getId())) {
            throw DataSourceErrors.conflict("OIDC identity source already has an organization data source");
        }
        if (source.getId() != null && (!departments.findBySourceId(source.requireId()).isEmpty() || !bindings.findBySourceId(source.requireId()).isEmpty())
            && (!settings(source).equals(valid) || source.getIdentitySourceId() != command.identitySourceId() || !Objects.equals(source.getParentUnitId(), command.parentUnitId()))) {
            throw DataSourceErrors.conflict("initialized directory authority, scope and mount cannot change");
        }
        if (command.parentUnitId() != null && units.findById(command.parentUnitId()).map(unit -> unit.isDirectoryManaged()).orElse(true)) {
            throw DataSourceErrors.invalid("directory mount must be a visible local department");
        }
        if (command.parentUnitId() != null) {
            scopes.requireWithin(actor, units, OrgUnit.class, DataAction.UPDATE, command.parentUnitId());
        }
        String credential = command.secret();
        if (credential != null && !credential.isBlank()) {
            if (credential.length() > 512) {
                throw DataSourceErrors.invalid("AppSecret is too long");
            }
            source.storeSecret(secrets.seal(credential));
        }
        if (source.getSecret() == null) {
            throw DataSourceErrors.invalid("AppSecret is required");
        }
        source.configure(command.name().strip(), command.identitySourceId(), command.enabled(), command.loginUpdates(), config, command.parentUnitId(), command.syncIntervalMinutes());
    }

    @SuppressWarnings("PMD.PreserveStackTrace") // Database exceptions can contain sensitive values.
    private <T> T mutate(java.util.function.Supplier<T> action)
    {
        try {
            return requireNonNull(transactions.execute(status -> action.get()));
        }
        catch (DataIntegrityViolationException conflict) {
            throw DataSourceErrors.conflict("duplicate data source code or OIDC authority");
        }
    }

    private DataSource lock(long id)
    {
        return sources.lockById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "data source missing"));
    }

    private void idle(DataSource source)
    {
        if (source.syncRunning(clock.instant())) {
            throw DataSourceErrors.conflict("organization synchronization already running");
        }
    }

    private DirectoryProvider provider(String type)
    {
        DirectoryProvider provider = providers.get(type);
        if (provider == null) {
            throw DataSourceErrors.invalid("unsupported data source type");
        }
        return provider;
    }

    private static Map<String, String> settings(DataSource source)
    {
        return JSON.readValue(source.getSettings(), new TypeReference<Map<String, String>>()
        {
        });
    }

    private String secret(DataSource source)
    {
        return secrets.open(requireNonNull(source.getSecret()));
    }

    private DataSourceView view(DataSource source)
    {
        Long parentId = source.getParentUnitId();
        return new DataSourceView(Long.toString(source.requireId()), source.getCode(), source.getName(), source.getType(), Long.toString(source.getIdentitySourceId()),
            identities.findById(source.getIdentitySourceId()).map(identity -> identity.getName()).orElse(""), source.getEnabled(), source.getLoginUpdates(), settings(source), parentId == null ? null : parentId.toString(),
            source.getSyncIntervalMinutes(), source.getSecret() != null, (!departments.findBySourceId(source.requireId()).isEmpty() || !bindings.findBySourceId(source.requireId()).isEmpty()), source.getLastSyncedAt(), source.getLastSyncSummary());
    }

    private void record(AuditAction action, AuditOutcome outcome, @Nullable Long actor, DataSource source, String detail)
    {
        audit.recordWithChange(new AuditRecord(action, outcome, TenantContext.requireTenantId(), actor, null, source.getCode(), detail));
    }
}
