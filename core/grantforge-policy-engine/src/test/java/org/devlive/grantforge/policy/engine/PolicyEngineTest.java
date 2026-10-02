// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.policy.engine;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolicyEngineTest
{
    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    private static PolicyItem users(String accessType, String... names)
    {
        return PolicyItem.builder().users(names).accessTypes(accessType).build();
    }

    private static Policy.Builder table(long id, String database, String table)
    {
        return Policy.builder(id).resource("database", ResourceSpec.of(database)).resource("table", ResourceSpec.of(table))
                .resource("column", ResourceSpec.of("*"));
    }

    private static AccessRequest.Builder request(String user, String accessType, String database, String table)
    {
        return AccessRequest.builder(user, accessType).resource("database", database).resource("table", table).time(NOW);
    }

    private static Decision decide(ServiceModel model, List<Policy> policies, AccessRequest request)
    {
        return PolicyEngine.create(model, policies, Map.of()).evaluate(request);
    }

    private static Decision hive(List<Policy> policies, AccessRequest request)
    {
        return decide(Models.HIVE, policies, request);
    }

    @Test
    void denialsWinOverAllowancesAndExceptionsCarveThemOut()
    {
        Policy allowAll = table(5, "sales", "*").allow(users("select", "alice", "bob"))
                .allowExceptions(users("select", "bob")).build();
        Policy denyOrders = table(3, "sales", "orders").deny(users("select", "alice", "carol"))
                .denyExceptions(users("select", "carol")).build();
        List<Policy> policies = List.of(allowAll, denyOrders);

        assertThat(hive(policies, request("alice", "select", "sales", "items").build())).isEqualTo(Decision.allowedBy(5));
        assertThat(hive(policies, request("alice", "select", "sales", "orders").build())).isEqualTo(Decision.deniedBy(3));
        // Bob is an exception to the allowance; Carol to the denial, but nothing allows her.
        assertThat(hive(policies, request("bob", "select", "sales", "items").build()).outcome())
                .isEqualTo(Decision.Outcome.NOT_DETERMINED);
        assertThat(hive(policies, request("carol", "select", "sales", "orders").build()).policyId()).isNull();
        assertThat(hive(policies, request("alice", "update", "sales", "items").build()).allowed()).isFalse();
    }

    @Test
    void overridePoliciesDecideBeforeNormalOnes()
    {
        Policy normalDeny = table(1, "*", "*").deny(users("select", "alice")).build();
        Policy overrideAllow = table(2, "hr", "*").priority(Priority.OVERRIDE).allow(users("select", "alice")).build();
        Policy normalAllow = table(3, "*", "*").allow(users("select", "bob")).build();
        Policy overrideDeny = table(4, "hr", "salaries").priority(Priority.OVERRIDE).deny(users("select", "bob")).build();
        List<Policy> policies = List.of(normalDeny, overrideAllow, normalAllow, overrideDeny);

        assertThat(hive(policies, request("alice", "select", "hr", "people").build())).isEqualTo(Decision.allowedBy(2));
        assertThat(hive(policies, request("alice", "select", "sales", "items").build())).isEqualTo(Decision.deniedBy(1));
        assertThat(hive(policies, request("bob", "select", "hr", "salaries").build())).isEqualTo(Decision.deniedBy(4));
        assertThat(hive(policies, request("bob", "select", "hr", "people").build())).isEqualTo(Decision.allowedBy(3));
    }

    @Test
    void groupsRolesPublicAndImpliedAccessTypesGrantToo()
    {
        Policy policy = table(1, "sales", "*").allow(PolicyItem.builder().groups("analysts").accessTypes("select").build(),
                PolicyItem.builder().roles("auditor").accessTypes("all").build()).build();
        Policy open = table(2, "public", "*").allow(PolicyItem.builder().groups(PolicyItem.PUBLIC).accessTypes("select").build())
                .build();
        List<Policy> policies = List.of(policy, open);

        assertThat(hive(policies, request("x", "select", "sales", "t").groups("analysts").build()).allowed()).isTrue();
        assertThat(hive(policies, request("x", "update", "sales", "t").groups("analysts").build()).allowed()).isFalse();
        assertThat(hive(policies, request("x", "drop", "sales", "t").roles("auditor").build()).allowed()).isTrue();
        assertThat(hive(policies, request("anyone", "select", "public", "t").build()).allowed()).isTrue();
        assertThat(hive(policies, request("x", "unknown", "sales", "t").roles("auditor").build()).outcome())
                .isEqualTo(Decision.Outcome.NOT_DETERMINED);
    }

    @Test
    void resourcesMatchWithWildcardsExclusionsCaseAndLevels()
    {
        Policy wildcard = table(1, "sal?s", "ord*").allow(users("select", "alice")).build();
        Policy excluded = Policy.builder(2).resource("database", ResourceSpec.of(List.of("secret"), true, false))
                .resource("table", ResourceSpec.of("*")).resource("column", ResourceSpec.of("*"))
                .allow(users("select", "bob")).build();
        Policy columns = Policy.builder(3).resource("database", ResourceSpec.of("hr")).resource("table", ResourceSpec.of("people"))
                .resource("column", ResourceSpec.of("name")).allow(users("select", "carol")).build();
        Policy databaseOnly = Policy.builder(4).resource("database", ResourceSpec.of("hr")).allow(users("drop", "dave")).build();
        List<Policy> policies = List.of(wildcard, excluded, columns, databaseOnly);

        assertThat(hive(policies, request("alice", "select", "SALES", "Orders2026").build()).allowed()).isTrue();
        assertThat(hive(policies, request("alice", "select", "sales", "items").build()).allowed()).isFalse();
        assertThat(hive(policies, request("bob", "select", "public", "x").build()).allowed()).isTrue();
        assertThat(hive(policies, request("bob", "select", "Secret", "x").build()).allowed()).isFalse();
        // A policy for a column does not cover the table, which is wider than the column.
        assertThat(hive(policies, request("carol", "select", "hr", "people").build()).allowed()).isFalse();
        assertThat(hive(policies, request("carol", "select", "hr", "people").resource("column", "name").build()).allowed()).isTrue();
        // A policy for the database does not cover its tables.
        assertThat(hive(policies, AccessRequest.builder("dave", "drop").resource("database", "hr").time(NOW).build()).allowed())
                .isTrue();
        assertThat(hive(policies, request("dave", "drop", "hr", "people").build()).allowed()).isFalse();
    }

    @Test
    void pathsMatchBySegmentAndRecursiveValuesCoverWhatLiesBelow()
    {
        Policy recursive = Policy.builder(1).resource("path", ResourceSpec.of(List.of("/data/sales/"), false, true))
                .allow(users("read", "alice")).build();
        Policy segment = Policy.builder(2).resource("path", ResourceSpec.of("/logs/*/today")).allow(users("read", "bob")).build();
        Policy everything = Policy.builder(3).resource("path", ResourceSpec.of(List.of("/"), false, true))
                .allow(users("execute", "carol")).build();
        Policy wildcardRecursive = Policy.builder(4).resource("path", ResourceSpec.of(List.of("/home/*"), false, true))
                .allow(users("write", "dave")).build();
        List<Policy> policies = List.of(recursive, segment, everything, wildcardRecursive);
        AccessRequest.Builder base = AccessRequest.builder("alice", "read").time(NOW);

        assertThat(decide(Models.HDFS, policies, path("alice", "read", "/data/sales")).allowed()).isTrue();
        assertThat(decide(Models.HDFS, policies, path("alice", "read", "/data/sales/2026/q1.csv")).allowed()).isTrue();
        assertThat(decide(Models.HDFS, policies, path("alice", "read", "/data/salesman")).allowed()).isFalse();
        assertThat(decide(Models.HDFS, policies, path("bob", "read", "/logs/app/today/")).allowed()).isTrue();
        assertThat(decide(Models.HDFS, policies, path("bob", "read", "/logs/app/x/today")).allowed()).isFalse();
        assertThat(decide(Models.HDFS, policies, path("bob", "read", "/logs/app/today/file")).allowed()).isFalse();
        assertThat(decide(Models.HDFS, policies, path("carol", "execute", "/anything/at/all")).allowed()).isTrue();
        assertThat(decide(Models.HDFS, policies, path("dave", "write", "/home/dave/notes")).allowed()).isTrue();
        assertThat(decide(Models.HDFS, policies, path("dave", "write", "/home")).allowed()).isFalse();
        assertThat(base.resource("path", "/data/sales").build().resource()).containsEntry("path", "/data/sales");
    }

    private static AccessRequest path(String user, String accessType, String path)
    {
        return AccessRequest.builder(user, accessType).resource("path", path).time(NOW).build();
    }

    @Test
    void exactAndRegularExpressionLevels()
    {
        Policy topic = Policy.builder(1).resource("topic", ResourceSpec.of("Orders")).allow(users("consume", "alice")).build();
        assertThat(decide(Models.EXACT, List.of(topic), topicRequest("Orders")).allowed()).isTrue();
        assertThat(decide(Models.EXACT, List.of(topic), topicRequest("orders")).allowed()).isFalse();
        assertThat(decide(Models.EXACT, List.of(topic), topicRequest("Orders2")).allowed()).isFalse();

        Policy urls = Policy.builder(2).resource("url", ResourceSpec.of("hdfs://cluster/(raw|clean)/.*"))
                .allow(users("select", "alice")).build();
        AccessRequest raw = AccessRequest.builder("alice", "select").resource("url", "hdfs://cluster/raw/x").time(NOW).build();
        AccessRequest other = AccessRequest.builder("alice", "select").resource("url", "hdfs://cluster/tmp/x").time(NOW).build();
        assertThat(hive(List.of(urls), raw).allowed()).isTrue();
        assertThat(hive(List.of(urls), other).allowed()).isFalse();
    }

    private static AccessRequest topicRequest(String topic)
    {
        return AccessRequest.builder("alice", "consume").resource("topic", topic).time(NOW).build();
    }

    @Test
    void conditionsMustHoldAndFailSafely()
    {
        Condition office = Condition.of("network", "office");
        Condition unknown = Condition.of("mystery", "x");
        Policy allow = table(1, "sales", "*").allow(PolicyItem.builder().users("alice").accessTypes("select").conditions(office).build(),
                PolicyItem.builder().users("bob").accessTypes("select").conditions(unknown).build()).build();
        Policy deny = table(2, "hr", "*").deny(PolicyItem.builder().users("alice").accessTypes("select").conditions(unknown).build())
                .allow(users("select", "alice")).build();
        ConditionEvaluator network = (values, request) -> values.contains(String.valueOf(request.context().get("network")));
        ConditionEvaluator broken = (values, request) -> {
            throw new IllegalStateException("broken");
        };
        PolicyEngine engine = PolicyEngine.create(Models.HIVE, List.of(allow, deny), Map.of("network", network));

        assertThat(engine.evaluate(request("alice", "select", "sales", "t").context(Map.of("network", "office")).build()).allowed())
                .isTrue();
        assertThat(engine.evaluate(request("alice", "select", "sales", "t").context(Map.of("network", "home")).build()).allowed())
                .isFalse();
        // Without an evaluator, an allowance does not apply but a denial does.
        assertThat(engine.evaluate(request("bob", "select", "sales", "t").build()).allowed()).isFalse();
        assertThat(engine.evaluate(request("alice", "select", "hr", "t").build())).isEqualTo(Decision.deniedBy(2));
        PolicyEngine failing = PolicyEngine.create(Models.HIVE, List.of(allow), Map.of("network", broken));
        assertThat(failing.evaluate(request("alice", "select", "sales", "t").build()).allowed()).isFalse();
    }

    @Test
    void policiesApplyOnlyWhileEnabledAndValid()
    {
        Policy disabled = table(1, "sales", "*").enabled(false).allow(users("select", "alice")).build();
        Policy windowed = table(2, "hr", "*").validity(Validity.between(NOW.minusSeconds(60), NOW.plusSeconds(60)))
                .validity(Validity.between(NOW.plusSeconds(3600), null)).allow(users("select", "alice")).build();
        List<Policy> policies = List.of(disabled, windowed);

        assertThat(hive(policies, request("alice", "select", "sales", "t").build()).allowed()).isFalse();
        assertThat(hive(policies, request("alice", "select", "hr", "t").build()).allowed()).isTrue();
        assertThat(hive(policies, request("alice", "select", "hr", "t").time(NOW.plusSeconds(600)).build()).allowed()).isFalse();
        assertThat(hive(policies, request("alice", "select", "hr", "t").time(NOW.plusSeconds(7200)).build()).allowed()).isTrue();
    }

    @Test
    void theSmallestDecidingPolicyIsReported()
    {
        List<Policy> policies = List.of(table(9, "*", "*").allow(users("select", "alice")).build(),
                table(4, "sales", "*").allow(users("select", "alice")).build());
        assertThat(hive(policies, request("alice", "select", "sales", "t").build())).isEqualTo(Decision.allowedBy(4));
        assertThat(hive(List.of(), request("alice", "select", "sales", "t").build())).isEqualTo(Decision.notDetermined());
    }

    @Test
    void refusesPoliciesAndRequestsThatDoNotFitTheModel()
    {
        assertThatThrownBy(() -> hive(List.of(Policy.builder(1).resource("schema", ResourceSpec.of("x")).build()),
                request("a", "select", "x", "y").build())).hasMessageContaining("unknown resource level schema");
        assertThatThrownBy(() -> hive(List.of(Policy.builder(1).resource("table", ResourceSpec.of("x")).build()),
                request("a", "select", "x", "y").build())).hasMessageContaining("every level from database down to table");
        assertThatThrownBy(() -> hive(List.of(table(1, "x", "y").allow(users("fly", "a")).build()),
                request("a", "select", "x", "y").build())).hasMessageContaining("unknown access type fly");
        assertThatThrownBy(() -> hive(List.of(Policy.builder(1).resource("database", ResourceSpec.of(List.of("x"), false, true))
                .build()), request("a", "select", "x", "y").build())).hasMessageContaining("only path levels can be recursive");
        assertThatThrownBy(() -> hive(List.of(Policy.builder(1).resource("url", ResourceSpec.of("([")).build()),
                request("a", "select", "x", "y").build())).hasMessageContaining("not a regular expression");
        assertThatThrownBy(() -> hive(List.of(), AccessRequest.builder("a", "select").resource("table", "t").time(NOW).build()))
                .hasMessageContaining("do not form a chain");
        assertThatThrownBy(() -> hive(List.of(), AccessRequest.builder("a", "select").resource("schema", "t").time(NOW).build()))
                .hasMessageContaining("unknown resource level schema");
    }
}
