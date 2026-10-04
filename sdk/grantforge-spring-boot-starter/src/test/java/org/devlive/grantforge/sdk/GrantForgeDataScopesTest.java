// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.sdk;

import org.devlive.grantforge.sdk.GrantForgeException.Reason;
import org.devlive.grantforge.sdk.jpa.TestJpaApplication;
import org.devlive.grantforge.sdk.jpa.TestOrder;
import org.devlive.grantforge.sdk.jpa.TestOrder.Status;
import org.devlive.grantforge.sdk.jpa.TestOrderRepository;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@ContextConfiguration(classes = TestJpaApplication.class)
class GrantForgeDataScopesTest
{
    private static final Instant NOW = Instant.parse("2026-10-04T00:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    // Ada is account 42 of tenant t1, in department 7, which has department 8 below it.
    private static final UserDataAccess.Subject ADA = new UserDataAccess.Subject("42", "t1", "ada", List.of("7"), List.of("7", "8"),
            List.of("buyers"), List.of());

    @Autowired
    private TestOrderRepository orders;

    @BeforeEach
    void createOrders()
    {
        orders.deleteAll();
        orders.save(new TestOrder(1, 42, 7L, "t1", "Paper for ada", Status.OPEN, new BigDecimal("10"), NOW.minusSeconds(60)));
        orders.save(new TestOrder(2, 43, 8L, "t1", "Pens 100%", Status.PAID, new BigDecimal("250"), NOW.minusSeconds(86_400)));
        orders.save(new TestOrder(3, 44, 9L, "t1", null, Status.PAID, new BigDecimal("5"), NOW));
        orders.save(new TestOrder(4, 42, 7L, "t2", "Elsewhere", Status.OPEN, new BigDecimal("1"), NOW));
        orders.save(new TestOrder(5, 45, null, "t1", "buyers_only", Status.OPEN, new BigDecimal("99"), NOW));
    }

    @Test
    void appliesEachScopeWithinTheUsersTenant()
    {
        assertThat(ids(rule(DataScope.ALL))).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(ids(rule(DataScope.TENANT))).containsExactly(1L, 2L, 3L, 5L);
        assertThat(ids(rule(DataScope.SELF))).containsExactly(1L);
        assertThat(ids(rule(DataScope.ORG))).containsExactly(1L);
        assertThat(ids(rule(DataScope.ORG_AND_CHILDREN))).containsExactly(1L, 2L);
        assertThat(ids(new UserDataAccess.Rule(DataScope.CUSTOM_ORGS, null, List.of("9")))).containsExactly(3L);
        assertThat(ids(new UserDataAccess.Rule(DataScope.CUSTOM_ORGS, null, List.of()))).isEmpty();
        assertThat(ids(new UserDataAccess.Rule(DataScope.CONDITION, null, List.of()))).isEmpty();
    }

    @Test
    void denyingRulesWinAndActionsWithoutRulesAllowNothing()
    {
        UserDataAccess access = access(List.of(new UserDataAccess.EntityRules("order", DataAction.READ, List.of(rule(DataScope.TENANT)),
                List.of(condition("{\"field\": \"status\", \"op\": \"eq\", \"value\": \"PAID\"}")))));

        assertThat(find(access, DataAction.READ)).containsExactly(1L, 5L);
        assertThat(find(access, DataAction.UPDATE)).isEmpty();
        assertThat(find(access(List.of(new UserDataAccess.EntityRules("order", DataAction.READ, List.of(), List.of()))), DataAction.READ))
                .isEmpty();
    }

    @Test
    void translatesConditions()
    {
        assertThat(where("{\"field\": \"total\", \"op\": \"gt\", \"value\": 9}")).containsExactly(1L, 2L, 5L);
        assertThat(where("{\"field\": \"total\", \"op\": \"lte\", \"value\": 10}")).containsExactly(1L, 3L);
        assertThat(where("{\"field\": \"total\", \"op\": \"gte\", \"value\": 250}")).containsExactly(2L);
        assertThat(where("{\"field\": \"total\", \"op\": \"lt\", \"value\": 6}")).containsExactly(3L);
        assertThat(where("{\"field\": \"title\", \"op\": \"ne\", \"value\": \"Paper for ada\"}")).containsExactly(2L, 3L, 5L);
        assertThat(where("{\"field\": \"title\", \"op\": \"is_null\"}")).containsExactly(3L);
        assertThat(where("{\"field\": \"title\", \"op\": \"not_null\"}")).containsExactly(1L, 2L, 5L);
        assertThat(where("{\"field\": \"title\", \"op\": \"contains\", \"value\": \"100%\"}")).containsExactly(2L);
        assertThat(where("{\"field\": \"title\", \"op\": \"starts_with\", \"value\": \"buyers_\"}")).containsExactly(5L);
        assertThat(where("{\"field\": \"status\", \"op\": \"in\", \"value\": [\"PAID\"]}")).containsExactly(2L, 3L);
        assertThat(where("{\"field\": \"status\", \"op\": \"not_in\", \"value\": [\"PAID\"]}")).containsExactly(1L, 5L);
        assertThat(where("{\"field\": \"status\", \"op\": \"in\", \"value\": []}")).isEmpty();
        assertThat(where("{\"field\": \"card\", \"op\": \"eq\", \"value\": true}")).containsExactly(2L, 3L);
        assertThat(where("{\"field\": \"createdAt\", \"op\": \"lt\", \"value\": \"2026-10-03T12:00:00Z\"}")).containsExactly(2L);
        assertThat(where("{\"field\": \"createdAt\", \"op\": \"gte\", \"value\": {\"var\": \"now\"}}")).containsExactly(3L, 5L);
        assertThat(where("{\"field\": \"title\", \"op\": \"contains\", \"value\": {\"var\": \"subject.username\"}}")).containsExactly(1L);
        assertThat(where("{\"field\": \"title\", \"op\": \"in\", \"value\": {\"var\": \"subject.groupCodes\"}}")).isEmpty();
        assertThat(where("{\"field\": \"total\", \"op\": \"in\", \"value\": {\"var\": \"subject.id\"}}")).isEmpty();
        assertThat(where("{\"and\": [{\"field\": \"status\", \"op\": \"eq\", \"value\": \"OPEN\"}, {\"not\": "
                + "{\"field\": \"total\", \"op\": \"gt\", \"value\": 50}}]}")).containsExactly(1L);
        assertThat(where("{\"or\": [{\"field\": \"total\", \"op\": \"eq\", \"value\": 5}, {\"field\": \"total\", \"op\": \"eq\", \"value\": 99}]}"))
                .containsExactly(3L, 5L);
        // An operator this starter does not know selects nothing.
        assertThat(where("{\"field\": \"total\", \"op\": \"between\", \"value\": 5}")).isEmpty();
        assertThatThrownBy(() -> where("{\"field\": \"title\", \"op\": \"eq\", \"value\": {\"var\": \"subject.orgUnitIds\"}}"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> where("{\"field\": \"title\", \"op\": \"eq\", \"value\": {\"var\": \"subject.mood\"}}"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scopesTheCurrentUserAndRefusesOtherClasses()
    {
        GrantForgeClient client = mock(GrantForgeClient.class);
        when(client.dataAccess("t")).thenReturn(access(List.of(new UserDataAccess.EntityRules("order", DataAction.READ,
                List.of(rule(DataScope.SELF)), List.of()))));
        GrantForgeDataScopes scopes = new GrantForgeDataScopes(client, () -> "t", Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(orders.findAll(scopes.scope(TestOrder.class, DataAction.READ))).extracting(TestOrder::getId).containsExactly(1L);
        assertThatThrownBy(() -> new GrantForgeDataScopes(client, () -> null, Clock.systemUTC()).scope(TestOrder.class, DataAction.READ))
                .isInstanceOfSatisfying(GrantForgeException.class, refused -> assertThat(refused.getReason()).isEqualTo(Reason.UNAUTHENTICATED));
        assertThatThrownBy(() -> GrantForgeDataScopes.of(String.class, access(List.of()), DataAction.READ, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<Long> where(String condition)
    {
        return ids(condition(condition));
    }

    private List<Long> ids(UserDataAccess.Rule rule)
    {
        return find(access(List.of(new UserDataAccess.EntityRules("order", DataAction.READ, List.of(rule), List.of()))), DataAction.READ);
    }

    private List<Long> find(UserDataAccess access, DataAction action)
    {
        Specification<TestOrder> scope = GrantForgeDataScopes.of(TestOrder.class, access, action, NOW);
        return orders.findAll(scope).stream().map(TestOrder::getId).sorted().toList();
    }

    private static UserDataAccess access(List<UserDataAccess.EntityRules> rules)
    {
        return new UserDataAccess(ADA, rules, 1);
    }

    private static UserDataAccess.Rule rule(DataScope scope)
    {
        return new UserDataAccess.Rule(scope, null, List.of());
    }

    private static UserDataAccess.Rule condition(@Nullable String json)
    {
        JsonNode condition = json == null ? null : JSON.readTree(json);
        return new UserDataAccess.Rule(DataScope.CONDITION, condition, List.of());
    }
}
