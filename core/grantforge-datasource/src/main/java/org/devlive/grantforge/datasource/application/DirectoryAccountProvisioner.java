// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.datasource.application;

import org.devlive.grantforge.datasource.domain.DataSource;
import org.devlive.grantforge.datasource.domain.DirectoryAccount;
import org.devlive.grantforge.datasource.domain.DirectoryAccountRepository;
import org.devlive.grantforge.datasource.provider.DirectoryProvider.Employee;
import org.devlive.grantforge.identity.application.DirectoryUser;
import org.devlive.grantforge.identity.application.ExternalAccounts;
import org.devlive.grantforge.identity.domain.ExternalIdentity;
import org.devlive.grantforge.identity.domain.ExternalIdentityRepository;
import org.devlive.grantforge.identity.domain.IdentitySource;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Applies resolved identities without moving any existing employee binding. */
@Component
public final class DirectoryAccountProvisioner
{
    private final ExternalAccounts identities;
    private final ExternalIdentityRepository external;
    private final DirectoryAccountRepository accounts;
    private final UserAccountRepository users;

    /**
     * Creates the directory account provisioner.
     * @param identities account lifecycle service
     * @param external login bindings
     * @param accounts employee bindings
     * @param users persisted account profiles
     */
    public DirectoryAccountProvisioner(ExternalAccounts identities, ExternalIdentityRepository external, DirectoryAccountRepository accounts, UserAccountRepository users)
    {
        this.identities = identities;
        this.external = external;
        this.accounts = accounts;
        this.users = users;
    }

    /**
     * Creates or links explicitly resolved employees before the membership writer runs.
     * @param source the locked data source
     * @param identity the locked OIDC source
     * @param employees the complete directory snapshot
     * @param subjects confirmed OIDC subjects by employee ID
     * @param problems bounded synchronization diagnostics
     */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops") // Each matched employee has its own identity data.
    public void apply(DataSource source, IdentitySource identity, List<Employee> employees, Map<String, String> subjects, List<String> problems)
    {
        Map<String, DirectoryAccount> byEmployee = new HashMap<>();
        Map<Long, DirectoryAccount> byAccount = new HashMap<>();
        for (DirectoryAccount link : accounts.findBySourceId(source.requireId())) {
            byAccount.put(link.getAccountId(), link);
            String employee = link.getEmployeeId();
            if (employee != null) {
                byEmployee.put(employee, link);
            }
        }
        for (Employee employee : employees) {
            if (byEmployee.containsKey(employee.id())) {
                continue;
            }
            String subject = subjects.get(employee.id());
            if (subject == null) {
                problem(problems, employee.id(), "OIDC mapping pending");
                continue;
            }
            ExternalIdentity existing = external.findBySourceIdAndExternalId(identity.requireId(), subject).orElse(null);
            DirectoryAccount current = existing == null ? null : byAccount.get(existing.getAccountId());
            if (current != null && current.getEmployeeId() != null) {
                problem(problems, employee.id(), "OIDC account already assigned to another employee");
                continue;
            }
            String key = identity.requireId() + ":" + subject;
            String username = "staff-" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
            long accountId = identities.preProvision(identity, new DirectoryUser(subject, username, employee.displayName(),
                    employee.email(), employee.phone(), null)).requireId();
            DirectoryAccount link = current == null ? DirectoryAccount.of(source.requireId(), accountId) : current;
            link.bind(employee.id());
            accounts.save(link);
            byAccount.put(accountId, link);
            byEmployee.put(employee.id(), link);
        }
    }

    void refreshProfiles(DataSource source, List<Employee> employees, Set<String> changedEmployees)
    {
        Map<String, Employee> changed = employees.stream().filter(employee -> changedEmployees.contains(employee.id()))
                .collect(Collectors.toMap(Employee::id, Function.identity()));
        if (changed.isEmpty()) {
            return;
        }
        for (DirectoryAccount binding : accounts.findBySourceId(source.requireId())) {
            Employee employee = changed.get(binding.getEmployeeId());
            if (employee != null) {
                refreshProfile(source, binding.getAccountId(), employee);
            }
        }
    }

    private void refreshProfile(DataSource source, long accountId, Employee employee)
    {
        ExternalIdentity link = external.findByAccountId(accountId).orElse(null);
        if (link == null || link.getSourceId() != source.getIdentitySourceId()) {
            return;
        }
        users.findById(accountId).filter(account -> !account.isSystemAccount()).ifPresent(account ->
                identities.refresh(account, new DirectoryUser(link.getExternalId(), account.getUsername(),
                        employee.displayName() == null ? account.getDisplayName() : employee.displayName(),
                        employee.email() == null ? account.getEmail() : employee.email(), employee.phone(), null)));
    }

    private static void problem(List<String> problems, String employeeId, String detail)
    {
        if (problems.size() < 20) {
            problems.add("employee " + employeeId + ": " + detail);
        }
    }
}
