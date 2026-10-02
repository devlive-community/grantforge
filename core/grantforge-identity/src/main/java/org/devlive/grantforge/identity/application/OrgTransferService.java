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
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.persistence.secured.DataAction;
import org.devlive.grantforge.persistence.secured.RowScopes;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Exports the bound tenant's organization tree to a table and imports departments from one. An import creates
 * departments only (existing codes are reported); a row's parent may be an existing department or another row of
 * the file, in any order. All rows are created or none. Callers need the matching permission, which the API checks.
 */
@Service
public final class OrgTransferService
{
    /** Most rows one import accepts. */
    public static final int MAX_IMPORT_ROWS = 5000;

    /** The columns of an export and of an import ({@code parentCode} and {@code sortOrder} are optional). */
    public static final List<String> COLUMNS = List.of("code", "name", "parentCode", "sortOrder");

    private final OrgUnitRepository units;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final RowScopes scopes;

    /**
     * Creates the service.
     *
     * @param units departments
     * @param audit records imports
     * @param transactionManager opens transactions
     * @param scopes the departments each actor may export or add departments below
     */
    public OrgTransferService(OrgUnitRepository units, AuditLog audit,
            PlatformTransactionManager transactionManager, RowScopes scopes)
    {
        this.scopes = requireNonNull(scopes, "scopes");
        this.units = requireNonNull(units, "units");
        this.audit = requireNonNull(audit, "audit");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
    }

    /**
     * Exports the departments an actor may export, each after its parent and siblings in order. A department whose parent
     * is not exported is exported as a root, without the parent's code.
     *
     * @param actorId the account asking
     * @return the header ({@link #COLUMNS}) and one row per department
     */
    public List<List<String>> export(long actorId)
    {
        return requireNonNull(transactions.execute(status -> {
            List<OrgUnit> tree = units.findAll(scopes.scope(actorId, OrgUnit.class, DataAction.EXPORT), OrgUnitRepository.TREE_ORDER);
            Map<Long, String> codes = tree.stream().collect(Collectors.toMap(OrgUnit::requireId, OrgUnit::getCode));
            // The tree order lists siblings in order, so each group keeps that order.
            Map<Long, List<OrgUnit>> children = tree.stream().filter(unit -> unit.getParentId() != null)
                    .collect(Collectors.groupingBy(unit -> requireNonNull(unit.getParentId())));
            List<OrgUnit> roots = tree.stream().filter(unit -> !codes.containsKey(unit.getParentId())).toList();
            List<List<String>> rows = new ArrayList<>();
            rows.add(COLUMNS);
            // Depth first, so a spreadsheet shows each department right below its parent.
            List<OrgUnit> pending = new ArrayList<>(roots);
            Collections.reverse(pending);
            while (!pending.isEmpty()) {
                OrgUnit unit = pending.remove(pending.size() - 1);
                Long parent = unit.getParentId();
                rows.add(List.of(unit.getCode(), unit.getName(), parent == null ? "" : codes.getOrDefault(parent, ""),
                        Integer.toString(unit.getSortOrder())));
                List<OrgUnit> below = children.getOrDefault(unit.requireId(), List.of());
                for (int i = below.size() - 1; i >= 0; i--) {
                    pending.add(below.get(i));
                }
            }
            return rows;
        }));
    }

