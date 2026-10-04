// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.authz.data;

import jakarta.persistence.EntityManagerFactory;
import org.devlive.grantforge.audit.domain.AuditEvent;
import org.devlive.grantforge.audit.domain.AuditEventRepository;
import org.devlive.grantforge.identity.domain.AccountStatus;
import org.devlive.grantforge.identity.domain.OrgMemberRepository;
import org.devlive.grantforge.identity.domain.OrgUnit;
import org.devlive.grantforge.identity.domain.OrgUnitRepository;
import org.devlive.grantforge.identity.domain.TenantRepository;
import org.devlive.grantforge.identity.domain.UserAccount;
import org.devlive.grantforge.identity.domain.UserAccountRepository;
import org.devlive.grantforge.persistence.secured.DataScope;
import org.devlive.grantforge.persistence.secured.SecuredEntities;
import org.devlive.grantforge.persistence.secured.SecuredEntityDefinition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DataScopeSpecificationsTest
{
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

    private DataScopeFixture data;

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

    static DataSubject subject(long accountId, long tenant, String username, List<OrgUnit> unitsOf)
    {
        return new DataSubject(accountId, tenant, username, unitsOf.stream().map(OrgUnit::requireId).toList(),
                unitsOf.stream().map(OrgUnit::getPath).toList(), List.of("auditors"), List.of());
    }

    private List<String> users(DataSubject subject, DataRule... allow)
    {
        return users(subject, new DataAccess.Rules(List.of(allow), List.of()));
    }

    private List<String> users(DataSubject subject, DataAccess.Rules rules)
    {
        SecuredEntityDefinition user = entities.find("user").orElseThrow();
        return data.find(UserAccount.class, DataScopeSpecifications.of(user, rules, subject, DataScopeFixture.NOW), UserAccount::getUsername);
    }

    private static DataRule when(Condition condition)
    {
        return new DataRule(DataScope.CONDITION, condition, List.of());
    }

    private static Condition.Comparison compare(String field, ComparisonOperator operator, Object value)
    {
        return new Condition.Comparison(field, operator, value, null);
    }

    @Test
    void coversTheTenantOwnRowsAndDepartments()
    {
        DataSubject carol = subject(data.carol, data.acme, "carol", List.of(data.hq));
        assertThat(users(carol, DataRule.of(DataScope.TENANT))).containsExactly("alice", "bob", "carol", "dave");
        assertThat(users(carol, DataRule.of(DataScope.ALL))).containsExactly("alice", "bob", "carol", "dave");
        assertThat(users(carol, DataRule.of(DataScope.SELF))).containsExactly("carol");
        assertThat(users(carol, DataRule.of(DataScope.ORG))).containsExactly("carol");
        assertThat(users(carol, DataRule.of(DataScope.ORG_AND_CHILDREN))).containsExactly("alice", "carol");
        assertThat(users(carol, new DataRule(DataScope.CUSTOM_ORGS, null, List.of(data.ops.requireId())))).containsExactly("bob");
        assertThat(users(carol, new DataRule(DataScope.CUSTOM_ORGS, null, List.of()))).isEmpty();
        DataSubject nobody = subject(data.dave, data.acme, "dave", List.of());
        assertThat(users(nobody, DataRule.of(DataScope.ORG))).isEmpty();
        assertThat(users(nobody, DataRule.of(DataScope.ORG_AND_CHILDREN))).isEmpty();
        // Without an allowing rule nothing is visible; denials take rows away from what is allowed.
        assertThat(users(carol)).isEmpty();
        assertThat(users(carol, new DataAccess.Rules(List.of(DataRule.of(DataScope.TENANT), DataRule.of(DataScope.SELF)),
                List.of(DataRule.of(DataScope.ORG_AND_CHILDREN))))).containsExactly("bob", "dave");
        assertThat(users(carol, new DataRule(DataScope.CONDITION, null, List.of()))).isEmpty();
    }

    @Test
    void departmentScopesReachDepartmentsThemselves()
    {
        SecuredEntityDefinition unit = entities.find("org-unit").orElseThrow();
        DataSubject carol = subject(data.carol, data.acme, "carol", List.of(data.hq));
        assertThat(data.find(OrgUnit.class, DataScopeSpecifications.of(unit, new DataAccess.Rules(List.of(DataRule.of(DataScope.ORG_AND_CHILDREN)),
                List.of()), carol, DataScopeFixture.NOW), OrgUnit::getCode)).containsExactly("hq", "rd");
        assertThat(data.find(OrgUnit.class, DataScopeSpecifications.of(unit, new DataAccess.Rules(List.of(DataRule.of(DataScope.ORG)),
                List.of()), carol, DataScopeFixture.NOW), OrgUnit::getCode)).containsExactly("hq");
        // Departments belong to no account.
        assertThat(data.find(OrgUnit.class, DataScopeSpecifications.of(unit, new DataAccess.Rules(List.of(DataRule.of(DataScope.SELF)),
                List.of()), carol, DataScopeFixture.NOW), OrgUnit::getCode)).isEmpty();
        SecuredEntityDefinition group = entities.find("group").orElseThrow();
        assertThat(DataScopeSpecifications.of(group, new DataAccess.Rules(List.of(DataRule.of(DataScope.ORG)), List.of()), carol,
                DataScopeFixture.NOW)).isNotNull();
    }

    @Test
    void keepsRowsWithoutATenantColumnToTheReadersTenant()
    {
        SecuredEntityDefinition audit = entities.find("audit-event").orElseThrow();
        DataSubject carol = subject(data.carol, data.acme, "carol", List.of(data.hq));
        assertThat(data.find(AuditEvent.class, DataScopeSpecifications.of(audit, new DataAccess.Rules(List.of(DataRule.of(DataScope.TENANT)),
                List.of()), carol, DataScopeFixture.NOW), event -> String.valueOf(event.getActorName()))).containsExactly("alice", "bob");
        assertThat(data.find(AuditEvent.class, DataScopeSpecifications.of(audit, new DataAccess.Rules(List.of(DataRule.of(DataScope.ALL)),
                List.of()), carol, DataScopeFixture.NOW), event -> String.valueOf(event.getActorName()))).containsExactly("alice", "bob", "eve");
        assertThat(data.find(AuditEvent.class, DataScopeSpecifications.of(audit, new DataAccess.Rules(List.of(DataRule.of(DataScope.ORG_AND_CHILDREN)),
                List.of()), carol, DataScopeFixture.NOW), event -> String.valueOf(event.getActorName()))).containsExactly("alice");
    }

    @Test
    void conditionsCompareFieldsWithValuesAndVariables()
    {
        DataSubject bob = subject(data.bob, data.acme, "bob", List.of(data.ops));
        assertThat(users(bob, when(compare("status", ComparisonOperator.EQ, "DISABLED")))).containsExactly("dave");
        assertThat(users(bob, when(compare("status", ComparisonOperator.IN, List.of("ACTIVE"))))).containsExactly("alice", "bob", "carol");
        // Rows without a value count as unequal and outside any list.
        assertThat(users(bob, when(compare("email", ComparisonOperator.NE, "alice@acme.test")))).containsExactly("bob", "carol", "dave");
        assertThat(users(bob, when(compare("email", ComparisonOperator.NOT_IN, List.of("carol@other.test", "bob_ops@acme.test")))))
                .containsExactly("alice", "dave");
        assertThat(users(bob, when(new Condition.Comparison("email", ComparisonOperator.IS_NULL, null, null)))).containsExactly("dave");
        assertThat(users(bob, when(new Condition.Comparison("email", ComparisonOperator.NOT_NULL, null, null))))
                .containsExactly("alice", "bob", "carol");
        // Wildcards in values are literal.
        assertThat(users(bob, when(compare("email", ComparisonOperator.CONTAINS, "_ops")))).containsExactly("bob");
        assertThat(users(bob, when(compare("email", ComparisonOperator.CONTAINS, "%")))).isEmpty();
        assertThat(users(bob, when(compare("email", ComparisonOperator.STARTS_WITH, "ca")))).containsExactly("carol");
        Instant twoDaysAgo = DataScopeFixture.NOW.minusSeconds(172_800);
        assertThat(users(bob, when(compare("lastLoginAt", ComparisonOperator.GT, twoDaysAgo)))).containsExactly("alice");
        assertThat(users(bob, when(compare("lastLoginAt", ComparisonOperator.GTE, twoDaysAgo)))).containsExactly("alice");
        assertThat(users(bob, when(compare("lastLoginAt", ComparisonOperator.LT, twoDaysAgo)))).containsExactly("bob");
        assertThat(users(bob, when(compare("lastLoginAt", ComparisonOperator.LTE, twoDaysAgo)))).containsExactly("bob");
        assertThat(users(bob, when(new Condition.Comparison("username", ComparisonOperator.EQ, null, ConditionVariable.SUBJECT_USERNAME))))
                .containsExactly("bob");
        assertThat(users(bob, when(new Condition.Comparison("lastLoginAt", ComparisonOperator.LT, null, ConditionVariable.NOW))))
                .containsExactly("alice", "bob");
        assertThat(users(bob, when(new Condition.AnyOf(List.of(compare("username", ComparisonOperator.EQ, "alice"),
                new Condition.AllOf(List.of(compare("status", ComparisonOperator.EQ, "ACTIVE"),
                        new Condition.Negation(compare("username", ComparisonOperator.STARTS_WITH, "a"))))))))).containsExactly("alice", "bob",
                "carol");
    }

    @Test
    void listVariablesAndNumbersWork()
    {
        DataSubject carol = subject(data.carol, data.acme, "carol", List.of(data.hq, data.ops));
        SecuredEntityDefinition unit = entities.find("org-unit").orElseThrow();
        // The variable of the reader's departments, compared with department ids.
        assertThat(data.find(OrgUnit.class, DataScopeSpecifications.of(unit, new DataAccess.Rules(List.of(when(new Condition.Comparison("id",
                ComparisonOperator.IN, null, ConditionVariable.SUBJECT_ORG_UNIT_IDS))), List.of()), carol, DataScopeFixture.NOW), OrgUnit::getCode))
                .containsExactly("hq", "ops");
        assertThat(data.find(OrgUnit.class, DataScopeSpecifications.of(unit, new DataAccess.Rules(List.of(when(compare("depth",
                ComparisonOperator.GT, BigDecimal.ZERO))), List.of()), carol, DataScopeFixture.NOW), OrgUnit::getCode)).containsExactly("rd");
        DataSubject lonely = new DataSubject(data.dave, data.acme, "dave", List.of(), List.of(), List.of(), List.of());
        assertThat(data.find(OrgUnit.class, DataScopeSpecifications.of(unit, new DataAccess.Rules(List.of(when(new Condition.Comparison("id",
                ComparisonOperator.IN, null, ConditionVariable.SUBJECT_ORG_UNIT_IDS))), List.of()), lonely, DataScopeFixture.NOW), OrgUnit::getCode))
                .isEmpty();
        assertThat(users(lonely, when(new Condition.Comparison("username", ComparisonOperator.NOT_IN, null,
                ConditionVariable.SUBJECT_GROUP_CODES)))).containsExactly("alice", "bob", "carol", "dave");
        assertThat(users(lonely, when(new Condition.Comparison("username", ComparisonOperator.IN, null,
                ConditionVariable.SUBJECT_POSITION_CODES)))).isEmpty();
        assertThat(users(lonely, when(new Condition.Comparison("lastLoginAt", ComparisonOperator.EQ, null, ConditionVariable.NOW)))).isEmpty();
        assertThat(data.find(OrgUnit.class, DataScopeSpecifications.of(unit, new DataAccess.Rules(List.of(when(new Condition.Comparison("id",
                ComparisonOperator.NE, null, ConditionVariable.SUBJECT_ID))), List.of()), lonely, DataScopeFixture.NOW), OrgUnit::getCode))
                .containsExactly("hq", "ops", "rd");
    }

    @Test
    void convertsValuesToTheFieldsType()
    {
        BigDecimal seven = new BigDecimal("7");
        assertThat(DataScopeSpecifications.convert(seven, long.class)).isEqualTo(7L);
        assertThat(DataScopeSpecifications.convert(seven, Integer.class)).isEqualTo(7);
        assertThat(DataScopeSpecifications.convert(seven, short.class)).isEqualTo((short) 7);
        assertThat(DataScopeSpecifications.convert(seven, Double.class)).isEqualTo(7.0);
        assertThat(DataScopeSpecifications.convert(seven, float.class)).isEqualTo(7.0f);
        assertThat(DataScopeSpecifications.convert(seven, BigInteger.class)).isEqualTo(BigInteger.valueOf(7));
        assertThat(DataScopeSpecifications.convert(seven, BigDecimal.class)).isEqualTo(seven);
        assertThat(DataScopeSpecifications.convert("ACTIVE", AccountStatus.class)).isEqualTo(AccountStatus.ACTIVE);
        assertThat(DataScopeSpecifications.convert("x", String.class)).isEqualTo("x");
        Instant moment = Instant.parse("2026-10-02T12:30:00Z");
        assertThat(DataScopeSpecifications.convert(moment, LocalDateTime.class)).isEqualTo(LocalDateTime.of(2026, 10, 2, 12, 30));
        assertThat(DataScopeSpecifications.convert(moment, LocalDate.class)).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(DataScopeSpecifications.convert(moment, OffsetDateTime.class)).isEqualTo(moment.atOffset(ZoneOffset.UTC));
        assertThat(DataScopeSpecifications.convert(moment, Instant.class)).isEqualTo(moment);
        assertThat(DataScopeSpecifications.escape("a_b%c\\d")).isEqualTo("a\\_b\\%c\\\\d");
    }
}
