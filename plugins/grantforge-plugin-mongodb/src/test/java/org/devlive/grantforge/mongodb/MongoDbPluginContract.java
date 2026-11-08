// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb;

import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The MongoDB plugin against a real replica set of one release: the connection test, lookups of the deployment, databases
 * and collections within what a policy names, what a lookup user with few privileges sees, a host that cannot be
 * reached, refused sign-ins, the older SCRAM and signing in with a client certificate over verified TLS.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
abstract class MongoDbPluginContract
{
    private final MongoDbProvider provider = new MongoDbProvider();
    private @Nullable MongoNode node;
    private @Nullable TestCertificates certificates;

    @TempDir
    private static Path files;

    /**
     * The release under test.
     *
     * @return such as {@code 8.0.34}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        TestCertificates made = new TestCertificates(files);
        certificates = made;
        MongoNode started = new MongoNode(version(), made);
        node = started;
        started.run("""
                const sales = db.getSiblingDB('sales');
                sales.orders.insertOne({_id: 1});
                sales.order_items.insertOne({_id: 1});
                sales.createView('orders_public', 'orders', [{$project: {card: 0}}]);
                sales.createCollection('events', {capped: true, size: 4096});
                db.getSiblingDB('hr').salaries.insertOne({_id: 1});
                db.getSiblingDB('sales_2026').orders.insertOne({_id: 1});
                const admin = db.getSiblingDB('admin');
                admin.createUser({user: 'lookup', pwd: 'lookup-secret', roles: ['readAnyDatabase']});
                admin.createUser({user: 'legacy', pwd: 'legacy-secret', roles: ['readAnyDatabase'], mechanisms: ['SCRAM-SHA-1']});
                sales.createUser({user: 'reader', pwd: 'reader-secret', roles: []});
                admin.createRole({role: 'orders_reader', privileges: [{resource: {db: 'sales', collection: 'orders'}, actions: ['find']}], roles: []});
                sales.grantRolesToUser('reader', [{role: 'orders_reader', db: 'admin'}]);
                db.getSiblingDB('$external').createUser({user: '%s', roles: [{role: 'readAnyDatabase', db: 'admin'}]});
                """.formatted(made.clientSubject()));
    }

    @AfterAll
    void stop()
    {
        if (node != null) {
            node.close();
        }
    }

    private MongoNode node()
    {
        return requireNonNull(node, "node");
    }

    private TestCertificates certificates()
    {
        return requireNonNull(certificates, "certificates");
    }

    private ServiceConfig config(String... more)
    {
        Map<String, String> values = new HashMap<>(Map.of(MongoDbSettings.URI, node().uri(), MongoDbSettings.USER, "lookup", MongoDbSettings.PASSWORD,
                "lookup-secret", MongoDbSettings.TIMEOUT, "10000"));
        for (int index = 0; index < more.length; index += 2) {
            values.put(more[index], more[index + 1]);
        }
        return new ServiceConfig("warehouse", values);
    }

    private ServiceConfig reader()
    {
        return config(MongoDbSettings.USER, "reader", MongoDbSettings.PASSWORD, "reader-secret", MongoDbSettings.SOURCE, "sales");
    }

    private List<String> lookup(ServiceConfig config, String resource, String typed, Map<String, List<String>> context)
    {
        return provider.lookup(new LookupRequest(config, resource, typed, context, 50));
    }

    @Test
    void theConnectionTestSignsInAndDescribesTheDeployment()
    {
        ConnectionResult result = provider.testConnection(config());
        assertThat(result.status()).as(String.valueOf(result.message())).isEqualTo(ConnectionResult.Status.SUCCEEDED);
        assertThat(result.message()).isEqualTo("MongoDB " + version() + ", replica set " + MongoNode.REPLICA_SET + ", signed in as lookup@admin, 3 databases");
        assertThat(provider.testConnection(reader()).message()).contains("signed in as reader@sales", "1 database").doesNotContain("1 databases");
    }

    @Test
    void looksUpTheDeploymentDatabasesAndCollections()
    {
        ServiceConfig config = config();
        assertThat(lookup(config, MongoDbProvider.DEPLOYMENT, "", Map.of())).containsExactly(MongoNode.REPLICA_SET);
        assertThat(lookup(config, MongoDbProvider.DATABASE, "", Map.of())).as("MongoDB's own databases are left out").containsExactly("hr", "sales", "sales_2026");
        assertThat(lookup(config, MongoDbProvider.DATABASE, "sa", Map.of())).containsExactly("sales", "sales_2026");
        assertThat(lookup(config, MongoDbProvider.COLLECTION, "", Map.of(MongoDbProvider.DATABASE, List.of("sales"))))
                .as("views count as collections, system collections are left out").containsExactly("events", "order_items", "orders", "orders_public");
        assertThat(lookup(config, MongoDbProvider.COLLECTION, "order", Map.of(MongoDbProvider.DATABASE, List.of("sales_*")))).containsExactly("orders");
        assertThat(lookup(config, MongoDbProvider.COLLECTION, "sal", Map.of(MongoDbProvider.DATABASE, List.of("h?")))).containsExactly("salaries");
        assertThatThrownBy(() -> lookup(config, "shard", "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }

    @Test
    void aLookupUserSeesWhatItHasPrivilegesOn()
    {
        ServiceConfig reader = reader();
        assertThat(lookup(reader, MongoDbProvider.DATABASE, "", Map.of())).containsExactly("sales");
        assertThat(lookup(reader, MongoDbProvider.COLLECTION, "", Map.of())).containsExactly("orders");
    }

    @Test
    void findsTheReplicaSetThroughAnyHostAndReportsWhenNoneAnswers()
    {
        ServiceConfig config = config(MongoDbSettings.URI, "mongodb://127.0.0.1:1," + node().address() + "/?replicaSet=" + MongoNode.REPLICA_SET);
        assertThat(lookup(config, MongoDbProvider.DATABASE, "h", Map.of())).containsExactly("hr");
        ServiceConfig down = config(MongoDbSettings.URI, "mongodb://127.0.0.1:1,127.0.0.1:2/?replicaSet=" + MongoNode.REPLICA_SET, MongoDbSettings.TIMEOUT, "1000");
        assertThatThrownBy(() -> lookup(down, MongoDbProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
        assertThat(provider.testConnection(down).status()).isEqualTo(ConnectionResult.Status.FAILED);
    }

    @Test
    void refusedSignInsAreReportedAsSuch()
    {
        ServiceConfig wrong = config(MongoDbSettings.PASSWORD, "wrong");
        assertThatThrownBy(() -> lookup(wrong, MongoDbProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        ServiceConfig unknown = config(MongoDbSettings.USER, "ghost", MongoDbSettings.PASSWORD, "x");
        assertThatThrownBy(() -> lookup(unknown, MongoDbProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED));
        assertThat(provider.testConnection(wrong).message()).startsWith("MongoDB refused the sign-in");
    }

    @Test
    void signsInWithTheOlderScram()
    {
        ServiceConfig legacy = config(MongoDbSettings.USER, "legacy", MongoDbSettings.PASSWORD, "legacy-secret", MongoDbSettings.MECHANISM, MongoDbSettings.SCRAM_SHA_1);
        assertThat(provider.testConnection(legacy).message()).contains("signed in as legacy@admin");
    }

    @Test
    void signsInWithAClientCertificateOverVerifiedTls()
    {
        for (String file : List.of("client-pkcs8.pem", "client-pkcs1.pem", "client-encrypted.pem")) {
            ServiceConfig config = new ServiceConfig("warehouse", Map.of(MongoDbSettings.URI, node().uri() + "&tls=true", MongoDbSettings.MECHANISM, MongoDbSettings.X509,
                    MongoDbSettings.CA_FILE, certificates().file("ca.pem"), MongoDbSettings.CERTIFICATE_KEY_FILE, certificates().file(file),
                    MongoDbSettings.CERTIFICATE_KEY_PASSWORD, TestCertificates.PASSPHRASE, MongoDbSettings.TIMEOUT, "10000"));
            ConnectionResult result = provider.testConnection(config);
            assertThat(result.status()).as(file + ": " + result.message()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
            assertThat(result.message()).contains("signed in as " + certificates().clientSubject() + "@$external");
        }
    }

    @Test
    void refusesAServerItsCaDoesNotVerify() throws Exception
    {
        TestCertificates other = new TestCertificates(Files.createDirectory(files.resolve("other")));
        ServiceConfig config = config(MongoDbSettings.URI, node().uri() + "&tls=true", MongoDbSettings.CA_FILE, other.file("ca.pem"), MongoDbSettings.TIMEOUT, "2000");
        assertThatThrownBy(() -> lookup(config, MongoDbProvider.DATABASE, "", Map.of())).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
    }
}
