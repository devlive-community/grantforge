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
import org.devlive.grantforge.identity.domain.UserGroup;
import org.devlive.grantforge.identity.domain.UserGroupRepository;
import org.devlive.grantforge.identity.domain.UserRow;
import org.devlive.grantforge.service.policy.SubjectKind;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentityPolicySubjectsTest
{
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final UserGroupRepository groups = mock(UserGroupRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final Instant now = Instant.parse("2026-10-02T00:00:00Z");
    private final IdentityPolicySubjects subjects = new IdentityPolicySubjects(accounts, groups, roles, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void tellsWhichNamesAreUnknown()
    {
        UserAccount alice = mock(UserAccount.class);
        when(alice.getUsernameNorm()).thenReturn("alice");
        when(accounts.findByUsernameNormIn(List.of("alice", "bob"))).thenReturn(List.of(alice));
        assertThat(subjects.unknown(SubjectKind.USER, List.of("Alice", "bob"))).containsExactly("bob");

        when(groups.findByCode(anyString())).thenReturn(Optional.empty());
        when(groups.findByCode("ops")).thenReturn(Optional.of(mock(UserGroup.class)));
        assertThat(subjects.unknown(SubjectKind.GROUP, List.of("ops", "spies"))).containsExactly("spies");

        when(roles.findByCode(anyString())).thenReturn(Optional.empty());
        when(roles.findByCode("analyst")).thenReturn(Optional.of(mock(Role.class)));
        assertThat(subjects.unknown(SubjectKind.ROLE, List.of("analyst", "boss"))).containsExactly("boss");
    }

    @Test
    void suggestsNamesContainingWhatWasTyped()
    {
        UserRow root = mock(UserRow.class);
        when(root.username()).thenReturn("root");
        when(accounts.search(eq(new UserCriteria("ro", null, null, null)), any(), eq(now), eq(0L), eq(5))).thenReturn(List.of(root));
        assertThat(subjects.suggest(SubjectKind.USER, " R%o_ ", 5)).containsExactly("root");
        when(accounts.search(eq(new UserCriteria(null, null, null, null)), any(), eq(now), eq(0L), eq(5))).thenReturn(List.of());
        assertThat(subjects.suggest(SubjectKind.USER, "", 5)).isEmpty();

        GroupRow ops = new GroupRow(1, "ops", "Operations", null, 0, now);
        when(groups.search(eq("%op%"), any(PageRequest.class))).thenReturn(new PageImpl<>(List.of(ops)));
        assertThat(subjects.suggest(SubjectKind.GROUP, "Op", 5)).containsExactly("ops");

        Role analyst = mock(Role.class);
        when(analyst.getCode()).thenReturn("analyst");
        when(roles.search("%%")).thenReturn(List.of(analyst, analyst));
        assertThat(subjects.suggest(SubjectKind.ROLE, "", 1)).containsExactly("analyst");
    }
}
