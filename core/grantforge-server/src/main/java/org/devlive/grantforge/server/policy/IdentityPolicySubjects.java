// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.server.policy;

import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.identity.domain.GroupRow;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserCriteria;
import org.devlive.grantforge.identity.domain.UserGroupRepository;
import org.devlive.grantforge.identity.domain.UserRow;
import org.devlive.grantforge.service.policy.PolicySubjects;
import org.devlive.grantforge.service.policy.SubjectKind;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * The users, groups and roles of the bound tenant, for policy items: users by user name (compared case-insensitively),
 * groups and roles by code. Must be called in a transaction with the tenant bound.
 */
@Component
public final class IdentityPolicySubjects
        implements PolicySubjects
{
    private final UserAccountRepository accounts;
    private final UserGroupRepository groups;
    private final RoleRepository roles;
    private final Clock clock;

    /**
     * Creates the directory.
     *
     * @param accounts the accounts of the bound tenant
     * @param groups the groups of the bound tenant
     * @param roles the roles of the bound tenant
     * @param clock the current time, for searching accounts
     */
    public IdentityPolicySubjects(UserAccountRepository accounts, UserGroupRepository groups, RoleRepository roles, Clock clock)
    {
        this.accounts = requireNonNull(accounts, "accounts");
        this.groups = requireNonNull(groups, "groups");
        this.roles = requireNonNull(roles, "roles");
        this.clock = requireNonNull(clock, "clock");
    }

    @Override
    public Set<String> unknown(SubjectKind kind, Collection<String> names)
    {
        return switch (kind) {
            case USER -> {
                Set<String> known = accounts.findByUsernameNormIn(names.stream().map(UserAccount::normalize).toList()).stream()
                        .map(UserAccount::getUsernameNorm).collect(Collectors.toSet());
                yield names.stream().filter(name -> !known.contains(UserAccount.normalize(name))).collect(Collectors.toSet());
            }
            case GROUP -> names.stream().filter(name -> groups.findByCode(name).isEmpty()).collect(Collectors.toSet());
            case ROLE -> names.stream().filter(name -> roles.findByCode(name).isEmpty()).collect(Collectors.toSet());
        };
    }

    @Override
    public List<String> suggest(SubjectKind kind, String text, int limit)
    {
        String needle = text.strip().replace("%", "").replace("_", "").toLowerCase(Locale.ROOT);
        String pattern = "%" + needle + "%";
        return switch (kind) {
            case USER -> accounts.search(new UserCriteria(needle.isEmpty() ? null : needle, null, null, null),
                    Specification.unrestricted(), clock.instant(), 0, limit)
                    .stream().map(UserRow::username).toList();
            case GROUP -> groups.search(pattern, PageRequest.of(0, limit)).stream().map(GroupRow::code).toList();
            case ROLE -> roles.search(pattern).stream().limit(limit).map(Role::getCode).toList();
        };
    }
}
