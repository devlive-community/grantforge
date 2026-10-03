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
import org.devlive.grantforge.common.page.PageQuery;
import org.devlive.grantforge.common.page.PageResult;
import org.devlive.grantforge.identity.domain.AccountPosition;
import org.devlive.grantforge.identity.domain.AccountPositionRepository;
import org.devlive.grantforge.identity.domain.AccountStatus;
import org.devlive.grantforge.identity.domain.OrgMember;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.Position;
import org.devlive.grantforge.identity.domain.PositionRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.query.InClauseBatcher;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.FieldRules;
import org.devlive.grantforge.persistence.secured.FieldView;
import org.devlive.grantforge.persistence.secured.RowScopes;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Exports the bound tenant's accounts to a table and imports accounts from one. Departments and positions are
 * written as their codes, several separated by {@code ;}. An import creates accounts only (existing login names
 * are reported) and applies all rows or none; imported accounts must change their password at the first sign-in.
 * Callers need the matching permission, which the API checks.
 */
@Service
public final class UserTransferService
{
    /** Most rows one import accepts; every row's password is hashed, which takes time. */
    public static final int MAX_IMPORT_ROWS = 1000;

    /** Most accounts one export writes. */
    public static final int MAX_EXPORT_ROWS = 10_000;

    /** The columns of an export; an import reads the same names plus {@code password}. */
    public static final List<String> EXPORT_COLUMNS = List.of("username", "displayName", "email", "status",
            "primaryUnit", "otherUnits", "positions", "lastLoginAt");

    private static final int EXPORT_PAGE = 200;

    private final UserAdminService users;
    private final UserAccountRepository accounts;
    private final OrgUnitRepository units;
    private final OrgMemberRepository members;
    private final PositionRepository positions;
    private final AccountPositionRepository holdings;
    private final PasswordPolicy policy;
    private final PasswordService passwords;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final RowScopes scopes;
    private final FieldRules fields;

