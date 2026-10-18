// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.kafka.agent;

import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.resource.ResourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaAccessTest
{
    @Test
    void namesResourceTypesAsThePluginsLevels()
    {
        assertThat(KafkaAccess.level(ResourceType.TOPIC)).isEqualTo("topic");
        assertThat(KafkaAccess.level(ResourceType.TRANSACTIONAL_ID)).isEqualTo("transactional_id");
        assertThat(KafkaAccess.level(ResourceType.DELEGATION_TOKEN)).isEqualTo("delegation_token");
        assertThat(KafkaAccess.level(ResourceType.ANY)).isNull();
        assertThat(KafkaAccess.level(ResourceType.UNKNOWN)).isNull();
        for (String level : KafkaAccess.RESOURCES) {
            ResourceType type = KafkaAccess.resourceType(level);
            assertThat(type).isNotNull();
            assertThat(KafkaAccess.level(type)).isEqualTo(level);
        }
        assertThat(KafkaAccess.resourceType("any")).isNull();
        assertThat(KafkaAccess.resourceType("database")).isNull();
    }

    @Test
    void namesOperationsAsThePluginsAccessTypes()
    {
        assertThat(KafkaAccess.accessType(AclOperation.IDEMPOTENT_WRITE)).isEqualTo("idempotent_write");
        assertThat(KafkaAccess.accessType(AclOperation.ALL)).isEqualTo("all");
        assertThat(KafkaAccess.accessType(AclOperation.ANY)).isNull();
        assertThat(KafkaAccess.accessType(AclOperation.UNKNOWN)).isNull();
        for (String accessType : KafkaAccess.ACCESS_TYPES) {
            AclOperation operation = KafkaAccess.operation(accessType);
            assertThat(operation).isNotNull();
            assertThat(KafkaAccess.accessType(operation)).isEqualTo(accessType);
        }
        assertThat(KafkaAccess.operation("any")).isNull();
        assertThat(KafkaAccess.operation("select")).isNull();
    }

    @Test
    void namesRequestTypes()
    {
        assertThat(KafkaAccess.api(0)).isEqualTo("PRODUCE");
        assertThat(KafkaAccess.api(1)).isEqualTo("FETCH");
        assertThat(KafkaAccess.api(-1)).isNull();
        assertThat(KafkaAccess.api(30_000)).isNull();
        assertThat(KafkaAccess.api(Integer.MAX_VALUE)).isNull();
        assertThat(KafkaAccess.api(Integer.MIN_VALUE)).isNull();
    }
}
