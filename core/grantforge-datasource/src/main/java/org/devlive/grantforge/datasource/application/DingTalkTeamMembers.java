// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.common.error.GrantForgeException;
import org.devlive.grantforge.datasource.domain.DataSource;
import org.devlive.grantforge.datasource.domain.DataSourceRepository;
import org.devlive.grantforge.datasource.domain.DirectoryAccount;
import org.devlive.grantforge.datasource.domain.DirectoryAccountRepository;
import org.devlive.grantforge.datasource.domain.DirectoryDepartment;
import org.devlive.grantforge.datasource.domain.DirectoryDepartmentRepository;
import org.devlive.grantforge.datasource.domain.DirectoryEmployee;
import org.devlive.grantforge.datasource.domain.DirectoryEmployeeRepository;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Finds a user's team in the tenant's synchronized DingTalk directory. */
@Service
public class DingTalkTeamMembers
{
    private static final String DINGTALK = "DINGTALK";

    private final DataSourceRepository sources;
    private final DirectoryAccountRepository accounts;
    private final DirectoryEmployeeRepository employees;
    private final DirectoryDepartmentRepository departments;
    private final OrgUnitRepository units;

    /**
     * Creates the directory reader.
     * @param sources directory configurations
     * @param accounts stable account bindings
     * @param employees synchronized employees
     * @param departments directory department mappings
     * @param units local department hierarchy
     */
    public DingTalkTeamMembers(DataSourceRepository sources, DirectoryAccountRepository accounts,
            DirectoryEmployeeRepository employees, DirectoryDepartmentRepository departments, OrgUnitRepository units)
    {
        this.sources = sources;
        this.accounts = accounts;
        this.employees = employees;
        this.departments = departments;
        this.units = units;
    }

    /**
     * Returns employees of the signed-in account's direct departments and their descendants.
     * @param accountId the authenticated account in the current tenant
     * @return the team, or an empty list if the caller lacks an active directory binding
     * @throws GrantForgeException if there is more than one eligible organization
     */
    @Transactional
    public List<DingTalkTeamMember> forAccount(long accountId)
    {
        List<DirectoryAccount> bindings = accounts.findByAccountId(accountId).stream()
                .filter(binding -> binding.getEmployeeId() != null)
                .filter(binding -> sources.existsByIdAndTypeAndEnabledTrue(binding.getSourceId(), DINGTALK))
                .toList();
        if (bindings.size() > 1) {
            throw new GrantForgeException(CommonErrorCode.CONFLICT, "the account is bound to multiple enabled DingTalk directories");
        }
        if (bindings.isEmpty()) {
            return List.of();
        }
        DirectoryAccount binding = bindings.get(0);
        long sourceId = binding.getSourceId();
        // Serialize with snapshot publication so a department move cannot mix two sync generations.
        if (sources.lockById(sourceId).filter(DingTalkTeamMembers::eligible).isEmpty()) {
            return List.of();
        }
        String employeeId = binding.getEmployeeId();
        if (employeeId == null) {
            return List.of();
        }
        return employees.findBySourceIdAndUserId(sourceId, employeeId)
                .filter(DirectoryEmployee::isPresentInDirectory)
                .map(employee -> membersBelow(sourceId, employee.departments()))
                .orElseGet(List::of);
    }

    private static boolean eligible(DataSource source)
    {
        return source.getEnabled() && DINGTALK.equals(source.getType());
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // Each mapped parent needs its own child list.
    private List<DingTalkTeamMember> membersBelow(long sourceId, List<String> roots)
    {
        if (roots.isEmpty()) {
            return List.of();
        }
        List<DirectoryDepartment> mappings = departments.findBySourceId(sourceId);
        Map<Long, String> byLocalId = mappings.stream()
                .collect(Collectors.toMap(DirectoryDepartment::getOrgUnitId, DirectoryDepartment::getExternalId));
        Set<String> mapped = new HashSet<>(byLocalId.values());
        Map<String, List<String>> children = new HashMap<>();
        for (var unit : units.findTree()) {
            String id = byLocalId.get(unit.requireId());
            String parent = byLocalId.get(unit.getParentId());
            if (id != null && parent != null) {
                children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(id);
            }
        }
        Set<String> included = new HashSet<>();
        var pending = new ArrayDeque<>(roots.stream().filter(mapped::contains).toList());
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (included.add(id)) {
                pending.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return employees.findBySourceIdAndPresentInDirectoryTrue(sourceId).stream()
                .filter(employee -> employee.departments().stream().anyMatch(included::contains))
                .sorted(Comparator.comparing(DirectoryEmployee::getUserId))
                .map(employee -> new DingTalkTeamMember(employee.getUserId(), employee.getPhone(), employee.getEmail()))
                .toList();
    }

    /**
     * One active DingTalk employee, even if the employee has never signed into GrantForge.
     * @param id stable DingTalk userid in the selected organization
     * @param mobile normalized phone, if provided by DingTalk
     * @param email normalized email, if provided by DingTalk
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DingTalkTeamMember(String id, @Nullable String mobile, @Nullable String email)
    {
    }
}
