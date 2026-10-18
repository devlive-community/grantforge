// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka.agent;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AccessControlEntryFilter;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclBindingFilter;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourcePatternFilter;
import org.apache.kafka.common.resource.ResourceType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AclViewTest
{
    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

    private static AclBinding acl(ResourceType type, String name, PatternType pattern, String principal, AclOperation operation,
            AclPermissionType permission)
    {
        return new AclBinding(new ResourcePattern(type, name, pattern), new AccessControlEntry(principal, "*", operation, permission));
    }

    private static AclBinding allowTopic(String name, PatternType pattern, String principal, AclOperation operation)
    {
        return acl(ResourceType.TOPIC, name, pattern, principal, operation, AclPermissionType.ALLOW);
    }

    @Test
    void showsPoliciesAsLiteralPrefixedAndWildcardAcls()
    {
        KafkaSnapshots snapshots = new KafkaSnapshots()
                .allow(List.of("alice"), List.of("read", "write"), "topic", "orders")
                .allow(List.of("@ops", "+auditor"), List.of("describe"), "topic", "pay*", "*")
                .allow(List.of("@public"), List.of("describe_configs"), "cluster", "kafka-cluster")
                .deny(List.of("bob"), List.of("all"), "group", "billing");
        assertThat(AclView.of(snapshots.snapshot(), AclBindingFilter.ANY, NOW)).containsExactly(
                allowTopic("orders", PatternType.LITERAL, "User:alice", AclOperation.READ),
                allowTopic("orders", PatternType.LITERAL, "User:alice", AclOperation.WRITE),
                allowTopic("pay", PatternType.PREFIXED, "Group:ops", AclOperation.DESCRIBE),
                allowTopic("pay", PatternType.PREFIXED, "Role:auditor", AclOperation.DESCRIBE),
                allowTopic("*", PatternType.LITERAL, "Group:ops", AclOperation.DESCRIBE),
                allowTopic("*", PatternType.LITERAL, "Role:auditor", AclOperation.DESCRIBE),
                acl(ResourceType.CLUSTER, "kafka-cluster", PatternType.LITERAL, "User:*", AclOperation.DESCRIBE_CONFIGS, AclPermissionType.ALLOW),
                acl(ResourceType.GROUP, "billing", PatternType.LITERAL, "User:bob", AclOperation.ALL, AclPermissionType.DENY));
    }

    @Test
    void returnsOnlyWhatMatchesTheFilterWithoutDuplicates()
    {
        KafkaSnapshots snapshots = new KafkaSnapshots()
                .allow(List.of("alice"), List.of("read"), "topic", "orders")
                .allow(List.of("alice"), List.of("read"), "topic", "orders")
                .allow(List.of("bob"), List.of("read"), "topic", "payments");
        AclBindingFilter orders = new AclBindingFilter(new ResourcePatternFilter(ResourceType.TOPIC, "orders", PatternType.LITERAL),
                AccessControlEntryFilter.ANY);
        assertThat(AclView.of(snapshots.snapshot(), orders, NOW)).containsExactly(allowTopic("orders", PatternType.LITERAL, "User:alice",
                AclOperation.READ));
    }

    @Test
    void leavesOutWhatAclsCannotExpress()
    {
        KafkaSnapshots snapshots = new KafkaSnapshots().level("other").accessType("fly")
                .allow(List.of("alice"), List.of("read"), "topic", "a*b", "x?", "*orders", "kept")
                .allow(List.of("alice"), List.of("read"), "other", "x")
                .allow(List.of("alice"), List.of("fly", "write"), "topic", "flights");
        KafkaSnapshots.item(snapshots.policy("topic", true, "secret").putArray("allow"), List.of("alice"), List.of("read"));
        ObjectNode conditional = KafkaSnapshots.item(snapshots.policy("topic", false, "conditional").putArray("allow"), List.of("alice"),
                List.of("read"));
        conditional.putArray("conditions").addObject().put("type", "ip").putArray("values").add("10.0.0.0/8");
        ObjectNode excepted = snapshots.policy("topic", false, "excepted");
        KafkaSnapshots.item(excepted.putArray("allow"), List.of("@staff"), List.of("read"));
        KafkaSnapshots.item(excepted.putArray("allowExceptions"), List.of("carol"), List.of("read"));
        KafkaSnapshots.item(excepted.putArray("deny"), List.of("dave"), List.of("write"));
        ObjectNode partly = snapshots.policy("topic", false, "partly");
        partly.putArray("allow");
        KafkaSnapshots.item(partly.putArray("deny"), List.of("@staff"), List.of("write"));
        KafkaSnapshots.item(partly.putArray("denyExceptions"), List.of("erin"), List.of("write"));
        ObjectNode expired = snapshots.policy("topic", false, "expired");
        KafkaSnapshots.item(expired.putArray("allow"), List.of("alice"), List.of("read"));
        expired.putArray("validity").addObject().put("from", "2026-01-01T00:00:00Z").put("until", "2026-02-01T00:00:00Z");
        assertThat(AclView.of(snapshots.snapshot(), AclBindingFilter.ANY, NOW)).containsExactly(
                allowTopic("kept", PatternType.LITERAL, "User:alice", AclOperation.READ),
                allowTopic("flights", PatternType.LITERAL, "User:alice", AclOperation.WRITE),
                acl(ResourceType.TOPIC, "excepted", PatternType.LITERAL, "User:dave", AclOperation.WRITE, AclPermissionType.DENY));
    }

    @Test
    void writesPatternsAsKafkaDoes()
    {
        assertThat(AclView.pattern(ResourceType.TOPIC, "*")).isEqualTo(new ResourcePattern(ResourceType.TOPIC, "*", PatternType.LITERAL));
        assertThat(AclView.pattern(ResourceType.GROUP, "app-*")).isEqualTo(new ResourcePattern(ResourceType.GROUP, "app-",
                PatternType.PREFIXED));
        assertThat(AclView.pattern(ResourceType.TOPIC, "orders")).isEqualTo(new ResourcePattern(ResourceType.TOPIC, "orders",
                PatternType.LITERAL));
        assertThat(AclView.pattern(ResourceType.TOPIC, "**")).isNull();
        assertThat(AclView.pattern(ResourceType.TOPIC, "a?")).isNull();
        assertThat(AclView.pattern(ResourceType.TOPIC, "*a")).isNull();
    }
}
