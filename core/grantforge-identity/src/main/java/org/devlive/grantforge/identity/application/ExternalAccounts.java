// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.application.AuditRecord;
import org.devlive.grantforge.audit.domain.AuditAction;
import org.devlive.grantforge.audit.domain.AuditOutcome;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.identity.domain.ExternalIdentity;
import org.devlive.grantforge.identity.domain.ExternalIdentityRepository;
import org.devlive.grantforge.identity.domain.IdentitySource;
import org.devlive.grantforge.identity.domain.IdentitySourceRepository;
import org.devlive.grantforge.identity.domain.IdentitySourceType;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Accounts of identity sources (D-72): their password is checked by the source, they are created when a user of a
 * source signs in for the first time or a sync finds them, and their names follow the source. A user whose name a local
 * account has is never linked to it, so a directory cannot take an account over.
 */
@Service
public class ExternalAccounts
{
    private static final Logger LOG = LoggerFactory.getLogger(ExternalAccounts.class);

    private final UserAccountRepository accounts;
    private final ExternalIdentityRepository links;
    private final IdentitySourceRepository sources;
    private final IdentitySourceSettings settings;
    private final LdapDirectory directory;
    private final PasswordEncoder encoder;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param accounts the accounts
     * @param links which accounts belong to which source
     * @param sources the sources
     * @param settings reads the settings of sources
     * @param directory talks to LDAP directories
     * @param encoder hashes the unusable passwords of created accounts
     * @param audit records created accounts
     * @param transactionManager opens transactions
     * @param clock the current time
     */
    public ExternalAccounts(UserAccountRepository accounts, ExternalIdentityRepository links, IdentitySourceRepository sources,
            IdentitySourceSettings settings, LdapDirectory directory, PasswordEncoder encoder, AuditLog audit,
            PlatformTransactionManager transactionManager, Clock clock)
    {
        this.accounts = requireNonNull(accounts, "accounts");
        this.links = requireNonNull(links, "links");
        this.sources = requireNonNull(sources, "sources");
        this.settings = requireNonNull(settings, "settings");
        this.directory = requireNonNull(directory, "directory");
        this.encoder = requireNonNull(encoder, "encoder");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Returns whether an identity source signs an account in.
     *
     * @param accountId the account
     * @return {@code true} for accounts of a source
     */
    public boolean isExternal(long accountId)
    {
        return links.findByAccountId(accountId).isPresent();
    }

    /**
     * Checks the password of an account of a directory by binding as its user, and takes over the names the directory
     * has now. Call it in the account's tenant, in a transaction.
     *
     * @param account the account
     * @param password the password entered
     * @return empty for an account whose password is kept here; otherwise whether the source accepts the password
     *         (always {@code false} for a disabled source or a provider, which signs users in on its own pages)
     */
    public Optional<Boolean> checkPassword(UserAccount account, @Nullable String password)
    {
        ExternalIdentity link = links.findByAccountId(account.requireId()).orElse(null);
        if (link == null) {
            return Optional.empty();
        }
        IdentitySource source = sources.findById(link.getSourceId()).orElse(null);
        if (source == null || !source.isEnabled() || source.getType() != IdentitySourceType.LDAP) {
            return Optional.of(false);
        }
        Optional<DirectoryUser> user = directory.authenticate(settings.ldap(source), settings.secret(source), account.getUsername(), password)
                .filter(found -> found.id().equals(link.getExternalId()));
        user.ifPresent(found -> refresh(account, found));
        return Optional.of(user.isPresent());
    }

    /**
     * Signs up a user no account has yet: the enabled directories that create accounts are asked in turn, and the first
     * that accepts the name and password gets an account for the user in its tenant.
     *
     * @param name the name entered
     * @param password the password entered
     * @return the new account, if a directory accepted the user
     */
    public Optional<Long> signUp(String name, @Nullable String password)
    {
        if (password == null || password.isEmpty()) {
            return Optional.empty();
        }
        List<IdentitySource> candidates = TenantContext.callAsSystem(() -> sources.findByTypeAndEnabledTrueOrderByIdAsc(IdentitySourceType.LDAP))
                .stream().filter(IdentitySource::isProvisioning).toList();
        for (IdentitySource candidate : candidates) {
            long tenantId = requireNonNull(candidate.getTenantId(), "tenantId");
            long sourceId = candidate.requireId();
            try {
                Optional<Long> created = TenantContext.callInTenant(tenantId, () -> requireNonNull(transactions.execute(status ->
                        sources.findById(sourceId).flatMap(source -> directory.authenticate(settings.ldap(source), settings.secret(source), name,
                                password).map(user -> provision(source, user).requireId())))));
                if (created.isPresent()) {
                    return created;
                }
            }
            catch (GrantForgeException refused) {
                // One directory being down or a name conflict must not stop the others.
                LOG.warn("Identity source '{}' could not sign up '{}': {}", candidate.getCode(), name, refused.getMessage());
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the account of a source's user, creating it if the source creates accounts. Call it in the source's tenant,
     * in a transaction.
     *
     * @param source the source
     * @param user the user as the source describes them
     * @return the account, with the names the source has now
     * @throws GrantForgeException with {@link IdentityErrorCode#EXTERNAL_ACCOUNT_CONFLICT} if an account kept here or of
     *         another source has the name, or {@link IdentityErrorCode#IDENTITY_SOURCE_INVALID} if the source does not
     *         create accounts or the name cannot be one
     */
    public UserAccount provision(IdentitySource source, DirectoryUser user)
    {
        long sourceId = source.requireId();
        ExternalIdentity link = links.findBySourceIdAndExternalId(sourceId, user.id()).orElse(null);
        if (link != null) {
            UserAccount account = accounts.findById(link.getAccountId()).orElseThrow(() -> new IllegalStateException("link without account"));
            refresh(account, user);
            return account;
        }
        if (!source.isProvisioning()) {
            throw LdapSettings.invalid("the source does not create accounts");
        }
        if (!UserAccount.USERNAME.matcher(user.username()).matches()) {
            throw LdapSettings.invalid("'" + user.username() + "' cannot be a user name");
        }
        String norm = UserAccount.normalize(user.username());
        // Names are unique across tenants (D-23).
        if (TenantContext.callAsSystem(() -> accounts.findByUsernameNorm(norm)).isPresent()) {
            throw new GrantForgeException(IdentityErrorCode.EXTERNAL_ACCOUNT_CONFLICT, "name taken: " + norm, user.username());
        }
        UserAccount account = UserAccount.create(user.username(), encoder.encode(UUID.randomUUID().toString()), clock.instant());
        refresh(account, user);
        account = accounts.saveAndFlush(account);
        long accountId = account.requireId();
        links.save(ExternalIdentity.of(accountId, sourceId, user.id()));
        audit.recordWithChange(new AuditRecord(AuditAction.ACCOUNT_PROVISIONED, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), null,
                source.getCode(), Long.toString(accountId), user.username()));
        return account;
    }

    /**
     * Takes over the display name and e-mail address a source has for a user; values the account cannot hold are left out.
     *
     * @param account the account
     * @param user the user as the source describes them
     * @return whether anything changed
     */
    public boolean refresh(UserAccount account, DirectoryUser user)
    {
        String name = user.displayName();
        if (name != null && name.length() > UserAccount.MAX_DISPLAY_NAME) {
            name = name.substring(0, UserAccount.MAX_DISPLAY_NAME);
        }
        String email = user.email();
        if (email != null && (email.length() > UserAccount.MAX_EMAIL || !UserAccount.EMAIL.matcher(email).matches())) {
            email = null;
        }
        boolean changed = !Objects.equals(name, account.getDisplayName()) || !Objects.equals(email, account.getEmail());
        if (changed) {
            account.withDisplayName(name).withEmail(email);
        }
        return changed;
    }
}