    /**
     * Creates the service.
     *
     * @param users lists accounts as the administration does
     * @param accounts user accounts
     * @param units departments
     * @param members department memberships
     * @param positions positions
     * @param holdings positions held by accounts
     * @param policy checks passwords without hashing them
     * @param passwords hashes the passwords of imported accounts
     * @param audit records imports
     * @param transactionManager opens transactions
     * @param clock source of the current time
     * @param scopes the accounts each actor may export and the departments and positions they may give
     * @param fields how each actor sees the secured fields, which exports hide and mask as the console does
     */
    public UserTransferService(UserAdminService users, UserAccountRepository accounts, OrgUnitRepository units,
            OrgMemberRepository members, PositionRepository positions, AccountPositionRepository holdings,
            PasswordPolicy policy, PasswordService passwords, AuditLog audit, PlatformTransactionManager transactionManager,
            Clock clock, RowScopes scopes, FieldRules fields)
    {
        this.fields = requireNonNull(fields, "fields");
        this.scopes = requireNonNull(scopes, "scopes");
        this.users = requireNonNull(users, "users");
        this.accounts = requireNonNull(accounts, "accounts");
        this.units = requireNonNull(units, "units");
        this.members = requireNonNull(members, "members");
        this.positions = requireNonNull(positions, "positions");
        this.holdings = requireNonNull(holdings, "holdings");
        this.policy = requireNonNull(policy, "policy");
        this.passwords = requireNonNull(passwords, "passwords");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Exports the accounts a filter matches that the actor may export, the newest first, at most {@value #MAX_EXPORT_ROWS}.
     * Secured fields are hidden or masked as for the actor in the console; a hidden field's column stays, empty.
     *
     * @param actorId the account asking
     * @param filter the filters, as for the user list
     * @return the header ({@link #EXPORT_COLUMNS}) and one row per account
     */
    // One page query per page read is the point of the loop.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public List<List<String>> export(long actorId, UserFilter filter)
    {
        List<UserSummary> found = new ArrayList<>();
        for (int page = 1; found.size() < MAX_EXPORT_ROWS; page++) {
            PageResult<UserSummary> batch = users.search(actorId, filter, new PageQuery(page, EXPORT_PAGE), DataAction.EXPORT);
            found.addAll(batch.items());
            if (batch.items().size() < EXPORT_PAGE) {
                break;
            }
        }
        List<UserSummary> exported = found.subList(0, Math.min(found.size(), MAX_EXPORT_ROWS));
        List<Long> ids = exported.stream().map(UserSummary::id).toList();
        FieldView email = fields.read(actorId, "user", "email");
        FieldView lastLogin = fields.read(actorId, "user", "lastLoginAt");
        return requireNonNull(transactions.execute(status -> {
            Map<Long, String> unitCodes = units.findAll().stream().collect(Collectors.toMap(OrgUnit::requireId, OrgUnit::getCode));
            Map<Long, String> positionCodes = positions.findAll().stream()
                    .collect(Collectors.toMap(Position::requireId, Position::getCode));
            Map<Long, List<OrgMember>> memberships = InClauseBatcher.query(ids, members::findByAccountIdIn).stream()
                    .collect(Collectors.groupingBy(OrgMember::getAccountId));
            Map<Long, List<AccountPosition>> held = InClauseBatcher.query(ids, holdings::findByAccountIdIn).stream()
                    .collect(Collectors.groupingBy(AccountPosition::getAccountId));
            List<List<String>> rows = new ArrayList<>();
            rows.add(EXPORT_COLUMNS);
            for (UserSummary user : exported) {
                List<OrgMember> of = memberships.getOrDefault(user.id(), List.of());
                rows.add(List.of(user.username(), text(user.displayName()), text(email.present(user.email())), state(user),
                        of.stream().filter(OrgMember::isPrimaryUnit).map(member -> unitCodes.get(member.getOrgUnitId()))
                                .findFirst().orElse(""),
                        joined(of.stream().filter(member -> !member.isPrimaryUnit()).map(OrgMember::getOrgUnitId).toList(),
                                unitCodes),
                        joined(held.getOrDefault(user.id(), List.of()).stream().map(AccountPosition::getPositionId).toList(),
                                positionCodes),
                        text(lastLogin.present(user.lastLoginAt()))));
            }
            return rows;
        }));
    }

    /**
     * Checks an import file and, when asked and every row is fine, creates its accounts in one transaction.
     * Required columns: {@code username}, {@code password}; optional: {@code displayName}, {@code email},
     * {@code primaryUnit}, {@code otherUnits} and {@code positions} (codes, several separated by {@code ;}).
     *
     * @param actorId the account asking
     * @param records the parsed file, header first
     * @param apply whether to create the accounts; {@code false} only checks
     * @return the report
     * @throws GrantForgeException with a file-level problem such as {@link IdentityErrorCode#IMPORT_MISSING_COLUMN}
     */
    public ImportReport importUsers(long actorId, List<List<String>> records, boolean apply)
    {
        ImportSheet sheet = new ImportSheet(records, Set.of("username", "password"), MAX_IMPORT_ROWS);
        // Only departments and positions the actor may see can be given; the others count as unknown.
        Map<String, Long> unitIds = requireNonNull(transactions.execute(status -> units.findAll(scopes.scope(actorId,
                OrgUnit.class, DataAction.READ)).stream().collect(Collectors.toMap(OrgUnit::getCode, OrgUnit::requireId))));
        Map<String, Long> positionIds = requireNonNull(transactions.execute(status -> positions.findAll(scopes.scope(actorId,
                Position.class, DataAction.READ)).stream().collect(Collectors.toMap(Position::getCode, Position::requireId))));
        Set<String> taken = takenNames(sheet);
        Instant now = clock.instant();
        List<ImportProblem> problems = new ArrayList<>();
        List<Planned> planned = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < sheet.size(); i++) {
            int row = ImportSheet.recordOf(i);
            int before = problems.size();
            String username = sheet.value(i, "username");
            String password = sheet.value(i, "password");
            if (username == null) {
                problems.add(problem(row, "username", IdentityErrorCode.IMPORT_REQUIRED_VALUE, "username"));
            }
            else if (!UserAccount.USERNAME.matcher(username).matches()) {
                problems.add(problem(row, "username", IdentityErrorCode.IMPORT_INVALID_VALUE, "username"));
            }
            else if (!seen.add(UserAccount.normalize(username))) {
                problems.add(problem(row, "username", IdentityErrorCode.IMPORT_DUPLICATE, username));
            }
            else if (taken.contains(UserAccount.normalize(username))) {
                problems.add(problem(row, "username", IdentityErrorCode.USERNAME_TAKEN, username));
            }
            if (password == null) {
                problems.add(problem(row, "password", IdentityErrorCode.IMPORT_REQUIRED_VALUE, "password"));
            }
            else {
                try {
                    policy.check(password, username);
                }
                catch (GrantForgeException weak) {
                    problems.add(new ImportProblem(row, "password", weak.getErrorCode(), weak.getArguments()));
                }
            }
            String displayName = sheet.value(i, "displayname");
            String email = sheet.value(i, "email");
            checkValue(problems, row, "displayName", () -> UserAccount.create("check", "x", now).withDisplayName(displayName));
            checkValue(problems, row, "email", () -> UserAccount.create("check", "x", now).withEmail(email));
            Long primary = lookup(problems, row, "primaryUnit", sheet.value(i, "primaryunit"), unitIds,
                    IdentityErrorCode.IMPORT_UNKNOWN_UNIT).stream().findFirst().orElse(null);
            List<Long> others = lookup(problems, row, "otherUnits", sheet.value(i, "otherunits"), unitIds,
                    IdentityErrorCode.IMPORT_UNKNOWN_UNIT);
            List<Long> held = lookup(problems, row, "positions", sheet.value(i, "positions"), positionIds,
                    IdentityErrorCode.IMPORT_UNKNOWN_POSITION);
            if (primary == null && !others.isEmpty()) {
                problems.add(problem(row, "primaryUnit", IdentityErrorCode.IMPORT_REQUIRED_VALUE, "primaryUnit"));
            }
            if (problems.size() == before && username != null && password != null) {
                planned.add(new Planned(username, password, displayName, email, primary, others, held));
            }
        }
        if (!apply || !problems.isEmpty()) {
            return new ImportReport(sheet.size(), 0, false, problems);
        }
        int created = create(planned, now);
        audit.record(new AuditRecord(AuditAction.USERS_IMPORTED, AuditOutcome.SUCCESS, TenantContext.requireTenantId(),
                actorId, null, null, Integer.toString(created)));
        return new ImportReport(sheet.size(), created, true, List.of());
    }

    private int create(List<Planned> planned, Instant now)
    {
        // Hashing is slow on purpose; do it before the transaction opens so it holds no locks meanwhile.
        Map<Planned, String> hashes = new HashMap<>();
        planned.forEach(row -> hashes.put(row, passwords.hashNew(row.password(), row.username())));
        try {
            return requireNonNull(transactions.execute(status -> {
                for (Planned row : planned) {
                    UserAccount account = UserAccount.create(row.username(), requireNonNull(hashes.get(row)), now)
                            .withDisplayName(row.displayName()).withEmail(row.email());
                    account.requirePasswordChange();
                    long id = accounts.save(account).requireId();
                    Long primary = row.primary();
                    if (primary != null) {
                        members.save(OrgMember.of(id, primary, true));
                        row.others().stream().filter(unit -> !unit.equals(primary))
                                .forEach(unit -> members.save(OrgMember.of(id, unit, false)));
                    }
                    row.positions().forEach(position -> holdings.save(AccountPosition.of(id, position)));
                }
                accounts.flush();
                return planned.size();
            }));
        }
        catch (DataIntegrityViolationException race) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "accounts changed during the import", race);
        }
    }

    private Set<String> takenNames(ImportSheet sheet)
    {
        Set<String> names = new LinkedHashSet<>();
        for (int i = 0; i < sheet.size(); i++) {
            String username = sheet.value(i, "username");
            if (username != null) {
                names.add(UserAccount.normalize(username));
            }
        }
        // Login names are unique across tenants, so look in every tenant.
        return TenantContext.callAsSystem(() -> requireNonNull(transactions.execute(status ->
                InClauseBatcher.query(names, accounts::findByUsernameNormIn).stream().map(UserAccount::getUsernameNorm)
                        .collect(Collectors.toSet()))));
    }

    private static List<Long> lookup(List<ImportProblem> problems, int row, String column, @Nullable String value,
            Map<String, Long> ids, IdentityErrorCode unknown)
    {
        if (value == null) {
            return List.of();
        }
        List<Long> found = new ArrayList<>();
        for (String code : Arrays.stream(value.split("[;,]")).map(String::trim).filter(code -> !code.isEmpty()).toList()) {
            Long id = ids.get(code.toLowerCase(Locale.ROOT));
            if (id == null) {
                problems.add(problem(row, column, unknown, code));
            }
            else if (!found.contains(id)) {
                found.add(id);
            }
        }
        return found;
    }

    private static void checkValue(List<ImportProblem> problems, int row, String column, Runnable check)
    {
        try {
            check.run();
        }
        catch (IllegalArgumentException invalid) {
            problems.add(problem(row, column, IdentityErrorCode.IMPORT_INVALID_VALUE, column));
        }
    }

    private static ImportProblem problem(int row, String column, IdentityErrorCode code, Object argument)
    {
        return new ImportProblem(row, column, code, List.of(argument));
    }

    private static String state(UserSummary user)
    {
        if (user.lockedUntil() != null) {
            return "LOCKED";
        }
        return user.status() == AccountStatus.ACTIVE ? "ACTIVE" : "DISABLED";
    }

    private static String joined(List<Long> ids, Map<Long, String> codes)
    {
        return ids.stream().map(codes::get).filter(code -> code != null).sorted().collect(Collectors.joining(";"));
    }

    private static String text(@Nullable Object value)
    {
        return value == null ? "" : value.toString();
    }

    /** A row ready to become an account. */
    private record Planned(String username, String password, @Nullable String displayName, @Nullable String email,
            @Nullable Long primary, List<Long> others, List<Long> positions)
    {
    }
}
