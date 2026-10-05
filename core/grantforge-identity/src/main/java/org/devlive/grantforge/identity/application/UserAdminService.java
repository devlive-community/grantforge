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
import org.devlive.grantforge.identity.domain.AccountPosition;
import org.devlive.grantforge.identity.domain.AccountPositionRepository;
import org.devlive.grantforge.identity.domain.ExternalIdentityRepository;
import org.devlive.grantforge.identity.domain.OrgMember;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.Position;
import org.devlive.grantforge.identity.domain.PositionRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserCriteria;
import org.devlive.grantforge.identity.domain.UserRow;
import org.devlive.grantforge.persistence.authz.AuthorizationChanges;
import org.devlive.grantforge.persistence.query.InClauseBatcher;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.FieldChanges;
import org.devlive.grantforge.persistence.secured.FieldErrorCode;
import org.devlive.grantforge.persistence.secured.FieldReadMode;
import org.devlive.grantforge.persistence.secured.FieldRules;
import org.devlive.grantforge.persistence.secured.RowScopes;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
 * Administration of the bound tenant's accounts. Callers need the matching permission, which the API
 * checks. System accounts and the administrator's own account are protected from being disabled, locked or
 * deleted, so a tenant cannot lose its last way in. Disabling, locking, resetting the password and deleting end
 * the account's sessions at once. Actors only see and change the accounts their data scope covers, and only give
 * departments and positions they may see; the others look as if they did not exist. Every method must be called with the
 * actor's tenant bound.
 */
@Service
public final class UserAdminService
{
    private final UserAccountRepository accounts;
    private final AuthorizationChanges changes;
    private final OrgUnitRepository units;
    private final OrgMemberRepository members;
    private final PositionRepository positions;
    private final AccountPositionRepository holdings;
    private final PasswordService passwords;
    private final ConsoleSessionService sessions;
    private final MfaService mfa;
    private final ExternalIdentityRepository links;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final RowScopes scopes;
    private final FieldRules fields;

    /**
     * Creates the service.
     *
     * @param accounts user accounts
     * @param units departments
     * @param members department memberships
     * @param positions positions
     * @param holdings positions held by accounts
     * @param passwords checks and hashes passwords
     * @param sessions ends sessions
     * @param audit records every change
     * @param transactionManager opens transactions
     * @param clock source of the current time
     * @param events announces deletions
     * @param changes notes changes of what permissions are worked out from
     * @param scopes the accounts, departments and positions each actor may use
     * @param links tells accounts of identity sources apart, whose passwords cannot be reset here
     * @param mfa turns two-step sign-in off for {@link #resetMfa}
     * @param fields how each actor sees the secured fields, which searches must not reveal
     */
    public UserAdminService(UserAccountRepository accounts, OrgUnitRepository units, OrgMemberRepository members,
            PositionRepository positions, AccountPositionRepository holdings, PasswordService passwords, ConsoleSessionService sessions, AuditLog audit,
            PlatformTransactionManager transactionManager, Clock clock,
            ApplicationEventPublisher events, AuthorizationChanges changes, RowScopes scopes, FieldRules fields, MfaService mfa,
            ExternalIdentityRepository links)
    {
        this.links = requireNonNull(links, "links");
        this.mfa = requireNonNull(mfa, "mfa");
        this.fields = requireNonNull(fields, "fields");
        this.scopes = requireNonNull(scopes, "scopes");
        this.changes = requireNonNull(changes, "changes");
        this.events = requireNonNull(events, "events");
        this.accounts = requireNonNull(accounts, "accounts");
        this.units = requireNonNull(units, "units");
        this.members = requireNonNull(members, "members");
        this.positions = requireNonNull(positions, "positions");
        this.holdings = requireNonNull(holdings, "holdings");
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
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} for an unknown department
     */
    public PageResult<UserSummary> search(long actorId, UserFilter filter, PageQuery page)
    {
        return search(actorId, filter, page, DataAction.READ);
    }

    /** Lists the matching accounts the actor may use for an action, as the list and the export need. */
    PageResult<UserSummary> search(long actorId, UserFilter filter, PageQuery page, DataAction action)
    {
        Instant now = clock.instant();
        return requireNonNull(transactions.execute(status -> {
            UserCriteria criteria = criteria(actorId, filter);
            Specification<UserAccount> scope = scopes.scope(actorId, UserAccount.class, action);
            List<UserSummary> items = accounts.search(criteria, scope, now, page.offset(), page.size()).stream()
                    .map(row -> UserSummary.from(row, now)).toList();
            return new PageResult<>(items, page.page(), page.size(), accounts.count(criteria, scope, now));
        }));
    }

