// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.common.lang.Strings;
import org.devlive.grantforge.identity.domain.AccountStatus;
import org.devlive.grantforge.identity.domain.ExternalIdentity;
import org.devlive.grantforge.identity.domain.ExternalIdentityRepository;
import org.devlive.grantforge.identity.domain.IdentitySource;
import org.devlive.grantforge.identity.domain.IdentitySourceRepository;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Identity sources of the current tenant (D-72): directories and providers its users sign in with, and syncs of
 * directories, which create accounts for new users, take over changed names and may disable accounts of users who left.
 */
@Service
public final class IdentitySourceService
{
    /** Codes: lowercase letters, digits and hyphens, starting with a letter. */
    public static final Pattern CODE = Pattern.compile("[a-z][a-z0-9-]{1,63}");

    private static final int MAX_PROBLEMS = 20;

    private final IdentitySourceRepository sources;
    private final ExternalIdentityRepository links;
    private final UserAccountRepository accounts;
    private final IdentitySourceSettings settings;
    private final LdapDirectory directory;
    private final ExternalAccounts externals;
    private final ConsoleSessionService sessions;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param sources the sources
     * @param links which accounts belong to which source
     * @param accounts the accounts
     * @param settings reads and writes the settings of sources
     * @param directory talks to LDAP directories
     * @param externals creates and updates accounts of sources
     * @param sessions ends the sessions of accounts a sync disables
     * @param audit records changes
     * @param transactionManager opens transactions
     * @param clock the current time
     */
    public IdentitySourceService(IdentitySourceRepository sources, ExternalIdentityRepository links, UserAccountRepository accounts,
            IdentitySourceSettings settings, LdapDirectory directory, ExternalAccounts externals, ConsoleSessionService sessions, AuditLog audit,
            PlatformTransactionManager transactionManager, Clock clock)
    {
        this.sources = requireNonNull(sources, "sources");
        this.links = requireNonNull(links, "links");
        this.accounts = requireNonNull(accounts, "accounts");
        this.settings = requireNonNull(settings, "settings");
        this.directory = requireNonNull(directory, "directory");
        this.externals = requireNonNull(externals, "externals");
        this.sessions = requireNonNull(sessions, "sessions");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Lists the tenant's sources.
     *
     * @return the sources in creation order
     */
    public List<IdentitySourceView> list()
    {
        return requireNonNull(transactions.execute(status -> sources.findAllByOrderByIdAsc().stream().map(this::view).toList()));
    }

    /**
     * Returns a source.
     *
     * @param id the source
     * @return the source
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public IdentitySourceView get(long id)
    {
        return requireNonNull(transactions.execute(status -> view(require(id))));
    }

    /**
     * Adds a source.
     *
     * @param actorId the administrator
     * @param command the source
     * @return the source
     * @throws GrantForgeException with {@link IdentityErrorCode#IDENTITY_SOURCE_INVALID} or
     *         {@link IdentityErrorCode#IDENTITY_SOURCE_CODE_TAKEN}
     */
    public IdentitySourceView create(long actorId, IdentitySourceCommand command)
    {
        String code = Strings.blankToNull(command.code());
        if (code == null || !CODE.matcher(code).matches()) {
            throw LdapSettings.invalid("code must be 2-64 lowercase letters, digits or hyphens, starting with a letter");
        }
        if (TenantContext.callAsSystem(() -> sources.findByCode(code)).isPresent()) {
            throw taken(code, null);
        }
        try {
            IdentitySourceView created = requireNonNull(transactions.execute(status -> {
                IdentitySource source = IdentitySource.create(code, command.type());
                configure(source, command);
                return view(sources.saveAndFlush(source));
            }));
            record(AuditAction.IDENTITY_SOURCE_CREATED, actorId, code, command.type().name());
            return created;
        }
        catch (DataIntegrityViolationException concurrent) {
            throw taken(code, concurrent);
        }
    }

    /**
     * Changes a source; its code and type stay.
     *
     * @param actorId the administrator
     * @param id the source
     * @param command the new values
     * @return the source
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or {@link IdentityErrorCode#IDENTITY_SOURCE_INVALID}
     */
    public IdentitySourceView update(long actorId, long id, IdentitySourceCommand command)
    {
        IdentitySourceView updated = requireNonNull(transactions.execute(status -> {
            IdentitySource source = require(id);
            if (source.getType() != command.type()) {
                throw LdapSettings.invalid("the type of a source cannot change");
            }
            configure(source, command);
            return view(source);
        }));
        record(AuditAction.IDENTITY_SOURCE_UPDATED, actorId, updated.code(), null);
        return updated;
    }

    /**
     * Deletes a source no account signs in with any more.
     *
     * @param actorId the administrator
     * @param id the source
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or {@link IdentityErrorCode#IDENTITY_SOURCE_IN_USE}
     */
    public void delete(long actorId, long id)
    {
        String code = requireNonNull(transactions.execute(status -> {
            IdentitySource source = require(id);
            long used = links.countBySourceId(id);
            if (used > 0) {
                throw new GrantForgeException(IdentityErrorCode.IDENTITY_SOURCE_IN_USE, used + " accounts use source " + id, used);
            }
            sources.delete(source);
            return source.getCode();
        }));
        record(AuditAction.IDENTITY_SOURCE_DELETED, actorId, code, null);
    }

    /**
     * Checks that a directory answers with the stored settings. Providers are checked when a user signs in.
     *
     * @param id the source
     * @throws GrantForgeException with {@link IdentityErrorCode#IDENTITY_SOURCE_UNAVAILABLE} if it does not answer
     */
    public void test(long id)
    {
        IdentitySource source = requireNonNull(transactions.execute(status -> require(id)));
        if (source.getType() != IdentitySourceType.LDAP) {
            throw new GrantForgeException(IdentityErrorCode.IDENTITY_SOURCE_NOT_SYNCABLE, "only directories can be tested");
        }
        directory.test(settings.ldap(source), settings.secret(source));
    }

    /**
     * Syncs the users of a directory into accounts: new users get one if the source creates accounts, names and e-mail
     * addresses of known users are taken over, and accounts of users no longer listed are disabled if the settings say so.
     *
     * @param actorId the administrator, or {@code null} for a scheduled sync
     * @param id the source
     * @return what changed
     * @throws GrantForgeException with {@link IdentityErrorCode#IDENTITY_SOURCE_NOT_SYNCABLE} for a provider or
     *         {@link IdentityErrorCode#IDENTITY_SOURCE_UNAVAILABLE} if the directory does not answer
     */
    public SyncReport sync(@Nullable Long actorId, long id)
    {
        IdentitySource stored = requireNonNull(transactions.execute(status -> require(id)));
        if (stored.getType() != IdentitySourceType.LDAP) {
            throw new GrantForgeException(IdentityErrorCode.IDENTITY_SOURCE_NOT_SYNCABLE, "only directories can be synced");
        }
        LdapSettings ldap = settings.ldap(stored);
        List<DirectoryUser> users;
        try {
            users = directory.list(ldap, settings.secret(stored));
        }
        catch (GrantForgeException unavailable) {
            transactions.executeWithoutResult(status -> require(id).synced(clock.instant(), "failed: " + unavailable.getMessage()));
            throw unavailable;
        }
        List<Long> disabled = new ArrayList<>();
        SyncReport report = requireNonNull(transactions.execute(status -> {
            IdentitySource source = require(id);
            Map<String, Long> known = new HashMap<>();
            for (ExternalIdentity link : links.findBySourceId(id)) {
                known.put(link.getExternalId(), link.getAccountId());
            }
            Set<String> seen = new HashSet<>();
            int created = 0;
            int updated = 0;
            List<String> problems = new ArrayList<>();
            for (DirectoryUser user : users) {
                seen.add(user.id());
                Long accountId = known.get(user.id());
                if (accountId != null) {
                    UserAccount account = accounts.findById(accountId).orElse(null);
                    if (account != null && externals.refresh(account, user)) {
                        updated++;
                    }
                }
                else if (source.isProvisioning()) {
                    try {
                        externals.provision(source, user);
                        created++;
                    }
                    catch (GrantForgeException refused) {
                        if (problems.size() < MAX_PROBLEMS) {
                            problems.add(user.username());
                        }
                    }
                }
            }
            if (ldap.disableMissing()) {
                for (Map.Entry<String, Long> link : known.entrySet()) {
                    UserAccount account = seen.contains(link.getKey()) ? null : accounts.findById(link.getValue()).orElse(null);
                    if (account != null && account.getStatus() == AccountStatus.ACTIVE) {
                        account.disable();
                        disabled.add(account.requireId());
                    }
                }
            }
            SyncReport done = new SyncReport(users.size(), created, updated, disabled.size(), problems);
            source.synced(clock.instant(), done.summary());
            audit.recordWithChange(new AuditRecord(AuditAction.IDENTITY_SOURCE_SYNCED, AuditOutcome.SUCCESS, TenantContext.requireTenantId(),
                    actorId, null, source.getCode(), done.summary()));
            return done;
        }));
        disabled.forEach(sessions::revokeAll);
        return report;
    }

    /**
     * Lists the providers of every tenant users may sign in with, for the buttons of the sign-in page.
     *
     * @return the enabled providers' codes and names, in creation order
     */
    public List<SignInOption> signInOptions()
    {
        return TenantContext.callAsSystem(() -> sources.findByTypeAndEnabledTrueOrderByIdAsc(IdentitySourceType.OIDC)).stream()
                .map(source -> new SignInOption(source.getCode(), source.getName())).toList();
    }

    /**
     * Lists the directories of every tenant whose automatic sync is due.
     *
     * @param now the current time
     * @return the sources' tenants and IDs
     */
    List<IdentitySource> dueForSync(Instant now)
    {
        return TenantContext.callAsSystem(() -> sources.findByTypeAndEnabledTrueOrderByIdAsc(IdentitySourceType.LDAP)).stream()
                .filter(source -> {
                    Integer interval = source.getSyncIntervalMinutes();
                    Instant last = source.getLastSyncedAt();
                    return interval != null && (last == null || !now.isBefore(last.plusSeconds(interval * 60L)));
                })
                .toList();
    }

    private void configure(IdentitySource source, IdentitySourceCommand command)
    {
        String name = Strings.blankToNull(command.name());
        if (name == null || name.length() > 128) {
            throw LdapSettings.invalid("name is required and at most 128 characters");
        }
        Object typed = switch (command.type()) {
            case LDAP -> command.ldap();
            case OIDC -> command.oidc();
        };
        if (typed == null) {
            throw LdapSettings.invalid(command.type().name().toLowerCase(Locale.ROOT) + " settings are required");
        }
        Integer interval = command.syncIntervalMinutes();
        if (interval != null && (command.type() != IdentitySourceType.LDAP || interval < 15 || interval > 10_080)) {
            throw LdapSettings.invalid("syncIntervalMinutes must be 15-10080, for directories only");
        }
        source.configure(name, command.enabled(), command.provisioning(), IdentitySourceSettings.write(typed), interval);
        String secret = command.secret();
        if (secret != null) {
            source.storeSecret(secret.isBlank() ? null : settings.seal(secret));
        }
    }

    private IdentitySourceView view(IdentitySource source)
    {
        long id = source.requireId();
        boolean ldap = source.getType() == IdentitySourceType.LDAP;
        return new IdentitySourceView(id, source.getCode(), source.getName(), source.getType(), source.isEnabled(), source.isProvisioning(),
                ldap ? settings.ldap(source) : null, ldap ? null : settings.oidc(source), source.getSecret() != null,
                source.getSyncIntervalMinutes(), source.getLastSyncedAt(), source.getLastSyncSummary(), links.countBySourceId(id));
    }

    private IdentitySource require(long id)
    {
        return sources.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no identity source " + id));
    }

    private void record(AuditAction action, long actorId, String code, @Nullable String reason)
    {
        audit.record(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null, code, reason));
    }

    private static GrantForgeException taken(String code, @Nullable Throwable cause)
    {
        return new GrantForgeException(IdentityErrorCode.IDENTITY_SOURCE_CODE_TAKEN, "identity source code taken: " + code, cause, code);
    }
}
