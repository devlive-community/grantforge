// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
import org.devlive.grantforge.identity.domain.AccountPositionRepository;
import org.devlive.grantforge.identity.domain.GroupMemberRepository;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.PositionRepository;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserGroupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(SubjectDirectory.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SubjectDirectoryTest
{
    @Autowired
    private SubjectDirectory directory;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private UserGroupRepository groups;

    @Autowired
    private GroupMemberRepository groupMembers;

    @Autowired
    private OrgUnitRepository units;

    @Autowired
    private OrgMemberRepository unitMembers;

    @Autowired
    private PositionRepository positions;

    @Autowired
    private AccountPositionRepository holdings;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleAssignmentRepository assignments;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private PlatformAdministrators platform;

    private CatalogFixture catalog;
    private AssignmentFixture people;

    @BeforeEach
    void createPeople()
    {
        catalog = new CatalogFixture(tenants, accounts, platform);
        people = new AssignmentFixture(catalog, accounts, groups, groupMembers, units, unitMembers, positions, holdings);
    }

    @AfterEach
    void deleteRows()
    {
        AssignmentFixture.deleteRows(assignments, roles, groupMembers, unitMembers, holdings, groups, units, positions);
        catalog.deleteRows(resources, applications, events, transactionManager);
    }

    private <T> T inTenant(Supplier<T> action)
    {
        return catalog.inTenant(() -> new TransactionTemplate(transactionManager).execute(status -> action.get()));
    }

    @Test
    void namesSubjectsOfTheBoundTenant()
    {
        assertThat(inTenant(() -> directory.find(SubjectType.USER, people.alice))).contains(new Subject(SubjectType.USER, people.alice,
                "Alice A", "alice"));
        assertThat(inTenant(() -> directory.find(SubjectType.USER, catalog.member))).map(Subject::name).contains("member");
        assertThat(inTenant(() -> directory.names(SubjectType.GROUP, List.of(people.dev, 42L)))).containsOnlyKeys(people.dev);
        assertThat(inTenant(() -> directory.find(SubjectType.ORG_UNIT, people.sales))).map(Subject::detail).contains("sales");
        assertThat(inTenant(() -> directory.find(SubjectType.POSITION, people.cfo))).map(Subject::name).contains("CFO");
        assertThat(inTenant(() -> directory.names(SubjectType.POSITION, List.of()))).isEmpty();
        // Subjects of another tenant are invisible.
        Optional<Subject> elsewhere = catalog.asRoot(() -> new TransactionTemplate(transactionManager).execute(status ->
                directory.find(SubjectType.USER, people.alice)));
        assertThat(elsewhere).isEmpty();
    }

    @Test
    void listsWhatAnAccountBelongsTo()
    {
        SubjectDirectory.Memberships memberships = inTenant(() -> directory.memberships(people.alice));

        assertThat(memberships.groups()).containsExactly(people.dev);
        assertThat(memberships.units()).containsExactly(people.sales);
        assertThat(memberships.parentUnits()).containsExactly(people.hq);
        assertThat(memberships.positions()).containsExactly(people.cfo);
        assertThat(inTenant(() -> directory.memberships(catalog.member))).isEqualTo(new SubjectDirectory.Memberships(catalog.member,
                Set.of(), Set.of(), Set.of(), Set.of()));
    }
}
