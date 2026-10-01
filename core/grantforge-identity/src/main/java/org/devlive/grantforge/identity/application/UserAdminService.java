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
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.identity.domain.OrgMember;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserCriteria;
import org.devlive.grantforge.identity.domain.UserRow;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Administration of the bound tenant's accounts. Until roles exist only system accounts (tenant administrators)
 * use it. System accounts and the administrator's own account are protected from being disabled, locked or
 * deleted, so a tenant cannot lose its last way in. Disabling, locking, resetting the password and deleting end
 * the account's sessions at once. Every method must be called with the actor's tenant bound.
 */
@Service
public final class UserAdminService
{
    private final UserAccountRepository accounts;
    private final OrgUnitRepository units;
    private final OrgMemberRepository members;
    private final PasswordService passwords;
    private final ConsoleSessionService sessions;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param accounts user accounts
     * @param units departments
     * @param members department memberships
     * @param passwords checks and hashes passwords
     * @param sessions ends sessions
     * @param audit records every change
     * @param transactionManager opens transactions
     * @param clock source of the current time
     */
    public UserAdminService(UserAccountRepository accounts, OrgUnitRepository units, OrgMemberRepository members,
            PasswordService passwords, ConsoleSessionService sessions, AuditLog audit,
            PlatformTransactionManager transactionManager, Clock clock)
    {
        this.accounts = requireNonNull(accounts, "accounts");
        this.units = requireNonNull(units, "units");
        this.members = requireNonNull(members, "members");
        this.passwords = requireNonNull(passwords, "passwords");
        this.sessions = requireNonNull(sessions, "sessions");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Lists matching accounts, the newest first.
     *
     * @param actorId the account asking
     * @param filter the filters
     * @param page the page
     * @return the accounts
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} unless the actor is an administrator, or
     *         {@link CommonErrorCode#NOT_FOUND} for an unknown department
     */
    public PageResult<UserSummary> search(long actorId, UserFilter filter, PageQuery page)
    {
        requireAdministrator(actorId);
        Instant now = clock.instant();
        return requireNonNull(transactions.execute(status -> {
            UserCriteria criteria = criteria(filter);
            List<UserSummary> items = accounts.search(criteria, now, page.offset(), page.size()).stream()
                    .map(row -> UserSummary.from(row, now)).toList();
            return new PageResult<>(items, page.page(), page.size(), accounts.count(criteria, now));
        }));
    }

    /**
     * Returns an account with its departments.
     *
     * @param actorId the account asking
     * @param id the account
     * @return the account
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} or {@link CommonErrorCode#NOT_FOUND}
     */
    public UserDetail find(long actorId, long id)
    {
        requireAdministrator(actorId);
        return detail(id);
    }

    /**
     * Creates an account, which must choose a new password at its first sign-in.
     *
     * @param actorId the account asking
     * @param username the login name, unique across tenants
     * @param password the initial password
     * @param profile the details and departments
     * @return the new account
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link IdentityErrorCode#USERNAME_TAKEN}, a
     *         password policy error, {@link CommonErrorCode#NOT_FOUND} for an unknown department, or
     *         {@link CommonErrorCode#BAD_REQUEST} for an invalid value
     */
    public UserDetail create(long actorId, @Nullable String username, @Nullable String password, UserProfileInput profile)
    {
        requireAdministrator(actorId);
        requireNonNull(profile, "profile");
        String hash = passwords.hashNew(password, username);
        UserAccount account = valid(() -> {
            UserAccount created = UserAccount.create(String.valueOf(username), hash, clock.instant())
                    .withDisplayName(profile.displayName())
                    .withEmail(profile.email());
            created.requirePasswordChange();
            return created;
        });
        if (TenantContext.callAsSystem(() -> accounts.findByUsernameNorm(account.getUsernameNorm())).isPresent()) {
            throw taken(account.getUsername(), null);
        }
        try {
            transactions.executeWithoutResult(status -> {
                accounts.saveAndFlush(account);
                replaceMemberships(account.requireId(), profile);
            });
        }
        catch (DataIntegrityViolationException race) {
            throw taken(account.getUsername(), race);
        }
        record(AuditAction.USER_CREATED, actorId, account.requireId());
        return detail(account.requireId());
    }

    /**
     * Changes an account's details and departments.
     *
     * @param actorId the account asking
     * @param id the account
     * @param profile the new details and departments
     * @return the account
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND} or
     *         {@link CommonErrorCode#BAD_REQUEST}
     */
    public UserDetail update(long actorId, long id, UserProfileInput profile)
    {
        requireAdministrator(actorId);
        requireNonNull(profile, "profile");
        transactions.executeWithoutResult(status -> {
            UserAccount account = require(id);
            valid(() -> account.withDisplayName(profile.displayName()).withEmail(profile.email()));
            replaceMemberships(id, profile);
        });
        record(AuditAction.USER_UPDATED, actorId, id);
        return detail(id);
    }

    /**
     * Lets an account sign in again.
     *
     * @param actorId the account asking
     * @param id the account
     * @return the account
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} or {@link CommonErrorCode#NOT_FOUND}
     */
    public UserDetail enable(long actorId, long id)
    {
        return change(actorId, id, false, UserAccount::enable, AuditAction.USER_ENABLED, false);
    }

    /**
     * Stops an account from signing in and ends its sessions.
     *
     * @param actorId the account asking
     * @param id the account
     * @return the account
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND} or
     *         {@link IdentityErrorCode#ACCOUNT_PROTECTED}
     */
    public UserDetail disable(long actorId, long id)
    {
        return change(actorId, id, true, UserAccount::disable, AuditAction.USER_DISABLED, true);
    }

    /**
     * Locks an account until it is unlocked and ends its sessions.
     *
     * @param actorId the account asking
     * @param id the account
     * @return the account
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND} or
     *         {@link IdentityErrorCode#ACCOUNT_PROTECTED}
     */
    public UserDetail lock(long actorId, long id)
    {
        return change(actorId, id, true, UserAccount::lockIndefinitely, AuditAction.USER_LOCKED, true);
    }

    /**
     * Lifts any lock of an account.
     *
     * @param actorId the account asking
     * @param id the account
     * @return the account
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN} or {@link CommonErrorCode#NOT_FOUND}
     */
    public UserDetail unlock(long actorId, long id)
    {
        return change(actorId, id, false, UserAccount::unlock, AuditAction.USER_UNLOCKED, false);
    }

    /**
     * Sets a new password, which the user must change at the next sign-in, and ends the account's sessions. Lifts
     * any lock. Administrators change their own password in their profile instead.
     *
     * @param actorId the account asking
     * @param id the account
     * @param password the new password
     * @return the account
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND},
     *         {@link IdentityErrorCode#ACCOUNT_PROTECTED} for the actor's own account, or a password policy error
     */
    public UserDetail resetPassword(long actorId, long id, @Nullable String password)
    {
        requireAdministrator(actorId);
        if (actorId == id) {
            throw protectedAccount(id);
        }
        transactions.executeWithoutResult(status -> {
            UserAccount account = require(id);
            passwords.replace(account, password, clock.instant());
            account.requirePasswordChange();
        });
        sessions.revokeAll(id);
        record(AuditAction.USER_PASSWORD_RESET, actorId, id);
        return detail(id);
    }

    /**
     * Deletes an account with its sessions and departments; the audit trail keeps its history.
     *
     * @param actorId the account asking
     * @param id the account
     * @throws GrantForgeException with {@link CommonErrorCode#FORBIDDEN}, {@link CommonErrorCode#NOT_FOUND} or
     *         {@link IdentityErrorCode#ACCOUNT_PROTECTED}
     */
    public void delete(long actorId, long id)
    {
        requireAdministrator(actorId);
        UserAccount account = requireNonNull(transactions.execute(status -> require(id)));
        requireUnprotected(actorId, account);
        // End the sessions first: the session store is not part of the account's rows.
        sessions.revokeAll(id);
        transactions.executeWithoutResult(status -> accounts.delete(require(id)));
        record(AuditAction.USER_DELETED, actorId, id);
    }

    private UserDetail change(long actorId, long id, boolean guarded, Consumer<UserAccount> change, AuditAction action,
            boolean endSessions)
    {
        requireAdministrator(actorId);
        transactions.executeWithoutResult(status -> {
            UserAccount account = require(id);
            if (guarded) {
                requireUnprotected(actorId, account);
            }
            change.accept(account);
        });
        if (endSessions) {
            sessions.revokeAll(id);
        }
        record(action, actorId, id);
        return detail(id);
    }

    private void replaceMemberships(long accountId, UserProfileInput profile)
    {
        Long primary = profile.primaryUnitId();
        Set<Long> others = new LinkedHashSet<>(profile.otherUnitIds());
        if (primary == null && !others.isEmpty()) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, "further departments need a primary department");
        }
        if (primary != null) {
            others.remove(primary);
        }
        List<Long> wanted = new ArrayList<>();
        if (primary != null) {
            wanted.add(primary);
        }
        wanted.addAll(others);
        Set<Long> known = units.findAllById(wanted).stream().map(OrgUnit::requireId).collect(Collectors.toSet());
        wanted.stream().filter(unit -> !known.contains(unit)).findFirst().ifPresent(unit -> {
            throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no department " + unit);
        });
        members.deleteByAccount(accountId);
        wanted.forEach(unit -> members.save(OrgMember.of(accountId, unit, unit.equals(primary))));
    }

    private UserDetail detail(long id)
    {
        Instant now = clock.instant();
        return requireNonNull(transactions.execute(status -> {
            UserAccount account = require(id);
            List<OrgMember> memberships = members.findByAccount(id);
            Map<Long, String> names = units.findAllById(memberships.stream().map(OrgMember::getOrgUnitId).toList())
                    .stream().collect(Collectors.toMap(OrgUnit::requireId, OrgUnit::getName));
            OrgMember primary = memberships.stream().filter(OrgMember::isPrimaryUnit).findFirst().orElse(null);
            UserRow row = new UserRow(id, account.getUsername(), account.getDisplayName(), account.getEmail(),
                    account.getStatus(), account.getLockedUntil(), account.isSystemAccount(),
                    account.isMustChangePassword(), account.getLastLoginAt(),
                    requireNonNull(account.getCreatedAt(), "createdAt"),
                    primary == null ? null : primary.getOrgUnitId(),
                    primary == null ? null : names.get(primary.getOrgUnitId()));
            return new UserDetail(UserSummary.from(row, now), memberships.stream()
                    .map(member -> new UserMembership(member.getOrgUnitId(),
                            names.getOrDefault(member.getOrgUnitId(), ""), member.isPrimaryUnit()))
                    .toList());
        }));
    }

    private UserCriteria criteria(UserFilter filter)
    {
        String text = Strings.blankToNull(filter.text());
        Long unitId = filter.unitId();
        OrgUnit unit = unitId == null ? null : units.findById(unitId)
                .orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no department " + unitId));
        String path = unit != null && filter.includeSubUnits() ? unit.getPath() : null;
        // Wildcards typed by the user are taken literally: dropped, so "50%" cannot match everything.
        String needle = text == null ? null : text.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "");
        return new UserCriteria(needle, filter.state(), path, path == null ? unitId : null);
    }

    private void requireAdministrator(long actorId)
    {
        boolean administrator = Boolean.TRUE.equals(transactions.execute(status -> accounts.findById(actorId)
                .map(UserAccount::isSystemAccount).orElse(false)));
        if (!administrator) {
            throw new GrantForgeException(CommonErrorCode.FORBIDDEN, "account " + actorId + " may not manage accounts");
        }
    }

    private static void requireUnprotected(long actorId, UserAccount account)
    {
        if (account.isSystemAccount() || account.requireId() == actorId) {
            throw protectedAccount(account.requireId());
        }
    }

    private static GrantForgeException protectedAccount(long id)
    {
        return new GrantForgeException(IdentityErrorCode.ACCOUNT_PROTECTED, "account " + id + " is protected");
    }

    private static GrantForgeException taken(String username, @Nullable Throwable cause)
    {
        return new GrantForgeException(IdentityErrorCode.USERNAME_TAKEN, "user name taken", cause, username);
    }

    private UserAccount require(long id)
    {
        return accounts.findById(id).orElseThrow(() -> new GrantForgeException(CommonErrorCode.NOT_FOUND, "no account " + id));
    }

    private static <T> T valid(Supplier<T> build)
    {
        try {
            return build.get();
        }
        catch (IllegalArgumentException invalid) {
            throw new GrantForgeException(CommonErrorCode.BAD_REQUEST, String.valueOf(invalid.getMessage()), invalid);
        }
    }

    private void record(AuditAction action, long actorId, long accountId)
    {
        audit.record(new AuditRecord(action, AuditOutcome.SUCCESS, TenantContext.requireTenantId(), actorId, null,
                Long.toString(accountId), null));
    }
}