    /**
     * Returns an account with its departments.
     *
     * @param actorId the account asking
     * @param id the account
     * @return the account
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
     */
    public UserDetail find(long actorId, long id)
    {
        transactions.executeWithoutResult(status -> require(actorId, id, DataAction.READ));
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
     * @throws GrantForgeException with {@link IdentityErrorCode#USERNAME_TAKEN}, a
     *         password policy error, {@link CommonErrorCode#NOT_FOUND} for an unknown department,
     *         {@link CommonErrorCode#BAD_REQUEST} for an invalid value, or {@link FieldErrorCode#READONLY_CHANGED} for a
     *         read-only field given a value
     */
    public UserDetail create(long actorId, @Nullable String username, @Nullable String password, UserProfileInput profile)
    {
        requireNonNull(profile, "profile");
        FieldChanges changes = FieldChanges.of(fields, actorId, "user");
        String email = changes.take("email", null, profile.email());
        changes.requireAllowed();
        String hash = passwords.hashNew(password, username);
        UserAccount account = valid(() -> {
            UserAccount created = UserAccount.create(String.valueOf(username), hash, clock.instant())
                    .withDisplayName(profile.displayName())
                    .withEmail(email);
            created.requirePasswordChange();
            return created;
        });
        if (TenantContext.callAsSystem(() -> accounts.findByUsernameNorm(account.getUsernameNorm())).isPresent()) {
            throw taken(account.getUsername(), null);
        }
        try {
            transactions.executeWithoutResult(status -> {
                accounts.saveAndFlush(account);
                replaceMemberships(actorId, account.requireId(), profile);
            });
        }
        catch (DataIntegrityViolationException race) {
            throw taken(account.getUsername(), race);
        }
        record(AuditAction.USER_CREATED, actorId, account.requireId());
        return detail(account.requireId());
    }

    /**
     * Changes an account's details and departments. A secured field sent back as the actor was shown it, such as a masked
     * e-mail address, keeps its value; changing a read-only one is refused.
     *
     * @param actorId the account asking
     * @param id the account
     * @param profile the new details and departments
     * @return the account
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}, {@link CommonErrorCode#BAD_REQUEST} or
     *         {@link FieldErrorCode#READONLY_CHANGED}
     */
    public UserDetail update(long actorId, long id, UserProfileInput profile)
    {
        requireNonNull(profile, "profile");
        transactions.executeWithoutResult(status -> {
            UserAccount account = require(actorId, id, DataAction.UPDATE);
            FieldChanges changes = FieldChanges.of(fields, actorId, "user");
            String email = changes.take("email", account.getEmail(), profile.email());
            changes.requireAllowed();
            valid(() -> account.withDisplayName(profile.displayName()).withEmail(email));
            replaceMemberships(actorId, id, profile);
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
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
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
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or
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
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or
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
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}
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
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND},
     *         {@link IdentityErrorCode#ACCOUNT_PROTECTED} for the actor's own account, or a password policy error
     */
    public UserDetail resetPassword(long actorId, long id, @Nullable String password)
    {
        if (actorId == id) {
            throw protectedAccount(id);
        }
        transactions.executeWithoutResult(status -> {
            UserAccount account = require(actorId, id, DataAction.UPDATE);
            if (links.findByAccountId(id).isPresent()) {
                throw new GrantForgeException(IdentityErrorCode.PASSWORD_MANAGED_EXTERNALLY, "account " + id + " has an identity source");
            }
            passwords.replace(account, password, clock.instant());
            account.requirePasswordChange();
        });
        sessions.revokeAll(id);
        record(AuditAction.USER_PASSWORD_RESET, actorId, id);
        return detail(id);
    }

    /**
     * Turns two-step sign-in off for an account whose authenticator was lost, and ends its sessions; the user signs in
     * with the password and may set an authenticator up again.
     *
     * @param actorId the account asking, which must reach the account in its data scope
     * @param id the account
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND}, {@link IdentityErrorCode#ACCOUNT_PROTECTED}
     *         for the actor's own account or {@link IdentityErrorCode#MFA_NOT_ENABLED}
     */
    public void resetMfa(long actorId, long id)
    {
        if (actorId == id) {
            throw protectedAccount(id);
        }
        transactions.executeWithoutResult(status -> {
            require(actorId, id, DataAction.UPDATE);
            mfa.reset(actorId, id);
        });
        sessions.revokeAll(id);
    }

    /**
     * Deletes an account with its sessions and departments; the audit trail keeps its history.
     *
     * @param actorId the account asking
     * @param id the account
     * @throws GrantForgeException with {@link CommonErrorCode#NOT_FOUND} or
     *         {@link IdentityErrorCode#ACCOUNT_PROTECTED}
     */
    public void delete(long actorId, long id)
    {
        UserAccount account = requireNonNull(transactions.execute(status -> require(actorId, id, DataAction.DELETE)));
        requireUnprotected(actorId, account);
        // End the sessions first: the session store is not part of the account's rows.
        sessions.revokeAll(id);
        transactions.executeWithoutResult(status -> accounts.delete(require(id)));
        events.publishEvent(new IdentityDeleted(IdentityDeleted.Kind.ACCOUNT, id));
        record(AuditAction.USER_DELETED, actorId, id);
    }

    private UserDetail change(long actorId, long id, boolean guarded, Consumer<UserAccount> change, AuditAction action,
            boolean endSessions)
    {
        transactions.executeWithoutResult(status -> {
            UserAccount account = require(actorId, id, DataAction.UPDATE);
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

    private void replaceMemberships(long actorId, long accountId, UserProfileInput profile)
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
        // Departments the actor may see, and those the account already belongs to, which the actor may keep.
        Set<Long> known = units.findAllWithin(wanted, scopes.scope(actorId, OrgUnit.class, DataAction.READ)).stream()
                .map(OrgUnit::requireId).collect(Collectors.toCollection(HashSet::new));
        members.findByAccount(accountId).forEach(member -> known.add(member.getOrgUnitId()));
        wanted.stream().filter(unit -> !known.contains(unit)).findFirst().ifPresent(unit -> {
            throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no department " + unit);
        });
        members.deleteByAccount(accountId);
        changes.currentTenant();
        wanted.forEach(unit -> members.save(OrgMember.of(accountId, unit, unit.equals(primary))));
        replacePositions(actorId, accountId, profile.positionIds());
    }

    private void replacePositions(long actorId, long accountId, List<Long> positionIds)
    {
        Set<Long> wanted = new LinkedHashSet<>(positionIds);
        Specification<Position> visible = scopes.scope(actorId, Position.class, DataAction.READ);
        Set<Long> known = InClauseBatcher.query(wanted, batch -> positions.findAllWithin(batch, visible)).stream()
                .map(Position::requireId).collect(Collectors.toCollection(HashSet::new));
        holdings.findByAccountId(accountId).forEach(holding -> known.add(holding.getPositionId()));
        wanted.stream().filter(position -> !known.contains(position)).findFirst().ifPresent(position -> {
            throw new GrantForgeException(CommonErrorCode.NOT_FOUND, "no position " + position);
        });
        holdings.removeAllOf(accountId);
        changes.currentTenant();
        wanted.forEach(position -> holdings.save(AccountPosition.of(accountId, position)));
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
            List<Long> held = holdings.findByAccountId(id).stream().map(AccountPosition::getPositionId).toList();
            List<UserPosition> heldPositions = InClauseBatcher.query(held, positions::findAllById).stream()
                    .sorted(Comparator.comparingInt(Position::getSortOrder).thenComparing(Position::getName))
                    .map(position -> new UserPosition(position.requireId(), position.getName())).toList();
            return new UserDetail(UserSummary.from(row, now), memberships.stream()
                    .map(member -> new UserMembership(member.getOrgUnitId(),
                            names.getOrDefault(member.getOrgUnitId(), ""), member.isPrimaryUnit()))
                    .toList(), heldPositions);
        }));
    }

    private UserCriteria criteria(long actorId, UserFilter filter)
    {
        String text = Strings.blankToNull(filter.text());
        Long unitId = filter.unitId();
        OrgUnit unit = unitId == null ? null : scopes.requireWithin(actorId, units, OrgUnit.class, DataAction.READ, unitId);
        String path = unit != null && filter.includeSubUnits() ? unit.getPath() : null;
        // Wildcards typed by the user are taken literally: dropped, so "50%" cannot match everything.
        String needle = text == null ? null : text.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "");
        boolean emailSearched = fields.read(actorId, "user", "email").mode() == FieldReadMode.VISIBLE;
        return new UserCriteria(needle, filter.state(), path, path == null ? unitId : null, emailSearched);
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

    private UserAccount require(long actorId, long id, DataAction action)
    {
        return scopes.requireWithin(actorId, accounts, UserAccount.class, action, id);
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