    /**
     * Checks an import file and, when asked and every row is fine, creates its departments in one transaction.
     *
     * @param actorId the account asking
     * @param records the parsed file, header first; columns {@code code}, {@code name}, optionally
     *        {@code parentCode} and {@code sortOrder}
     * @param apply whether to create the departments; {@code false} only checks
     * @return the report
     * @throws GrantForgeException with a file-level problem such as {@link IdentityErrorCode#IMPORT_MISSING_COLUMN}
     */
    public ImportReport importUnits(long actorId, List<List<String>> records, boolean apply)
    {
        ImportSheet sheet = new ImportSheet(records, Set.of("code", "name"), MAX_IMPORT_ROWS);
        Map<String, OrgUnit> existing = requireNonNull(transactions.execute(status -> units.findTree().stream()
                .collect(Collectors.toMap(OrgUnit::getCode, Function.identity()))));
        // New departments go only below departments the actor may change; the others count as unknown.
        Set<String> parents = requireNonNull(transactions.execute(status -> units.findAll(scopes.scope(actorId, OrgUnit.class,
                DataAction.UPDATE)).stream().map(OrgUnit::getCode).collect(Collectors.toSet())));
        List<ImportProblem> problems = new ArrayList<>();
        Map<String, Row> rows = new LinkedHashMap<>();
        for (int i = 0; i < sheet.size(); i++) {
            Row row = read(sheet, i, existing, rows, problems);
            if (row != null) {
                rows.put(row.code(), row);
            }
        }
        for (Row row : rows.values()) {
            String parent = row.parentCode();
            if (parent != null && !parents.contains(parent) && !rows.containsKey(parent)) {
                problems.add(problem(row.record(), "parentCode", IdentityErrorCode.IMPORT_UNKNOWN_UNIT, parent));
            }
        }
        List<OrgUnit> created = problems.isEmpty() ? build(rows, existing, problems) : List.of();
        if (!apply || !problems.isEmpty()) {
            problems.sort((left, right) -> Integer.compare(left.row(), right.row()));
            return new ImportReport(sheet.size(), 0, false, problems);
        }
        try {
            transactions.executeWithoutResult(status -> {
                units.saveAll(created);
                units.flush();
            });
        }
        catch (DataIntegrityViolationException race) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "departments changed during the import", race);
        }
        audit.record(new AuditRecord(AuditAction.ORG_UNITS_IMPORTED, AuditOutcome.SUCCESS, TenantContext.requireTenantId(),
                actorId, null, null, Integer.toString(created.size())));
        return new ImportReport(sheet.size(), created.size(), true, List.of());
    }

    private static @Nullable Row read(ImportSheet sheet, int index, Map<String, OrgUnit> existing, Map<String, Row> rows,
            List<ImportProblem> problems)
    {
        int record = ImportSheet.recordOf(index);
        int before = problems.size();
        String rawCode = sheet.value(index, "code");
        String name = sheet.value(index, "name");
        String code = rawCode == null ? null : rawCode.toLowerCase(Locale.ROOT);
        if (code == null) {
            problems.add(problem(record, "code", IdentityErrorCode.IMPORT_REQUIRED_VALUE, "code"));
        }
        else if (rows.containsKey(code)) {
            problems.add(problem(record, "code", IdentityErrorCode.IMPORT_DUPLICATE, code));
        }
        else if (existing.containsKey(code)) {
            problems.add(problem(record, "code", IdentityErrorCode.ORG_CODE_TAKEN, code));
        }
        if (name == null) {
            problems.add(problem(record, "name", IdentityErrorCode.IMPORT_REQUIRED_VALUE, "name"));
        }
        if (code != null && name != null) {
            try {
                OrgUnit.create(null, code, name, 0);
            }
            catch (IllegalArgumentException invalid) {
                problems.add(problem(record, "code/name", IdentityErrorCode.IMPORT_INVALID_VALUE, "code/name"));
            }
        }
        int order = 0;
        String sortOrder = sheet.value(index, "sortorder");
        if (sortOrder != null) {
            try {
                order = Integer.parseInt(sortOrder);
            }
            catch (NumberFormatException invalid) {
                order = -1;
            }
            if (order < 0) {
                problems.add(problem(record, "sortOrder", IdentityErrorCode.IMPORT_INVALID_VALUE, "sortOrder"));
            }
        }
        String parent = sheet.value(index, "parentcode");
        if (problems.size() > before || code == null || name == null) {
            return null;
        }
        return new Row(record, code, name, parent == null ? null : parent.toLowerCase(Locale.ROOT), order);
    }

    /**
     * Creates the departments parents first. Rows whose parent chain never reaches an existing department or a
     * root belong to a cycle within the file; rows that would nest too deep are refused too.
     */
    private static List<OrgUnit> build(Map<String, Row> rows, Map<String, OrgUnit> existing, List<ImportProblem> problems)
    {
        Map<String, OrgUnit> made = new HashMap<>();
        List<OrgUnit> created = new ArrayList<>();
        boolean progress = true;
        while (progress && made.size() < rows.size()) {
            progress = false;
            for (Row row : rows.values()) {
                if (made.containsKey(row.code())) {
                    continue;
                }
                String parentCode = row.parentCode();
                OrgUnit parent = parentCode == null ? null : existing.getOrDefault(parentCode, made.get(parentCode));
                if (parentCode != null && parent == null) {
                    continue;
                }
                if (parent != null && parent.getDepth() >= OrgUnit.MAX_DEPTH) {
                    problems.add(problem(row.record(), "parentCode", IdentityErrorCode.ORG_TOO_DEEP, OrgUnit.MAX_DEPTH + 1));
                    // Stand in for the refused row so its descendants are not reported as a cycle.
                    made.put(row.code(), parent);
                }
                else {
                    OrgUnit unit = OrgUnit.create(parent, row.code(), row.name(), row.sortOrder());
                    made.put(row.code(), unit);
                    created.add(unit);
                }
                progress = true;
            }
        }
        rows.values().stream().filter(row -> !made.containsKey(row.code())).forEach(row ->
                problems.add(new ImportProblem(row.record(), "parentCode", IdentityErrorCode.ORG_MOVE_CYCLE, List.of())));
        return created;
    }

    private static ImportProblem problem(int row, String column, IdentityErrorCode code, Object argument)
    {
        return new ImportProblem(row, column, code, List.of(argument));
    }

    /** A valid row of the file. */
    private record Row(int record, String code, String name, @Nullable String parentCode, int sortOrder)
    {
    }
}
