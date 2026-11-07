// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TargetTest
{
    @Test
    void readsAndWritesResourcesAsARoleHoldsThem()
    {
        assertThat(Target.of(new Document("cluster", true))).isEqualTo(Target.DEPLOYMENT);
        assertThat(Target.of(new Document("db", "sales").append("collection", "orders"))).isEqualTo(Target.collection("sales", "orders"));
        assertThat(Target.of(new Document("db", "sales").append("collection", ""))).isEqualTo(Target.database("sales"));
        assertThat(Target.of(new Document("anyResource", true))).as("what the agent does not grant").isNull();
        assertThat(Target.of(new Document("db", "sales"))).isNull();
        assertThat(Target.of(new Document("collection", "orders"))).isNull();
        assertThat(Target.DEPLOYMENT.document()).isEqualTo(new Document("cluster", true));
        assertThat(new Target(null, "x").document()).isEqualTo(new Document("cluster", true));
        assertThat(new Target("x", null).document()).isEqualTo(new Document("cluster", true));
        assertThat(Target.collection("sales", "orders").document()).isEqualTo(new Document("db", "sales").append("collection", "orders"));
    }

    @Test
    void coversWhatMongoDbSaysItCovers()
    {
        assertThat(Target.collection("sales", "orders").covers("sales", "orders")).isTrue();
        assertThat(Target.collection("sales", "orders").covers("sales", "items")).isFalse();
        assertThat(Target.collection("sales", "orders").covers("hr", "orders")).isFalse();
        assertThat(Target.database("sales").covers("sales", "orders")).isTrue();
        assertThat(Target.database("sales").covers("hr", "orders")).isFalse();
        assertThat(new Target("", "").covers("hr", "orders")).as("every database").isTrue();
        assertThat(new Target("", "orders").covers("hr", "orders")).isTrue();
        assertThat(new Target("", "orders").covers("hr", "items")).isFalse();
        assertThat(Target.DEPLOYMENT.covers("hr", "orders")).isFalse();
        assertThat(new Target("hr", null).covers("hr", "orders")).isFalse();
    }

    @Test
    void coversADatabaseAsSuchOnlyWithAllItsCollections()
    {
        assertThat(Target.database("sales").coversAll("sales")).isTrue();
        assertThat(Target.database("sales").coversAll("hr")).isFalse();
        assertThat(new Target("", "").coversAll("hr")).isTrue();
        assertThat(Target.collection("sales", "orders").coversAll("sales")).isFalse();
        assertThat(Target.DEPLOYMENT.coversAll("sales")).isFalse();
        assertThat(new Target("sales", null).coversAll("sales")).isFalse();
    }

    @Test
    void labelsItselfForMessages()
    {
        assertThat(Target.DEPLOYMENT.label()).isEqualTo("the deployment");
        assertThat(new Target(null, "x").label()).isEqualTo("the deployment");
        assertThat(Target.collection("sales", "orders").label()).isEqualTo("sales.orders");
        assertThat(Target.database("sales").label()).isEqualTo("sales.*");
        assertThat(new Target("", "").label()).isEqualTo("*.*");
    }

    @Test
    void sortsTheDeploymentFirst()
    {
        assertThat(Target.ORDER.compare(Target.DEPLOYMENT, Target.database("sales"))).isNegative();
        assertThat(Target.ORDER.compare(Target.database("sales"), Target.collection("sales", "orders"))).isNegative();
        assertThat(Target.ORDER.compare(Target.collection("hr", "a"), Target.collection("sales", "a"))).isNegative();
    }
}
