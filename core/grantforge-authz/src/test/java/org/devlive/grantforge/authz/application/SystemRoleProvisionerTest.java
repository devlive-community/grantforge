// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.identity.application.TenantCreated;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(SystemRoleProvisioner.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SystemRoleProvisionerTest
{
    @Autowired
    private SystemRoleProvisioner provisioner;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private RoleAssignmentRepository assignments;

    @Autowired
    private UserAccountRepository accounts;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            assignments.deleteAllInBatch();
            roles.deleteAllInBatch();
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    private List<String> codes(long tenant)
    {
        return TenantContext.callInTenant(tenant, () -> roles.search("%")).stream()
                .peek(role -> assertThat(role.getType()).isEqualTo(RoleType.SYSTEM)).map(Role::getCode).sorted().toList();
    }

    @Test
    void newTenantsGetTheirSystemRolesAndStartUpFillsInMissingOnes()
    {
        long platform = tenants.save(Tenant.create("default", "Default").markPlatform()).requireId();
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long boss = TenantContext.callInTenant(acme, () -> accounts.save(UserAccount.create("boss", "h", Instant.EPOCH)
                .markSystemAccount()).requireId());
        long root = TenantContext.callInTenant(platform, () -> accounts.save(UserAccount.create("root", "h", Instant.EPOCH)
                .markSystemAccount()).requireId());
        TenantContext.runInTenant(acme, () -> accounts.save(UserAccount.create("member", "h", Instant.EPOCH)));

        events.publishEvent(new TenantCreated(acme, false));
        assertThat(codes(acme)).containsExactly("tenant-admin");
        assertThat(codes(platform)).isEmpty();

        provisioner.run(new DefaultApplicationArguments());
        assertThat(codes(platform)).containsExactly("platform-admin", "tenant-admin");
        assertThat(provisioner.provision(acme, false)).isZero();
        assertThat(codes(acme)).containsExactly("tenant-admin");
        // System accounts have the system roles; other accounts do not.
        assertThat(TenantContext.callInTenant(acme, () -> assignments.findAll())).extracting(RoleAssignment::getSubjectId)
                .containsExactly(boss);
        assertThat(TenantContext.callInTenant(platform, () -> assignments.findBySubjects(SubjectType.USER, List.of(root))))
                .hasSize(2);
    }
}
