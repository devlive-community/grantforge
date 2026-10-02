// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({SubjectDirectory.class, EffectiveRoles.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EffectiveRolesTest
{
    @Autowired
    private EffectiveRoles effectiveRoles;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleAssignmentRepository assignments;

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
    private ApplicationRepository applications;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

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

    @Test
    void leavesOutAssignmentsOfVanishedSubjectsAndListsActiveRolesFirst()
    {
        catalog.inTenant(() -> {
            long active = roles.save(Role.create("zeta", "Zeta", null)).requireId();
            long later = roles.save(Role.create("alpha", "Alpha", null)).requireId();
            assignments.save(RoleAssignment.create(active, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED));
            assignments.save(RoleAssignment.create(later, SubjectType.GROUP, people.dev, new RoleAssignment.Terms(Instant.parse("2030-01-01T00:00:00Z"),
                    null, false)));
            return assignments.save(RoleAssignment.create(active, SubjectType.POSITION, 42, RoleAssignment.Terms.UNLIMITED));
        });

        List<EffectiveRole> found = catalog.inTenant(() -> new TransactionTemplate(transactionManager).execute(status ->
                effectiveRoles.of(people.alice, Instant.EPOCH)));

        assertThat(found).extracting(role -> role.role().code() + ":" + role.active()).containsExactly("zeta:true", "alpha:false");
        assertThat(found.get(0).sources()).hasSize(1);
    }
}
