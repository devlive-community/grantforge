// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import jakarta.persistence.EntityManagerFactory;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.identity.application.PlatformAdministrators;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.devlive.grantforge.persistence.secured.SecuredEntities;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;
import org.devlive.grantforge.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs data scopes on the database chosen by {@code -Dgrantforge.it.database} (H2 by default): the subqueries over
 * memberships and department paths, escaped wildcards, rows without values and moments must select the same rows on every
 * supported database.
 */
@SpringBootTest
class DataScopeIT
{
    private static final TestDatabase DATABASE = TestDatabase.fromSystemProperty();

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private OrgUnitRepository units;

    @Autowired
    private OrgMemberRepository members;

    @Autowired
    private AuditEventRepository events;

    @Autowired
    private EntityManagerFactory factory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private SecuredEntities entities;

    // The identity services are not part of this module's test context.
    @MockitoBean
    private PlatformAdministrators platform;

    private DataScopeFixture data;

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

    @BeforeEach
    void createData()
    {
        data = new DataScopeFixture(tenants, accounts, units, members, events, factory, transactionManager);
    }

    @AfterEach
    void deleteData()
    {
        data.delete();
    }

    private List<String> users(DataSubject subject, DataRule... allow)
    {
        SecuredEntityDefinition user = entities.find("user").orElseThrow();
        return data.find(UserAccount.class, DataScopeSpecifications.of(user, new DataAccess.Rules(List.of(allow), List.of()), subject,
                DataScopeFixture.NOW), UserAccount::getUsername);
    }

    private static DataRule when(Condition condition)
    {
        return new DataRule(DataScope.CONDITION, condition, List.of());
    }

    @Test
    void departmentScopesSelectTheSameRowsEverywhere()
    {
        DataSubject carol = DataScopeSpecificationsTest.subject(data.carol, data.acme, "carol", List.of(data.hq));
        assertThat(users(carol, DataRule.of(DataScope.ORG_AND_CHILDREN))).containsExactly("alice", "carol");
        assertThat(users(carol, DataRule.of(DataScope.ORG))).containsExactly("carol");
        assertThat(users(carol, DataRule.of(DataScope.SELF))).containsExactly("carol");
        assertThat(data.find(OrgUnit.class, DataScopeSpecifications.of(entities.find("org-unit").orElseThrow(),
                new DataAccess.Rules(List.of(DataRule.of(DataScope.ORG_AND_CHILDREN)), List.of(DataRule.of(DataScope.ORG))), carol,
                DataScopeFixture.NOW), OrgUnit::getCode)).containsExactly("rd");
        assertThat(data.find(AuditEvent.class, DataScopeSpecifications.of(entities.find("audit-event").orElseThrow(),
                new DataAccess.Rules(List.of(DataRule.of(DataScope.ORG_AND_CHILDREN)), List.of()), carol, DataScopeFixture.NOW),
                event -> String.valueOf(event.getActorName()))).containsExactly("alice");
    }

    @Test
    void conditionsSelectTheSameRowsEverywhere()
    {
        DataSubject bob = DataScopeSpecificationsTest.subject(data.bob, data.acme, "bob", List.of(data.ops));
        assertThat(users(bob, when(new Condition.Comparison("email", ComparisonOperator.CONTAINS, "_ops", null)))).containsExactly("bob");
        assertThat(users(bob, when(new Condition.Comparison("email", ComparisonOperator.NE, "alice@acme.test", null))))
                .containsExactly("bob", "carol", "dave");
        assertThat(users(bob, when(new Condition.Comparison("status", ComparisonOperator.IN, List.of("DISABLED"), null))))
                .containsExactly("dave");
        Instant twoDaysAgo = DataScopeFixture.NOW.minusSeconds(172_800);
        assertThat(users(bob, when(new Condition.Comparison("lastLoginAt", ComparisonOperator.GT, twoDaysAgo, null)))).containsExactly("alice");
        assertThat(users(bob, when(new Condition.Comparison("username", ComparisonOperator.EQ, null, ConditionVariable.SUBJECT_USERNAME))))
                .containsExactly("bob");
    }
}
