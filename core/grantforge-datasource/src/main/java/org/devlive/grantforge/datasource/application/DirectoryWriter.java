// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.application;

import org.devlive.grantforge.datasource.domain.DataSource;
import org.devlive.grantforge.datasource.domain.DirectoryAccount;
import org.devlive.grantforge.datasource.domain.DirectoryAccountRepository;
import org.devlive.grantforge.datasource.domain.DirectoryDepartment;
import org.devlive.grantforge.datasource.domain.DirectoryDepartmentRepository;
import org.devlive.grantforge.datasource.domain.DirectoryEmployee;
import org.devlive.grantforge.datasource.domain.DirectoryEmployeeRepository;
import org.devlive.grantforge.datasource.provider.DirectoryProvider;
import org.devlive.grantforge.identity.domain.OrgMember;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.persistence.authz.AuthorizationChanges;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Applies provider-neutral snapshots and login bindings under the data source lock, in the caller's transaction.
 */
@Component
public final class DirectoryWriter
{
    private final DirectoryDepartmentRepository mappings;
    private final DirectoryEmployeeRepository employees;
    private final DirectoryAccountRepository accounts;
    private final OrgUnitRepository units;
    private final OrgMemberRepository members;
    private final AuthorizationChanges changes;

    /**
     * Uses existing organization models and authorization version tracking.
     *
     * @param mappings  external department identifiers
     * @param employees complete employee snapshots
     * @param accounts  stable login bindings
     * @param units     local departments
     * @param members   local memberships
     * @param changes   permission cache invalidation
     */
    public DirectoryWriter(DirectoryDepartmentRepository mappings, DirectoryEmployeeRepository employees, DirectoryAccountRepository accounts,
                           OrgUnitRepository units, OrgMemberRepository members, AuthorizationChanges changes)
    {
        this.mappings = mappings;
        this.employees = employees;
        this.accounts = accounts;
        this.units = units;
        this.members = members;
        this.changes = changes;
    }

    /**
     * Reconciles a trusted login, including repairing existing bindings without remote calls.
     *
     * @param source    the locked data source
     * @param accountId authenticated account
     * @param email     verified OIDC email
     * @return whether an employee is linked
     */
    public boolean login(DataSource source, long accountId, @Nullable String email)
    {
        long sourceId = source.requireId();
        DirectoryAccount link = accounts.findBySourceIdAndAccountId(sourceId, accountId).orElse(null);
        if (link == null) {
            link = accounts.save(DirectoryAccount.of(sourceId, accountId));
        }
        link.remember(email);
        DirectoryEmployee employee;
        if (link.getEmployeeId() != null) {
            employee = employees.findBySourceIdAndUserId(sourceId, requireNonNull(link.getEmployeeId())).orElse(null);
        }
        else {
            String address = link.getTrustedEmail();
            List<DirectoryEmployee> matches = address == null ? List.of() : employees.findBySourceIdAndEmailAndPresentInDirectoryTrue(sourceId, address);
            if (matches.size() != 1 || accounts.countBySourceIdAndTrustedEmail(sourceId, requireNonNull(address)) != 1
                    || accounts.existsBySourceIdAndEmployeeId(sourceId, matches.get(0).getUserId())) {
                return false;
            }
            employee = matches.get(0);
            link.bind(employee.getUserId());
        }
        if (employee == null) {
            return false;
        }
        Map<String, OrgUnit> departments = departments(source.requireId());
        memberships(accountId, employee.departments(), departments, owned(departments), members.findByAccount(accountId));
        return true;
    }

