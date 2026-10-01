// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.identity;

import jakarta.persistence.EntityManagerFactory;
import org.devlive.grantforge.identity.domain.ConsoleSession;
import org.devlive.grantforge.identity.domain.ConsoleSessionRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.PasswordHistory;
import org.devlive.grantforge.identity.domain.PasswordHistoryRepository;
import org.devlive.grantforge.identity.domain.PlatformSetting;
import org.devlive.grantforge.identity.domain.PlatformSettingRepository;
import org.devlive.grantforge.identity.domain.Tenant;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.naming.SchemaNamingVerifier;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.devlive.grantforge.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Applies the identity changelog to a real database selected with {@code -Dgrantforge.it.database}; context
 * start-up proves that Hibernate validates the migrated schema, and the tests cover the column types and
 * constraints that differ between databases.
 */
@SpringBootTest(classes = TestIdentityApplication.class)
class IdentitySchemaIT
{
    private static final TestDatabase DATABASE = TestDatabase.fromSystemProperty();
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PlatformSettingRepository settings;

    @Autowired
    private PasswordHistoryRepository history;

    @Autowired
    private ConsoleSessionRepository sessions;

    @Autowired
    private OrgUnitRepository units;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry)
    {
        registry.add("spring.datasource.url", DATABASE::url);
        registry.add("spring.datasource.username", DATABASE::username);
        registry.add("spring.datasource.password", DATABASE::password);
    }

    @AfterAll
    static void stopDatabase()
    {
        DATABASE.close();
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            history.deleteAllInBatch();
            sessions.deleteAllInBatch();
            // Children first: the parent foreign key forbids deleting a parent before its children.
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                List<OrgUnit> all = units.findAll(Sort.by("depth"));
                for (int i = all.size() - 1; i >= 0; i--) {
                    units.delete(all.get(i));
                }
            });
            accounts.deleteAllInBatch();
            return null;
        });
        tenants.deleteAllInBatch();
        settings.deleteAllInBatch();
    }

    @Test
    void accountsRoundTripWithUnicodeFlagsAndTimestamps()
    {
        long tenantId = tenants.save(Tenant.create("acme", "权限管理-ÄÖÜ-🔐")).requireId();
        UserAccount account = UserAccount.create("alice", "{argon2}hash", NOW).withDisplayName("爱丽丝 🔐")
                .markSystemAccount();
        account.recordFailedLogin(NOW, 1, Duration.ofMinutes(5));
        TenantContext.runInTenant(tenantId, () -> accounts.save(account));

        UserAccount loaded = TenantContext.callAsSystem(() -> accounts.findByUsernameNorm("alice")).orElseThrow();

        assertThat(loaded.getTenantId()).isEqualTo(tenantId);
        assertThat(loaded.getDisplayName()).isEqualTo("爱丽丝 🔐");
        assertThat(loaded.isSystemAccount()).isTrue();
        assertThat(loaded.isMustChangePassword()).isFalse();
        assertThat(loaded.isLocked(NOW.plusSeconds(1))).isTrue();
        assertThat(loaded.getPasswordChangedAt()).isEqualTo(NOW);
        assertThat(tenants.findById(tenantId)).get().extracting(Tenant::getName).isEqualTo("权限管理-ÄÖÜ-🔐");
    }

    @Test
    void loginNamesTenantCodesAndSettingKeysAreUnique()
    {
        long first = tenants.save(Tenant.create("first", "First")).requireId();
        long second = tenants.save(Tenant.create("second", "Second")).requireId();
        TenantContext.runInTenant(first, () -> accounts.saveAndFlush(UserAccount.create("alice", "h", NOW)));
        settings.saveAndFlush(PlatformSetting.of("k", "v"));

        assertThatThrownBy(() -> TenantContext.runInTenant(second,
                () -> accounts.saveAndFlush(UserAccount.create("Alice", "h", NOW))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> tenants.saveAndFlush(Tenant.create("first", "Again")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> settings.saveAndFlush(PlatformSetting.of("k", "w")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void accountsRequireAnExistingTenant()
    {
        assertThatThrownBy(() -> TenantContext.runInTenant(42,
                () -> accounts.saveAndFlush(UserAccount.create("orphan", "h", NOW))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void passwordHistoryFollowsItsAccount()
    {
        long tenantId = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long accountId = TenantContext.callInTenant(tenantId,
                () -> accounts.save(UserAccount.create("alice", "{argon2}current", NOW)).requireId());
        TenantContext.runInTenant(tenantId, () -> history.save(PasswordHistory.of(accountId, "{argon2}former")));

        assertThat(TenantContext.callInTenant(tenantId, () -> history.findByAccountIdOrderByCreatedAtDescIdDesc(accountId)))
                .extracting(PasswordHistory::getPasswordHash).containsExactly("{argon2}former");

        // Deleting an account removes its history (ON DELETE CASCADE).
        TenantContext.runInTenant(tenantId, accounts::deleteAllInBatch);
        long remaining = TenantContext.callAsSystem(() -> history.count());
        assertThat(remaining).isZero();
    }

    @Test
    void consoleSessionsListWithTheirOwnersAndFollowTheirAccount()
    {
        long tenantId = tenants.save(Tenant.create("acme", "Acme")).requireId();
        long accountId = TenantContext.callInTenant(tenantId,
                () -> accounts.save(UserAccount.create("alice", "{argon2}current", NOW).withDisplayName("爱丽丝")).requireId());
        String agent = "Mozilla/5.0 (Macintosh) 浏览器 " + "x".repeat(300);
        TenantContext.runInTenant(tenantId, () -> sessions.save(ConsoleSession.start(
                "0b9a7f8e-1c2d-4e5f-8a9b-0c1d2e3f4a5b", accountId, "2001:db8::ffff:192.0.2.1", agent, NOW)));

        var page = TenantContext.callInTenant(tenantId, () -> sessions.findActive(NOW.minusSeconds(1),
                PageRequest.of(0, 10)));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).displayName()).isEqualTo("爱丽丝");
        assertThat(page.getContent().get(0).session().getUserAgent()).hasSize(ConsoleSession.MAX_USER_AGENT)
                .startsWith("Mozilla/5.0 (Macintosh) 浏览器");
        assertThat(page.getContent().get(0).session().getLastSeenAt()).isEqualTo(NOW);

        // Deleting an account removes its sessions from the index (ON DELETE CASCADE).
        TenantContext.runInTenant(tenantId, accounts::deleteAllInBatch);
        long remaining = TenantContext.callAsSystem(() -> sessions.count());
        assertThat(remaining).isZero();
    }

    @Test
    void departmentSubtreesMoveByRewritingTheirPathPrefix()
    {
        long tenantId = tenants.save(Tenant.create("acme", "Acme")).requireId();
        OrgUnit hq = OrgUnit.create(null, "hq", "总部", 0);
        OrgUnit sales = OrgUnit.create(hq, "sales", "销售部", 0);
        OrgUnit east = OrgUnit.create(sales, "east", "华东", 0);
        OrgUnit lab = OrgUnit.create(null, "lab", "实验室", 1);
        TenantContext.runInTenant(tenantId, () -> units.saveAll(List.of(hq, sales, east, lab)));

        String oldPrefix = sales.getPath();
        String newPrefix = lab.getPath() + sales.requireId() + "/";
        int moved = TenantContext.callInTenant(tenantId, () -> new TransactionTemplate(
                transactionManager).execute(status -> {
                    units.reparent(sales.requireId(), lab.requireId());
                    return units.moveSubtree(oldPrefix, oldPrefix + "%", newPrefix, oldPrefix.length() + 1, 0);
                }));

        assertThat(moved).isEqualTo(2);
        assertThat(TenantContext.callInTenant(tenantId, () -> units.findTree())).extracting(OrgUnit::getCode,
                OrgUnit::getPath).containsExactly(tuple("hq", hq.getPath()), tuple("lab", lab.getPath()),
                tuple("sales", newPrefix), tuple("east", newPrefix + east.requireId() + "/"));
        assertThat(TenantContext.callInTenant(tenantId, () -> units.maxDepthBelow(lab.getPath() + "%"))).isEqualTo(2);
    }

    @Test
    void mappedNamesStayPortable()
    {
        assertThat(SchemaNamingVerifier.verify(entityManagerFactory)).isEmpty();
    }
}
