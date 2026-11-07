// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb;

import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import org.devlive.grantforge.plugin.api.ConnectionResult;
import org.devlive.grantforge.plugin.api.LookupException;
import org.devlive.grantforge.plugin.api.LookupRequest;
import org.devlive.grantforge.plugin.api.ServiceConfig;
import org.devlive.grantforge.plugin.api.model.ResourceDefinition;
import org.devlive.grantforge.plugin.api.model.ServiceTypeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MongoDbProviderTest
{
    private static final ServiceConfig CONFIG = new ServiceConfig("warehouse", Map.of(MongoDbSettings.URI, "mongodb://db1", MongoDbSettings.USER, "lookup",
            MongoDbSettings.PASSWORD, "secret"));

    private static MongoDbProvider provider(MongoClient client)
    {
        return new MongoDbProvider(settings -> new MongoDbClient(settings)
        {
            @Override
            MongoClient connect()
            {
                return client;
            }
        });
    }

    private static Document signedIn(String user, String database)
    {
        return new Document("authInfo", new Document("authenticatedUsers", List.of(new Document("user", user).append("db", database))));
    }

    private static MongoClient client(Document hello, Document status, Map<String, List<String>> collections)
    {
        return FakeMongo.client(collections, Map.of("buildInfo", new Document("version", "8.0.34"), "hello", hello, "connectionStatus", status));
    }

    @Test
    void definesTheLevelsAndAccessTypesInMongoDbsTerms()
    {
        ServiceTypeDefinition definition = new MongoDbProvider().definition();
        assertThat(definition.name()).isEqualTo("mongodb");
        assertThat(definition.resources()).extracting(ResourceDefinition::name).containsExactly("deployment", "database", "collection");
        assertThat(definition.resources().get(2).accessTypes()).containsExactlyInAnyOrder("read", "change_stream", "insert", "update", "remove", "create_index", "drop_index", "alter",
                "bypass_validation", "drop");
        assertThat(definition.resources().get(1).accessTypes()).containsExactlyInAnyOrder("create", "drop_database");
        assertThat(definition.resources().get(0).accessTypes()).containsExactlyInAnyOrder("list_databases", "monitor");
        assertThat(definition.accessTypes()).hasSize(14);
        assertThat(definition.rowFilter()).isNull();
        assertThat(definition.dataMask()).isNull();
    }

    @Test
    void validatesItsSettings()
    {
        assertThat(new MongoDbProvider().validateConfig(CONFIG)).isEmpty();
        assertThat(new MongoDbProvider().validateConfig(new ServiceConfig("warehouse", Map.of()))).isNotEmpty();
    }

    @Test
    void theConnectionTestDescribesEachKindOfDeployment()
    {
        Map<String, List<String>> databases = Map.of("admin", List.of(), "sales", List.of(), "hr", List.of());
        assertThat(provider(client(new Document("setName", "rs0"), signedIn("lookup", "admin"), databases)).testConnection(CONFIG)).satisfies(result -> {
            assertThat(result.status()).isEqualTo(ConnectionResult.Status.SUCCEEDED);
            assertThat(result.message()).isEqualTo("MongoDB 8.0.34, replica set rs0, signed in as lookup@admin, 2 databases");
        });
        assertThat(provider(client(new Document("msg", "isdbgrid"), signedIn("lookup", "sales"), Map.of("sales", List.of()))).testConnection(CONFIG).message())
                .isEqualTo("MongoDB 8.0.34, a sharded cluster, signed in as lookup@sales, 1 database");
        assertThat(provider(client(new Document(), new Document(), Map.of())).testConnection(CONFIG).message())
                .isEqualTo("MongoDB 8.0.34, a single server, signed in as nobody, 0 databases");
        assertThat(provider(client(new Document(), new Document("authInfo", new Document("authenticatedUsers", List.of())), Map.of())).testConnection(CONFIG).message())
                .contains("signed in as nobody");
    }

    @Test
    void theConnectionTestReportsWhatGoesWrong()
    {
        assertThat(new MongoDbProvider().testConnection(new ServiceConfig("warehouse", Map.of()))).satisfies(result -> {
            assertThat(result.status()).isEqualTo(ConnectionResult.Status.FAILED);
            assertThat(result.message()).startsWith(MongoDbSettings.URI);
        });
        MongoDbProvider unreachable = new MongoDbProvider(settings -> new MongoDbClient(settings)
        {
            @Override
            MongoClient connect()
            {
                throw new MongoTimeoutException("no server");
            }
        });
        assertThat(unreachable.testConnection(CONFIG).message()).isEqualTo("no MongoDB server can be reached: no server");
        MongoDbProvider noTls = new MongoDbProvider(settings -> new MongoDbClient(settings)
        {
            @Override
            MongoClient connect()
            {
                throw new LookupException(LookupException.Reason.FAILED, "the TLS files cannot be used", new IllegalStateException());
            }
        });
        assertThat(noTls.testConnection(CONFIG).message()).isEqualTo("the TLS files cannot be used");
    }

    @Test
    void looksUpThroughTheClient()
    {
        MongoDbProvider provider = provider(client(new Document(), new Document(), Map.of("sales", List.of("orders"), "hr", List.of("salaries"))));
        assertThat(provider.lookup(new LookupRequest(CONFIG, "database", "", Map.of(), 10))).containsExactly("hr", "sales");
        assertThat(provider.lookup(new LookupRequest(CONFIG, "collection", "or", Map.of("database", List.of("sales")), 10))).containsExactly("orders");
    }

    @Test
    void aLookupOfWhatCannotBeAskedIsInvalidInput()
    {
        MongoDbProvider provider = new MongoDbProvider();
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(CONFIG, "shard", "", Map.of(), 10))).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
        assertThatThrownBy(() -> provider.lookup(new LookupRequest(new ServiceConfig("warehouse", Map.of()), "database", "", Map.of(), 10)))
                .isInstanceOfSatisfying(LookupException.class, failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.INVALID_INPUT));
    }
}