    /**
     * Applies a complete snapshot atomically; absent departments and bindings are retained.
     *
     * @param source   the locked data source
     * @param snapshot complete provider output
     * @return applied counts
     */
    public DirectoryReport apply(DataSource source, DirectoryProvider.Snapshot snapshot)
    {
        long sourceId = source.requireId();
        Map<String, OrgUnit> departments = departments(sourceId);
        OrgUnit mount = source.getParentUnitId() == null ? null : units.findById(source.getParentUnitId()).orElseThrow(() ->
            DataSourceErrors.invalid("directory mount department missing"));
        if (mount != null && mount.isDirectoryManaged()) {
            throw DataSourceErrors.invalid("directory mount must be a local department");
        }
        int created = 0;
        int updated = 0;
        Set<String> processed = new HashSet<>();
        int roots = 0;
        for (var department : snapshot.departments()) {
            identifier(department.id());
            if (!processed.add(department.id()) || (department.parentId() != null && !processed.contains(department.parentId()))) {
                throw DataSourceErrors.invalid("directory parents must precede children");
            }
            if (department.parentId() == null) {
                roots++;
            }
            OrgUnit parent = department.parentId() == null ? mount : requireNonNull(departments.get(department.parentId()));
            OrgUnit unit = departments.get(department.id());
            if (unit == null) {
                unit = OrgUnit.create(parent, "ds-" + sourceId + "-" + java.util.UUID.randomUUID().toString().replace("-", ""), department.name(), department.order());
                unit.manageExternally();
                units.save(unit);
                mappings.save(DirectoryDepartment.of(sourceId, department.id(), unit.requireId()));
                departments.put(department.id(), unit);
                created++;
            }
            else {
                String path = (parent == null ? "/" : parent.getPath()) + unit.requireId() + "/";
                if (!unit.getName().equals(department.name()) || !unit.getPath().equals(path) || unit.getSortOrder() != department.order()) {
                    unit.rename(unit.getCode(), department.name());
                    unit.relocate(parent);
                    unit.placeAt(department.order());
                    updated++;
                }
            }
        }
        if (roots != 1) {
            throw DataSourceErrors.invalid("directory snapshot must have one root");
        }
        Map<Long, OrgUnit> byLocalId = departments.values().stream().collect(Collectors.toMap(OrgUnit::requireId, unit -> unit));
        Set<Long> rebuilt = new HashSet<>();
        while (rebuilt.size() < byLocalId.size()) {
            int before = rebuilt.size();
            for (OrgUnit unit : byLocalId.values()) {
                Long parentId = unit.getParentId();
                if (!rebuilt.contains(unit.requireId()) && (!byLocalId.containsKey(parentId) || rebuilt.contains(parentId))) {
                    unit.relocate(byLocalId.containsKey(parentId) ? byLocalId.get(parentId) : mount);
                    rebuilt.add(unit.requireId());
                }
            }
            if (before == rebuilt.size()) {
                throw DataSourceErrors.invalid("directory hierarchy contains a cycle");
            }
        }
        Map<String, DirectoryEmployee> known = employeeMap(sourceId);
        Set<String> seen = new HashSet<>();
        for (var employee : snapshot.employees()) {
            identifier(employee.id());
            if (!seen.add(employee.id()) || employee.departments().stream().anyMatch(id -> !processed.contains(id))) {
                throw DataSourceErrors.invalid("invalid employee memberships");
            }
            DirectoryEmployee user = known.computeIfAbsent(employee.id(), id -> DirectoryEmployee.create(sourceId, id));
            user.refresh(employee.email(), employee.departments(), true);
        }
        for (DirectoryEmployee user : known.values()) {
            if (!seen.contains(user.getUserId())) {
                user.refresh(user.getEmail(), List.of(), false);
            }
        }
        employees.saveAll(known.values());
        List<DirectoryAccount> links = accounts.findBySourceId(sourceId);
        bind(known, links);
        List<Long> bound = links.stream().filter(link -> link.getEmployeeId() != null).map(DirectoryAccount::getAccountId).toList();
        Map<Long, List<OrgMember>> current = new HashMap<>();
        for (int offset = 0; offset < bound.size(); offset += 500) {
            members.findByAccountIdIn(bound.subList(offset, Math.min(offset + 500, bound.size())))
                .forEach(member -> current.computeIfAbsent(member.getAccountId(), ignored -> new ArrayList<>()).add(member));
        }
        int added = 0;
        int removed = 0;
        int linked = 0;
        Set<Long> owned = owned(departments);
        for (DirectoryAccount link : links) {
            DirectoryEmployee user = link.getEmployeeId() == null ? null : known.get(link.getEmployeeId());
            if (user != null) {
                int[] delta = memberships(link.getAccountId(), user.departments(), departments, owned, current.getOrDefault(link.getAccountId(), List.of()));
                added += delta[0];
                removed += delta[1];
                if (user.isPresentInDirectory()) {
                    linked++;
                }
            }
        }
        List<String> problems = new ArrayList<>();
        Map<String, Long> counts = emailCounts(known);
        for (DirectoryEmployee user : known.values()) {
            if (user.isPresentInDirectory() && (user.getEmail() == null || counts.getOrDefault(user.getEmail(), 0L) > 1) && problems.size() < 20) {
                problems.add("employee " + user.getUserId() + ": missing or duplicate email");
            }
        }
        return new DirectoryReport(snapshot.employees().size(), created, updated, added, removed, linked, snapshot.employees().size() - linked,
            departments.size() - processed.size(), problems);
    }

    private Map<String, DirectoryEmployee> employeeMap(long sourceId)
    {
        return employees.findBySourceId(sourceId).stream().collect(Collectors.toMap(DirectoryEmployee::getUserId, user -> user));
    }

