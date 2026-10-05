// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.agent;

import org.devlive.grantforge.policy.engine.AccessRequest;
import org.devlive.grantforge.policy.engine.ConditionEvaluator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SnapshotTest
{
    /** Holds when the client address starts with one of the values' first octet, enough for the test's 10.0.0.0/8. */
    private static final ConditionEvaluator FIRST_OCTET = (values, request) -> {
        Object ip = request.context().get("clientIp");
        return ip != null && values.stream().anyMatch(range -> ip.toString().startsWith(range.substring(0, range.indexOf('.') + 1)));
    };

    private static Snapshot parse(String json, Map<String, ConditionEvaluator> evaluators)
    {
        return Snapshot.parse(json.getBytes(StandardCharsets.UTF_8), evaluators);
    }

    private static Snapshot snapshot()
    {
        return parse(Snapshots.json(7, true), Map.of("ip-range", FIRST_OCTET));
    }

    @Test
    void readsTheServiceAndCountsPolicies()
    {
        Snapshot snapshot = snapshot();

        assertThat(snapshot.service()).isEqualTo("warehouse");
        assertThat(snapshot.serviceType()).isEqualTo("hive");
        assertThat(snapshot.serviceEnabled()).isTrue();
        assertThat(snapshot.policyVersion()).isEqualTo(7);
        assertThat(snapshot.accessPolicies()).isEqualTo(3);
        assertThat(snapshot.otherPolicies()).isEqualTo(1);
        assertThat(snapshot.rolesOf("alice")).containsExactly("analyst");
        assertThat(snapshot.groupsOf("bob")).containsExactly("ops", "spies");
        assertThat(snapshot.rolesOf("nobody")).isEmpty();
    }

    @Test
    void decidesWithTheRolesAndGroupsTheSnapshotGives()
    {
        Snapshot snapshot = snapshot();

        AgentDecision analyst = snapshot.decide(Snapshots.request("alice", "select", "sales", "orders"));
        assertThat(analyst.outcome()).isEqualTo(AgentDecision.Outcome.ALLOWED);
        assertThat(analyst.policyId()).isEqualTo(11L);
        assertThat(analyst.policyVersion()).isEqualTo(7L);
        assertThat(snapshot.decide(Snapshots.request("bob", "select", "sales", "orders")).allowed()).isTrue();
        AgentDecision spy = snapshot.decide(Snapshots.request("bob", "update", "sales", "orders"));
        assertThat(spy.outcome()).isEqualTo(AgentDecision.Outcome.DENIED);
        assertThat(spy.policyId()).isEqualTo(11L);
        assertThat(snapshot.decide(Snapshots.request("mallory", "select", "sales", "orders")).outcome())
                .isEqualTo(AgentDecision.Outcome.NOT_DETERMINED);
        // Groups the system knows count as well as those the snapshot gives.
        AccessRequest fromSystem = AccessRequest.builder("zed", "select").groups("ops").resource("database", "sales")
                .resource("table", "t").resource("column", "c").build();
        assertThat(snapshot.decide(fromSystem).allowed()).isTrue();
    }

    @Test
    void appliesConditionsThroughTheAgentsEvaluators()
    {
        Snapshot snapshot = snapshot();
        AccessRequest inside = AccessRequest.builder("carol", "select").resource("database", "hr").resource("table", "salaries")
                .resource("column", "pay").context(Map.of("clientIp", "10.1.2.3")).build();
        AccessRequest outside = AccessRequest.builder("carol", "select").resource("database", "hr").resource("table", "salaries")
                .resource("column", "pay").context(Map.of("clientIp", "192.168.1.1")).build();

        assertThat(snapshot.decide(inside).outcome()).isEqualTo(AgentDecision.Outcome.DENIED);
        assertThat(snapshot.decide(outside).allowed()).isTrue();
        // Without the evaluator the deny holds, the safe direction.
        assertThat(parse(Snapshots.json(7, true), Map.of()).decide(outside).outcome()).isEqualTo(AgentDecision.Outcome.DENIED);
    }

    @Test
    void expiredPoliciesDecideNothing()
    {
        AccessRequest archive = AccessRequest.builder("dave", "select").resource("database", "archive").resource("table", "t")
                .resource("column", "c").time(Instant.parse("2026-01-01T00:00:00Z")).build();

        assertThat(snapshot().decide(archive).outcome()).isEqualTo(AgentDecision.Outcome.NOT_DETERMINED);
    }

    @Test
    void aServiceNotInUseDecidesNothing()
    {
        Snapshot disabled = parse(Snapshots.json(3, false), Map.of());

        AgentDecision decision = disabled.decide(Snapshots.request("alice", "select", "sales", "orders"));
        assertThat(decision.outcome()).isEqualTo(AgentDecision.Outcome.NOT_DETERMINED);
        assertThat(decision.policyVersion()).isEqualTo(3L);
    }

    @Test
    void refusesWhatItCannotRead()
    {
        String valid = Snapshots.json(1, true);
        assertThatIllegalArgumentException().isThrownBy(() -> parse("not json", Map.of())).withMessageContaining("JSON");
        assertThatIllegalArgumentException().isThrownBy(() -> parse("[]", Map.of())).withMessageContaining("object");
        assertThatIllegalArgumentException().isThrownBy(() -> parse(valid.replace("\"format\": 1", "\"format\": 2"), Map.of()))
                .withMessageContaining("upgrade");
        assertThatIllegalArgumentException().isThrownBy(() -> parse(valid.replace("\"WILDCARD\", \"caseSensitive\": false}],",
                "\"FUZZY\", \"caseSensitive\": false}],"), Map.of())).withMessageContaining("FUZZY");
        assertThatIllegalArgumentException().isThrownBy(() -> parse(valid.replace("\"parent\": \"table\"", "\"parent\": \"nowhere\""), Map.of()))
                .withMessageContaining("unknown parents");
        assertThatIllegalArgumentException().isThrownBy(() -> parse(valid.replace("\"id\": \"11\"", "\"id\": \"eleven\""), Map.of()))
                .withMessageContaining("eleven");
        assertThatIllegalArgumentException().isThrownBy(() -> parse(valid.replace("\"until\": \"2020-01-01T00:00:00Z\"", "\"until\": \"soon\""),
                Map.of())).withMessageContaining("until");
        assertThatIllegalArgumentException().isThrownBy(() -> parse(valid.replace("\"policyVersion\": 1", "\"policyVersion\": \"1\""), Map.of()))
                .withMessageContaining("policyVersion");
        assertThatIllegalArgumentException().isThrownBy(() -> parse(valid.replace("\"serviceEnabled\": true", "\"serviceEnabled\": 1"), Map.of()))
                .withMessageContaining("serviceEnabled");
        assertThatIllegalArgumentException().isThrownBy(() -> parse(valid.replace("\"service\": \"warehouse\",", ""), Map.of()))
                .withMessageContaining("service is missing");
        assertThatIllegalArgumentException().isThrownBy(() -> parse(valid.replace("\"roles\": {\"analyst\": [\"alice\"]}", "\"roles\": []"), Map.of()))
                .withMessageContaining("roles");
        assertThatIllegalArgumentException().isThrownBy(() -> parse(valid.replace("\"analyst\": [\"alice\"]", "\"analyst\": \"alice\""), Map.of()))
                .withMessageContaining("analyst");
        assertThatIllegalArgumentException().isThrownBy(() -> parse(valid.replace("\"analyst\": [\"alice\"]", "\"analyst\": [1]"), Map.of()))
                .withMessageContaining("analyst");
        assertThatIllegalArgumentException().isThrownBy(() -> parse(valid.replace("\"groups\": [\"ops\"]", "\"groups\": [1]"), Map.of()))
                .withMessageContaining("groups");
        assertThatIllegalArgumentException().isThrownBy(() -> parse(valid.replace("\"definition\": {", "\"definition\": [], \"x\": {"),
                Map.of())).withMessageContaining("definition");
        assertThatIllegalArgumentException().isThrownBy(() -> parse(valid.replace("\"policies\": [", "\"policies\": {\"x\": ["), Map.of()));
    }

    @Test
    void missingRolesAndGroupsMeanNone()
    {
        String bare = Snapshots.json(1, true).replace("\"roles\": {\"analyst\": [\"alice\"]},", "\"roles\": null,")
                .replace(",\n \"groups\": {\"ops\": [\"bob\"], \"spies\": [\"eve\", \"bob\"]}", "");

        Snapshot snapshot = parse(bare, Map.of());
        assertThat(snapshot.rolesOf("alice")).isEmpty();
        assertThat(snapshot.groupsOf("bob")).isEmpty();
    }
}
