// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.common.error.CommonErrorCode;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(CatalogAccess.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CatalogAccessTest
{
    @Autowired
    private CatalogAccess access;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private ApplicationRepository applications;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private PlatformAdministrators platform;

    private CatalogFixture fixture;

    @BeforeEach
    void createAccounts()
    {
        fixture = new CatalogFixture(tenants, accounts, platform);
    }

    @AfterEach
    void deleteRows()
    {
        fixture.deleteRows(resources, applications, events, transactionManager);
    }

    @Test
    void administratorsReadAndOnlyPlatformAdministratorsEdit()
    {
        assertThatCode(() -> fixture.asRoot(() -> {
            access.requireReader(fixture.root);
            access.requireEditor(fixture.root);
            return null;
        })).doesNotThrowAnyException();
        assertThatCode(() -> fixture.inTenant(() -> {
            access.requireReader(fixture.boss);
            return null;
        })).doesNotThrowAnyException();
        assertThatThrownBy(() -> fixture.inTenant(() -> {
            access.requireEditor(fixture.boss);
            return null;
        })).satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> fixture.inTenant(() -> {
            access.requireReader(fixture.member);
            return null;
        })).satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.FORBIDDEN));
        // Accounts of another tenant are invisible while this tenant is bound.
        assertThatThrownBy(() -> fixture.inTenant(() -> {
            access.requireReader(fixture.root);
            return null;
        })).satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(CommonErrorCode.FORBIDDEN));
    }
}
