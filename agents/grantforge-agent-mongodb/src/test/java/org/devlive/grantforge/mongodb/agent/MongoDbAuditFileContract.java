// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The agent reading the audit log of Percona Server for MongoDB, which writes the same {@code authCheck} events as MongoDB
 * Enterprise: what was allowed and what was refused, with who decided, what an aggregation joined, and that no argument
 * reaches GrantForge, passwords and documents least of all.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class MongoDbAuditFileContract
{
    @TempDir
    static Path files;

    private @Nullable MongoNode node;
    private @Nullable AgentServer server;
    private @Nullable MongoDbAgent agent;

    /**
     * The release under test.
     *
     * @return such as {@code 8.0.34}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        Path shared = Files.createDirectory(files.resolve("shared"));
        Files.setPosixFilePermissions(shared, PosixFilePermissions.fromString("rwxrwxrwx"));
        TestCertificates certificates = new TestCertificates(Files.createDirectory(files.resolve("certificates")));
        MongoNode started = new MongoNode("percona/percona-server-mongodb:" + version(), certificates, shared,
                "--auditDestination file --auditFormat JSON --auditPath /shared/audit.json --setParameter auditAuthorizationSuccess=true");
        node = started;
        started.run("""
                const admin = db.getSiblingDB('admin');
                const sales = db.getSiblingDB('sales');
                sales.orders.insertOne({_id: 1});
                sales.items.insertOne({_id: 1});
                sales.secret.insertOne({_id: 1});
                admin.createRole({role: 'inventory', privileges: [{resource: {db: '', collection: ''}, actions: ['listCollections']}], roles: []});
                admin.createUser({user: 'grantforge', pwd: 'agent-secret', roles: ['userAdminAnyDatabase', 'clusterMonitor', 'inventory']});
                admin.createUser({user: 'alice', pwd: 'alice-secret', roles: []});
                """);
        Path audit = shared.resolve("audit.json");
        eventually("the server writes its audit log", () -> Files.isReadable(audit));
        AgentServer running = new AgentServer(new MongoSnapshots());
        server = running;
        running.publish(new MongoSnapshots().allow(List.of("alice"), List.of("read"), "database", "sales", "collection", "orders")
                .allow(List.of("alice"), List.of("read"), "database", "sales", "collection", "items")
                .allow(List.of("alice"), List.of("insert"), "database", "sales", "collection", "orders")
                .deny(List.of("alice"), List.of("read"), "database", "sales", "collection", "secret"));
        Map<String, String> values = new HashMap<>(Map.of("grantforge.server.url", "http://127.0.0.1:" + running.port(),
                "grantforge.token.file", Files.writeString(files.resolve("token"), AgentServer.TOKEN).toString(), "grantforge.instance", "mongodb-audit",
                "grantforge.cache.dir", files.resolve("cache").toString(), "grantforge.refresh.interval.ms", "1000", "grantforge.audit.flush.interval.ms", "200",
                "grantforge.mongodb.uri", started.uri(), "grantforge.mongodb.user", "grantforge",
                "grantforge.mongodb.password.file", Files.writeString(files.resolve("password"), "agent-secret").toString()));
        values.putAll(Map.of("grantforge.mongodb.audit.files", audit.toString(), "grantforge.sync.interval.ms", "3600000", "grantforge.audit.log.interval.ms", "600000",
                "grantforge.audit.fold.window.ms", "0"));
        agent = MongoDbAgent.start(AgentConfig.parse(values, "test"));
        eventually("the agent takes the policies", () -> {
            Snapshot snapshot = agent().agent().snapshot();
            return snapshot != null && snapshot.policyVersion() == 2;
        });
        agent().syncNow();
        agent().readAccessesNow();
    }

    @AfterAll
    void stop()
    {
        if (agent != null) {
            agent.close();
        }
        if (server != null) {
            server.close();
        }
        if (node != null) {
            node.close();
        }
    }

    private MongoDbAgent agent()
    {
        return requireNonNull(agent, "agent");
    }

    private AgentServer server()
    {
        return requireNonNull(server, "server");
    }

    private static void eventually(String what, BooleanSupplier condition) throws InterruptedException
    {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("timed out waiting until " + what);
            }
            Thread.sleep(100);
        }
    }

    private JsonNode event(String resource, String type) throws InterruptedException
    {
        eventually(type + " on " + resource + " is reported", () -> agent().readAccessesNow() >= 0 && server().events().stream()
                .anyMatch(event -> resource.equals(event.path("resource").asText()) && type.equals(event.path("accessType").asText())));
        return server().events().stream().filter(event -> resource.equals(event.path("resource").asText()) && type.equals(event.path("accessType").asText())).findFirst().orElseThrow();
    }

    @Test
    void reportsWhatWasAllowedAndRefusedWithWhoDecided() throws Exception
    {
        try (MongoClient client = requireNonNull(node, "node").client("alice", "alice-secret", "admin")) {
            var sales = client.getDatabase("sales");
            sales.getCollection("orders").find().first();
            sales.getCollection("orders").insertOne(new Document("card", "4111-do-not-report"));
            sales.getCollection("orders").aggregate(List.of(new Document("$lookup", new Document("from", "items").append("localField", "_id").append("foreignField", "_id")
                    .append("as", "joined")))).first();
            try {
                sales.getCollection("secret").find().first();
            }
            catch (MongoCommandException refused) {
                assertThat(refused.getErrorCode()).isEqualTo(13);
            }
            try {
                sales.getCollection("orders").drop();
            }
            catch (MongoCommandException refused) {
                assertThat(refused.getErrorCode()).isEqualTo(13);
            }
        }
        JsonNode read = event("sales.orders", "read");
        assertThat(read.path("user").asText()).isEqualTo("alice");
        assertThat(read.path("outcome").asText()).isEqualTo("ALLOWED");
        assertThat(read.path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(read.path("clientIp").asText()).isNotEmpty();
        assertThat(event("sales.items", "read").path("action").asText()).as("what an aggregation joined").isEqualTo("aggregate");

        JsonNode secret = event("sales.secret", "read");
        assertThat(secret.path("outcome").asText()).isEqualTo("DENIED");
        assertThat(secret.path("enforcer").asText()).isEqualTo("GRANTFORGE");
        JsonNode drop = event("sales.orders", "drop");
        assertThat(drop.path("outcome").asText()).isEqualTo("DENIED");

        assertThat(event("sales.orders", "insert").path("outcome").asText()).isEqualTo("ALLOWED");
        assertThat(server().events().toString()).as("no argument reaches GrantForge").doesNotContain("4111-do-not-report", "alice-secret", "agent-secret");
        assertThat(server().events()).noneMatch(event -> "grantforge".equals(event.path("user").asText()));
    }
}
