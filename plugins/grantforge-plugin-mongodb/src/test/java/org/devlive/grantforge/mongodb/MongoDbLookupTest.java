// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.MongoSecurityException;
import com.mongodb.MongoSocketOpenException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.Document;
import org.devlive.grantforge.plugin.api.LookupException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MongoDbLookupTest
{
    private static final MongoDbSettings SETTINGS = new MongoDbSettings("mongodb://db1", "lookup", "secret", MongoDbSettings.SCRAM_SHA_256, "admin", null, null, null, null, null,
            "mongodb", 1000);

    private static MongoDbClient connected(MongoClient client)
    {
        return new MongoDbClient(SETTINGS)
        {
            @Override
            MongoClient connect()
            {
                return client;
            }
        };
    }

    private static MongoCommandException refused(int code, String message)
    {
        return new MongoCommandException(new BsonDocument("ok", new BsonInt32(0)).append("code", new BsonInt32(code)).append("errmsg", new BsonString(message)),
                new ServerAddress("db1"));
    }

    private final MongoClient client = FakeMongo.client(Map.of("admin", List.of("system.users"), "local", List.of("oplog.rs"), "config", List.of("chunks"), "sales",
            List.of("orders", "order_items", "system.views", "system.profile"), "sales_2026", List.of("orders"), "hr", List.of("salaries")),
            Map.of("hello", new Document("setName", "rs0")));

    private List<String> names(String resource, String typed, Map<String, List<String>> context, int limit)
    {
        return MongoDbLookup.of(resource, typed, context, limit).run(connected(client));
    }

    @Test
    void refusesALevelTheServiceTypeDoesNotHave()
    {
        assertThatIllegalArgumentException().isThrownBy(() -> MongoDbLookup.of("shard", "", Map.of(), 10)).withMessageContaining("shard");
    }

    @Test
    void stripsWhatIsGiven()
    {
        MongoDbLookup lookup = MongoDbLookup.of(MongoDbProvider.COLLECTION, " or ", Map.of(MongoDbProvider.DATABASE, List.of(" sales ", " ")), 0);
        assertThat(lookup.typed()).isEqualTo("or");
        assertThat(lookup.databases()).containsExactly("sales");
        assertThat(lookup.limit()).isEqualTo(1);
    }

    @Test
    void namesTheDeployment()
    {
        assertThat(names(MongoDbProvider.DEPLOYMENT, "", Map.of(), 10)).containsExactly("rs0");
        assertThat(MongoDbLookup.deployment(new Document("msg", "isdbgrid"))).isEqualTo(MongoDbProvider.SHARDED);
        assertThat(MongoDbLookup.deployment(new Document())).isEqualTo(MongoDbProvider.STANDALONE);
    }

    @Test
    void listsDatabasesWithoutMongoDbsOwnWithinWhatWasTyped()
    {
        assertThat(names(MongoDbProvider.DATABASE, "", Map.of(), 10)).containsExactly("hr", "sales", "sales_2026");
        assertThat(names(MongoDbProvider.DATABASE, "sa", Map.of(), 1)).containsExactly("sales");
        assertThat(names(MongoDbProvider.DATABASE, "Sa", Map.of(), 10)).as("names compare with case").isEmpty();
    }

    @Test
    void listsCollectionsOfTheDatabasesThePolicyNamesWithoutSystemCollections()
    {
        assertThat(names(MongoDbProvider.COLLECTION, "", Map.of(MongoDbProvider.DATABASE, List.of("sales")), 10)).containsExactly("order_items", "orders");
        assertThat(names(MongoDbProvider.COLLECTION, "", Map.of(MongoDbProvider.DATABASE, List.of("sales_*", "h?")), 10)).containsExactly("orders", "salaries");
        assertThat(names(MongoDbProvider.COLLECTION, "sal", Map.of(), 10)).containsExactly("salaries");
    }

    @Test
    void looksInAtMostSoManyDatabases()
    {
        List<String> many = IntStream.range(0, MongoDbLookup.MAX_PARENTS + 5).mapToObj(index -> "db" + index).toList();
        assertThat(MongoDbLookup.matching(many, List.of())).hasSize(MongoDbLookup.MAX_PARENTS);
        assertThat(MongoDbLookup.matching(many, List.of("db*"))).hasSize(MongoDbLookup.MAX_PARENTS);
        assertThat(MongoDbLookup.matching(many, List.of("db1"))).containsExactly("db1");
    }

    @Test
    void wildcardsTakeEverythingElseLiterally()
    {
        assertThat(MongoDbLookup.pattern("a.b*").matcher("a.bc").matches()).isTrue();
        assertThat(MongoDbLookup.pattern("a.b*").matcher("axbc").matches()).isFalse();
        assertThat(MongoDbLookup.pattern("a?c").matcher("abc").matches()).isTrue();
        assertThat(MongoDbLookup.pattern("*").matcher("").matches()).isTrue();
    }

    @Test
    void aFailingDeploymentFailsTheLookupWithItsReason()
    {
        MongoDbClient failing = new MongoDbClient(SETTINGS)
        {
            @Override
            MongoClient connect()
            {
                throw new MongoTimeoutException("no server");
            }
        };
        assertThatThrownBy(() -> MongoDbLookup.of(MongoDbProvider.DATABASE, "", Map.of(), 1).run(failing)).isInstanceOfSatisfying(LookupException.class,
                failed -> assertThat(failed.getReason()).isEqualTo(LookupException.Reason.UNREACHABLE));
    }

    @Test
    void tellsRefusedSignInsFromMissingPrivilegesAndUnreachableServers()
    {
        MongoSecurityException wrapped = new MongoSecurityException(MongoCredential.createScramSha256Credential("lookup", "admin", new char[] {'x'}), "Exception authenticating",
                refused(18, "Authentication failed."));
        assertThat(MongoDbLookup.failure(wrapped)).satisfies(failure -> {
            assertThat(failure.getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
            assertThat(failure.getMessage()).isEqualTo("MongoDB refused the sign-in: Authentication failed.");
        });
        MongoSecurityException plain = new MongoSecurityException(MongoCredential.createScramSha256Credential("lookup", "admin", new char[] {'x'}), "no such mechanism");
        assertThat(MongoDbLookup.failure(plain).getMessage()).isEqualTo("MongoDB refused the sign-in: no such mechanism");
        assertThat(MongoDbLookup.failure(refused(18, "Authentication failed.")).getReason()).isEqualTo(LookupException.Reason.AUTHENTICATION_FAILED);
        assertThat(MongoDbLookup.failure(refused(13, "not authorized on sales to execute command { find: \"x\" }"))).satisfies(failure -> {
            assertThat(failure.getReason()).isEqualTo(LookupException.Reason.ACCESS_DENIED);
            assertThat(failure.getMessage()).isEqualTo("MongoDB refused the lookup user: not authorized on sales");
        });
        assertThat(MongoDbLookup.failure(new MongoTimeoutException("no server")).getReason()).isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(MongoDbLookup.failure(new MongoSocketOpenException("closed", new ServerAddress("db1"), new IOException("closed"))).getReason())
                .isEqualTo(LookupException.Reason.UNREACHABLE);
        assertThat(MongoDbLookup.failure(refused(2, "bad value"))).satisfies(failure -> {
            assertThat(failure.getReason()).isEqualTo(LookupException.Reason.FAILED);
            assertThat(failure.getMessage()).isEqualTo("MongoDB failed the lookup: bad value");
        });
        assertThat(MongoDbLookup.failure(new MongoException("odd")).getMessage()).isEqualTo("MongoDB failed the lookup: odd");
    }
}