    private Map<String, OrgUnit> departments(long sourceId)
    {
        List<DirectoryDepartment> mapped = mappings.findBySourceId(sourceId);
        Map<Long, OrgUnit> locals = new HashMap<>();
        List<Long> ids = mapped.stream().map(DirectoryDepartment::getOrgUnitId).toList();
        for (int offset = 0; offset < ids.size(); offset += 500) {
            units.findAllById(ids.subList(offset, Math.min(offset + 500, ids.size()))).forEach(unit -> locals.put(unit.requireId(), unit));
        }
        return mapped.stream().collect(Collectors.toMap(DirectoryDepartment::getExternalId, mapping -> requireNonNull(locals.get(mapping.getOrgUnitId()))));
    }

    private static Set<Long> owned(Map<String, OrgUnit> departments)
    {
        return departments.values().stream().map(OrgUnit::requireId).collect(Collectors.toSet());
    }

    private static Map<String, Long> emailCounts(Map<String, DirectoryEmployee> known)
    {
        return known.values().stream().filter(DirectoryEmployee::isPresentInDirectory).map(DirectoryEmployee::getEmail).filter(Objects::nonNull)
            .collect(Collectors.groupingBy(email -> email, Collectors.counting()));
    }

    private static void bind(Map<String, DirectoryEmployee> known, List<DirectoryAccount> links)
    {
        Map<String, Long> counts = emailCounts(known);
        Map<String, List<DirectoryAccount>> candidates = links.stream().filter(link -> link.getTrustedEmail() != null)
            .collect(Collectors.groupingBy(link -> requireNonNull(link.getTrustedEmail())));
        Set<String> bound = links.stream().map(DirectoryAccount::getEmployeeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, DirectoryEmployee> unique = known.values().stream().filter(user -> user.isPresentInDirectory() && user.getEmail() != null
            && counts.getOrDefault(user.getEmail(), 0L) == 1).collect(Collectors.toMap(user -> requireNonNull(user.getEmail()), user -> user));
        for (DirectoryAccount link : links) {
            String email = link.getTrustedEmail();
            DirectoryEmployee user = email == null ? null : unique.get(email);
            if (link.getEmployeeId() == null && user != null && candidates.getOrDefault(email, List.of()).size() == 1 && bound.add(user.getUserId())) {
                link.bind(user.getUserId());
            }
        }
    }

    private static void identifier(String id)
    {
        if (id.isBlank() || id.length() > 255) {
            throw DataSourceErrors.invalid("invalid directory identifier");
        }
    }

    private int[] memberships(long account, List<String> externalIds, Map<String, OrgUnit> departments, Set<Long> owned, List<OrgMember> current)
    {
        Set<Long> wanted = externalIds.stream().sorted(java.util.Comparator.comparingInt(String::length).thenComparing(java.util.Comparator.naturalOrder())).map(id -> requireNonNull(departments.get(id)).requireId())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<OrgMember> retained = current.stream().filter(member -> !owned.contains(member.getOrgUnitId())).toList();
        Long primary = retained.stream().filter(OrgMember::isPrimaryUnit).map(OrgMember::getOrgUnitId).findFirst().orElse(null);
        if (primary == null) {
            primary = current.stream().filter(OrgMember::isPrimaryUnit).map(OrgMember::getOrgUnitId).filter(wanted::contains)
                .findFirst().orElse(wanted.stream().findFirst().orElse(retained.isEmpty() ? null : retained.get(0).getOrgUnitId()));
        }
        Map<Long, Boolean> desired = new LinkedHashMap<>();
        for (OrgMember member : retained) {
            desired.put(member.getOrgUnitId(), Objects.equals(primary, member.getOrgUnitId()));
        }
        for (Long id : wanted) {
            desired.put(id, Objects.equals(primary, id));
        }
        Set<Long> kept = new HashSet<>();
        int removed = 0;
        for (OrgMember member : current) {
            Boolean desiredPrimary = desired.get(member.getOrgUnitId());
            if (desiredPrimary == null || desiredPrimary != member.isPrimaryUnit()) {
                members.delete(member);
                removed++;
            }
            else {
                kept.add(member.getOrgUnitId());
            }
        }
        // Flush deletes before inserting the same (account, department) with a changed primary flag.
        if (removed > 0) {
            members.flush();
        }
        int added = 0;
        for (var entry : desired.entrySet()) {
            if (!kept.contains(entry.getKey())) {
                members.save(OrgMember.of(account, entry.getKey(), entry.getValue()));
                added++;
            }
        }
        if (added > 0 || removed > 0) {
            changes.currentTenant();
        }
        return new int[]{added, removed};
    }
}
