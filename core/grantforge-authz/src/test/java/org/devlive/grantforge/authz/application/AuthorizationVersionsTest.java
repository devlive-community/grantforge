// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.application;

import org.devlive.grantforge.audit.application.AuditLog;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.authz.domain.ApplicationRepository;
import org.devlive.grantforge.authz.domain.CatalogTestData;
import org.devlive.grantforge.authz.domain.DependencyKind;
import org.devlive.grantforge.authz.domain.DependencySource;
import org.devlive.grantforge.authz.domain.GrantEffect;
import org.devlive.grantforge.authz.domain.Resource;
import org.devlive.grantforge.authz.domain.ResourceDependency;
import org.devlive.grantforge.authz.domain.ResourceDependencyRepository;
import org.devlive.grantforge.authz.domain.ResourceRepository;
import org.devlive.grantforge.authz.domain.ResourceType;
import org.devlive.grantforge.authz.domain.Role;
import org.devlive.grantforge.authz.domain.RoleAssignment;
import org.devlive.grantforge.authz.domain.RoleAssignmentRepository;
import org.devlive.grantforge.authz.domain.RoleGrant;
import org.devlive.grantforge.authz.domain.RoleGrantRepository;
import org.devlive.grantforge.authz.domain.RoleParentRepository;
import org.devlive.grantforge.authz.domain.RoleRepository;
import org.devlive.grantforge.authz.domain.SubjectType;
import org.devlive.grantforge.common.error.ErrorCode;
import org.devlive.grantforge.identity.application.IdentityConfiguration;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
import org.devlive.grantforge.identity.domain.AccountPositionRepository;
import org.devlive.grantforge.identity.domain.GroupMemberRepository;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.PositionRepository;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.identity.domain.UserGroupRepository;
import org.devlive.grantforge.persistence.authz.AuthorizationChanges;
import org.devlive.grantforge.persistence.tenant.TenantContext;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({AuditLog.class, IdentityConfiguration.class, CatalogAccess.class, ApplicationService.class, RoleService.class,
        SystemRoleProvisioner.class, SubjectDirectory.class, EffectiveRoles.class, AuthorizationEvaluator.class, AuthorizationVersions.class,
        RoleAssignmentService.class, RoleGrantService.class, ImpactAnalysis.class, RoleHolders.class, RoleService.class, RoleInheritanceService.class, AuthorizationVersionsTest.MovingClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthorizationVersionsTest
{
    static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    /** A clock the tests move forward. */
    static class MovingClock
    {
        static final AtomicReference<Instant> TIME = new AtomicReference<>(NOW);

        @Bean
        @Primary
        Clock movingClock()
        {
            return new Clock()
            {
                @Override
                public ZoneId getZone()
                {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone)
                {
                    return this;
                }

                @Override
                public Instant instant()
                {
                    return requireNonNull(TIME.get());
                }
            };
        }
    }

    @Autowired
    private AuthorizationEvaluator evaluator;

    @Autowired
    private AuthorizationVersions versions;

    @Autowired
    private AuthorizationChanges changes;

    @Autowired
    private RoleService roleService;

    @Autowired
    private RoleParentRepository parents;

    @Autowired
    private RoleAssignmentService assignmentService;

    @Autowired
    private RoleGrantService grantService;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private SystemRoleProvisioner provisioner;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private RoleGrantRepository grants;

    @Autowired
    private RoleAssignmentRepository assignments;

    @Autowired
    private ResourceRepository resources;

    @Autowired
    private ResourceDependencyRepository dependencies;

    @Autowired
    private ApplicationRepository applications;

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
    private long console;
    private Resource users;
    private Resource edit;
    private Resource delete;
    private Resource update;
    private Resource tenantsPage;
    private Resource reports;

    @BeforeEach
    void createCatalogAndPeople()
    {
        catalog = new CatalogFixture(tenants, accounts, platform);
        people = new AssignmentFixture(catalog, accounts, groups, groupMembers, units, unitMembers, positions, holdings);
        console = applicationService.registerConsole();
        Resource system = save(null, ResourceType.MODULE, "system");
        users = save(system, ResourceType.PAGE, "system.user");
        edit = save(users, ResourceType.ACTION, "system.user.btn.edit");
        delete = save(users, ResourceType.ACTION, "system.user.btn.delete");
        Resource apis = save(null, ResourceType.MODULE, "api");
        update = save(apis, ResourceType.API, "api:system.user.update");
        Resource platformModule = save(null, ResourceType.MODULE, "platform");
        tenantsPage = save(platformModule, ResourceType.PAGE, "platform.tenant");
        Resource extra = save(null, ResourceType.MODULE, "extra");
        reports = save(extra, ResourceType.PAGE, "extra.reports");
        dependencies.save(ResourceDependency.create(edit, update, DependencyKind.REQUIRED, DependencySource.DECLARED));
        provisioner.provision(catalog.tenant, false);
        provisioner.provision(catalog.platform, true);
    }

    @AfterEach
    void deleteRows()
    {
        TenantContext.callAsSystem(() -> {
            parents.deleteAllInBatch();
            grants.deleteAllInBatch();
            return null;
        });
        AssignmentFixture.deleteRows(assignments, roles, groupMembers, unitMembers, holdings, groups, units, positions);
        dependencies.deleteAllInBatch();
        catalog.deleteRows(resources, applications, events, transactionManager);
    }

    private Resource save(@Nullable Resource parent, ResourceType type, String code)
    {
        return resources.save(Resource.create(console, parent, type, code, CatalogTestData.details(code), 0));
    }

    private long role(String code, boolean enabled, Object... grantsAndEffects)
    {
        return catalog.inTenant(() -> {
            Role created = Role.create(code, code, null);
            created.enable(enabled);
            long id = roles.save(created).requireId();
            for (int i = 0; i < grantsAndEffects.length; i += 2) {
                grants.save(RoleGrant.create(id, (Resource) grantsAndEffects[i], (GrantEffect) grantsAndEffects[i + 1], null, 1));
            }
            return id;
        });
    }

    private void give(long role, SubjectType type, long subject, RoleAssignment.Terms terms)
    {
        catalog.inTenant(() -> assignments.save(RoleAssignment.create(role, type, subject, terms)));
    }

    private AuthorizationSnapshot snapshotOf(long account)
    {
        return catalog.inTenant(() -> evaluator.snapshot(account));
    }

    private static void assertRefused(Supplier<?> action, ErrorCode expected)
    {
        assertThatThrownBy(action::get).satisfies(error -> assertThat(CatalogFixture.errorOf(error)).isEqualTo(expected));
    }

    @AfterEach
    void resetClock()
    {
        MovingClock.TIME.set(NOW);
    }

    private void inTransaction(Runnable action)
    {
        catalog.inTenant(() -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
            return Boolean.TRUE;
        });
    }

    @Test
    void changesRaiseTheCountersOfTheirScopeOnCommit()
    {
        AuthorizationVersions.Versions before = catalog.inTenant(() -> versions.current(catalog.tenant));
        long platformBefore = catalog.asRoot(() -> versions.current(catalog.platform)).tenant();
        role("auditors", true, edit, GrantEffect.ALLOW);
        AuthorizationVersions.Versions after = catalog.inTenant(() -> versions.current(catalog.tenant));
        assertThat(after.tenant()).isGreaterThan(before.tenant());
        assertThat(after.catalog()).isEqualTo(before.catalog());

        save(null, ResourceType.MODULE, "reports");
        assertThat(catalog.inTenant(() -> versions.current(catalog.tenant)).catalog()).isGreaterThan(after.catalog());
        // Another tenant's counter is untouched.
        assertThat(catalog.asRoot(() -> versions.current(catalog.platform)).tenant()).isEqualTo(platformBefore);
    }

    @Test
    void snapshotsAreReusedUntilSomethingTheyDependOnChanges()
    {
        long auditors = role("auditors", true, edit, GrantEffect.ALLOW);
        give(auditors, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        AuthorizationSnapshot first = snapshotOf(people.alice);
        assertThat(first.resources()).contains("system.user.btn.edit");

        // A bulk delete passes no entity listener: the cached snapshot is still used...
        inTransaction(() -> grants.removeRole(auditors));
        assertThat(snapshotOf(people.alice)).isSameAs(first);
        // ...until a change of the tenant is noted.
        inTransaction(changes::currentTenant);
        assertThat(snapshotOf(people.alice).resources()).isEmpty();

        // Ordinary changes are noted by the entity listener.
        catalog.inTenant(() -> grants.save(RoleGrant.create(auditors, delete, GrantEffect.ALLOW, null, 1)));
        assertThat(snapshotOf(people.alice).resources()).contains("system.user.btn.delete");
    }

    @Test
    void snapshotsExpireWhenAnAssignmentEndsEvenWithoutAChange()
    {
        long auditors = role("auditors", true, edit, GrantEffect.ALLOW);
        give(auditors, SubjectType.USER, people.alice, new RoleAssignment.Terms(null, NOW.plusSeconds(60), false));
        assertThat(snapshotOf(people.alice).resources()).contains("system.user.btn.edit");

        MovingClock.TIME.set(NOW.plusSeconds(30));
        AuthorizationSnapshot halfway = snapshotOf(people.alice);
        assertThat(halfway.computedAt()).isEqualTo(NOW);

        MovingClock.TIME.set(NOW.plusSeconds(61));
        assertThat(snapshotOf(people.alice).resources()).isEmpty();
        MovingClock.TIME.set(NOW.plus(AuthorizationEvaluator.MAX_AGE).plusSeconds(120));
        assertThat(snapshotOf(people.alice).computedAt()).isEqualTo(MovingClock.TIME.get());
    }

    @Test
    void concurrentReadersSeeTheLastCommittedStateOnceTheWriterIsDone() throws Exception
    {
        long auditors = role("auditors", true, edit, GrantEffect.ALLOW);
        give(auditors, SubjectType.USER, people.alice, RoleAssignment.Terms.UNLIMITED);
        long grant = catalog.inTenant(() -> grants.findByRoleId(auditors)).get(0).requireId();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        AtomicBoolean writing = new AtomicBoolean(true);
        List<Future<Integer>> readers = new ArrayList<>();
        for (int reader = 0; reader < 3; reader++) {
            readers.add(pool.submit(() -> {
                int reads = 0;
                while (writing.get()) {
                    snapshotOf(people.alice);
                    reads++;
                }
                return reads;
            }));
        }
        Future<?> writer = pool.submit(() -> {
            for (int round = 0; round < 20; round++) {
                GrantEffect effect = round % 2 == 0 ? GrantEffect.DENY : GrantEffect.ALLOW;
                inTransaction(() -> grants.findById(grant).orElseThrow().change(effect, null, 1));
            }
            writing.set(false);
        });
        writer.get(60, TimeUnit.SECONDS);
        for (Future<Integer> reader : readers) {
            assertThat(reader.get(60, TimeUnit.SECONDS)).isPositive();
        }
        pool.shutdown();
        // The last round allowed the button again; every read from now on sees it.
        for (int read = 0; read < 5; read++) {
            assertThat(snapshotOf(people.alice).resources()).contains("system.user.btn.edit");
        }
    }
}
