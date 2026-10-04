// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity.domain;

import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Links of accounts to identity sources. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ExternalIdentityRepositoryTest
{
    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private IdentitySourceRepository sources;

    @Autowired
    private ExternalIdentityRepository links;

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            links.deleteAllInBatch();
            accounts.deleteAllInBatch();
            sources.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
    }

    @Test
    void findsAccountsByWhatTheSourceCallsThem()
    {
        long acme = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long corp = TenantContext.callInTenant(acme, () -> {
            IdentitySource source = IdentitySource.create("corp", IdentitySourceType.LDAP);
            source.configure("Corp", true, true, "{}", null);
            return sources.save(source).requireId();
        });
        long alice = TenantContext.callInTenant(acme, () -> accounts.save(UserAccount.create("alice", "h", Instant.EPOCH)).requireId());
        long bob = TenantContext.callInTenant(acme, () -> accounts.save(UserAccount.create("bob", "h", Instant.EPOCH)).requireId());
        TenantContext.callInTenant(acme, () -> links.save(ExternalIdentity.of(alice, corp, "uuid-a")));

        assertThat(TenantContext.callInTenant(acme, () -> links.findBySourceIdAndExternalId(corp, "uuid-a"))).get()
                .extracting(ExternalIdentity::getAccountId).isEqualTo(alice);
        assertThat(TenantContext.callInTenant(acme, () -> links.findByAccountId(bob))).isEmpty();
        assertThat(TenantContext.callInTenant(acme, () -> links.findBySourceId(corp))).hasSize(1);
        assertThat(TenantContext.callInTenant(acme, () -> links.countBySourceId(corp))).isOne();
        // One account per user of a source, and one source per account.
        assertThatThrownBy(() -> TenantContext.callInTenant(acme, () -> links.saveAndFlush(ExternalIdentity.of(bob, corp, "uuid-a"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> TenantContext.callInTenant(acme, () -> links.saveAndFlush(ExternalIdentity.of(alice, corp, "uuid-b"))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
