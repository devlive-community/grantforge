// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.RoleType;
import org.devlive.grantforge.identity.application.TenantCreated;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
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

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            roles.deleteAllInBatch();
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

        events.publishEvent(new TenantCreated(acme, false));
        assertThat(codes(acme)).containsExactly("tenant-admin");
        assertThat(codes(platform)).isEmpty();

        provisioner.run(new DefaultApplicationArguments());
        assertThat(codes(platform)).containsExactly("platform-admin", "tenant-admin");
        assertThat(provisioner.provision(acme, false)).isZero();
        assertThat(codes(acme)).containsExactly("tenant-admin");
    }
}
