// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.MongoCommandException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.devlive.grantforge.agent.Snapshot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The agent against a real replica set of one release, its user holding only what the guide tells to give it: reading
 * collections and whole databases, writing by kind, creating and dropping, a view read without its source, MongoDB roles
 * and GrantForge groups, the deployment as a whole, changes of policies and of the service, changes made by hand undone,
 * the identities of one user, the users it leaves alone, roles outside GrantForge read for what they give, the profiler
 * as access events, signing in with a client certificate, and the shaded jar run as a service.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 15, unit = TimeUnit.MINUTES)
abstract class MongoDbAgentContract
{
    private static final String AGENT = "grantforge";
    private static final String AGENT_PASSWORD = "agent-secret";
    private static final List<String> USERS = List.of("alice", "bob", "carol", "dave", "erin", "frank", "grace", "hank", "ivan", "judy", "etl", "boss");

    /** The agents' files. */
    @TempDir
    static Path files;

    private final List<LogRecord> logged = new CopyOnWriteArrayList<>();
    private final Handler capture = new Handler()
    {
        @Override
        public void publish(LogRecord record)
        {
            logged.add(record);
        }

        @Override
        public void flush()
        {
            // Nothing is buffered.
        }

        @Override
        public void close()
        {
            // Nothing to let go of.
        }
    };
    private final Logger logger = Logger.getLogger("org.devlive.grantforge.mongodb.agent");
    private @Nullable MongoNode node;
    private @Nullable MongoClient root;
    private @Nullable TestCertificates certificates;
    private @Nullable AgentServer server;
    private @Nullable MongoDbAgent agent;
    private long published = 1;

    /**
     * The release under test.
     *
     * @return such as {@code 8.0.34}
     */
    abstract String version();

    @BeforeAll
    void start() throws Exception
    {
        certificates = new TestCertificates(Files.createDirectory(files.resolve("certificates")));
        MongoNode started = new MongoNode(version(), certificates);
        node = started;
        StringBuilder users = new StringBuilder();
        for (String user : USERS) {
            users.append("admin.createUser({user: '").append(user).append("', pwd: '").append(user).append("-secret', roles: []});\n");
        }
        started.run("""
                const admin = db.getSiblingDB('admin');
                const sales = db.getSiblingDB('sales');
                sales.orders.insertMany([{_id: 1, region: 'EU', card: '4111'}, {_id: 2, region: 'US', card: '5500'}]);
                sales.items.insertOne({_id: 1});
                sales.secret.insertOne({_id: 1});
                sales.createView('orders_public', 'orders', [{$project: {card: 0}}]);
                db.getSiblingDB('hr').salaries.insertOne({_id: 1});
                db.getSiblingDB('team_a').seed.insertOne({_id: 1});
                db.getSiblingDB('team_b').seed.insertOne({_id: 1});
                admin.createRole({role: 'inventory', privileges: [{resource: {db: '', collection: ''}, actions: ['listCollections']}], roles: []});
                admin.createUser({user: '%s', pwd: '%s', roles: ['userAdminAnyDatabase', 'clusterMonitor', 'inventory']});
                admin.createRole({role: 'analysts', privileges: [], roles: []});
                %s
                admin.grantRolesToUser('boss', ['root']);
                admin.grantRolesToUser('carol', ['analysts']);
                admin.grantRolesToUser('dave', [{role: 'readWrite', db: 'hr'}]);
                admin.grantRolesToUser('hank', [{role: 'userAdmin', db: 'hr'}]);
                sales.createUser({user: 'alice', pwd: 'alice-other', roles: []});
                db.getSiblingDB('$external').createUser({user: '%s', roles: [{role: 'userAdminAnyDatabase', db: 'admin'}, {role: 'clusterMonitor', db: 'admin'}, {role: 'inventory', db: 'admin'}]});
                """.formatted(AGENT, AGENT_PASSWORD, users, certificates.clientSubject()));
        root = started.client("root", MongoNode.ROOT_PASSWORD, "admin");
        logger.addHandler(capture);
        server = new AgentServer(new MongoSnapshots());
        agent = MongoDbAgent.start(AgentConfig.parse(settings("main", "grantforge_", "mongodb.user", AGENT, "mongodb.password.file",
                file("agent.password", AGENT_PASSWORD)), "test"));
        awaitPolicies();
    }

    @AfterAll
    void stop()
    {
        logger.removeHandler(capture);
        if (agent != null) {
            agent.close();
        }
        if (server != null) {
            server.close();
        }
        if (root != null) {
            root.close();
        }
        if (node != null) {
            node.close();
        }
    }

    private MongoNode node()
    {
        return requireNonNull(node, "node");
    }

    private MongoClient root()
    {
        return requireNonNull(root, "root");
    }

    private AgentServer server()
    {
        return requireNonNull(server, "server");
    }

    private MongoDbAgent agent()
    {
        return requireNonNull(agent, "agent");
    }

    private static String file(String name, String content) throws IOException
    {
        Path path = files.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path.toString();
    }

    /** The settings of an agent of the test server, with timers too slow to run on their own but the first sync. */
    private Map<String, String> settings(String instance, String prefix, String... more) throws IOException
    {
        Map<String, String> values = new HashMap<>(Map.of("grantforge.server.url", "http://127.0.0.1:" + server().port(),
                "grantforge.token.file", file(instance + ".token", AgentServer.TOKEN), "grantforge.instance", "mongodb-" + instance,
                "grantforge.cache.dir", files.resolve("cache-" + instance).toString(), "grantforge.refresh.interval.ms", "1000",
                "grantforge.audit.flush.interval.ms", "200", "grantforge.mongodb.uri", node().uri(), "grantforge.mongodb.role.prefix", prefix));
        values.putAll(Map.of("grantforge.mongodb.unmanaged.users", "etl", "grantforge.sync.interval.ms", "3600000",
                "grantforge.audit.log.delay.ms", "0", "grantforge.audit.log.interval.ms", "600000", "grantforge.audit.fold.window.ms", "0"));
        for (int index = 0; index < more.length; index += 2) {
            values.put("grantforge." + more[index], more[index + 1]);
        }
        return values;
    }

    /** Publishes policies, waits until the agent has them and syncs. */
    private Reconciler.Outcome publish(MongoSnapshots policies) throws InterruptedException
    {
        server().publish(policies);
        published++;
        awaitPolicies();
        return requireNonNull(agent().syncNow(), "outcome");
    }

    private void awaitPolicies() throws InterruptedException
    {
        eventually("the agent takes policy version " + published, () -> {
            Snapshot snapshot = agent().agent().snapshot();
            return snapshot != null && snapshot.policyVersion() == published;
        });
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

    /** Whether MongoDB lets a user do something: a refusal for lack of a privilege is no; any other failure fails the test. */
    private boolean can(String user, Consumer<MongoClient> work)
    {
        try (MongoClient client = node().client(user, user + "-secret", "admin")) {
            work.accept(client);
            return true;
        }
        catch (MongoCommandException refused) {
            if (refused.getErrorCode() == 13) {
                return false;
            }
            throw refused;
        }
    }

    private static Consumer<MongoClient> reading(String database, String collection)
    {
        return client -> client.getDatabase(database).getCollection(collection).find().first();
    }

    private static MongoCollection<Document> collection(MongoClient client, String database, String name)
    {
        return client.getDatabase(database).getCollection(name);
    }

    /** What a role of the agent holds, as {@code database.collection: actions}; empty for a role that does not exist. */
    private List<String> privileges(String role)
    {
        Document answer = root().getDatabase("admin").runCommand(new Document("rolesInfo", role).append("showPrivileges", true));
        List<String> privileges = new ArrayList<>();
        for (Document found : answer.getList("roles", Document.class)) {
            for (Document privilege : found.getList("privileges", Document.class)) {
                Document resource = privilege.get("resource", Document.class);
                String on = Boolean.TRUE.equals(resource.getBoolean("cluster")) ? "cluster" : resource.getString("db") + "." + resource.getString("collection");
                privileges.add(on + ": " + String.join(",", new TreeSet<>(privilege.getList("actions", String.class))));
            }
        }
        return privileges;
    }

    private boolean roleExists(String role)
    {
        return !root().getDatabase("admin").runCommand(new Document("rolesInfo", role)).getList("roles", Document.class).isEmpty();
    }

    private List<String> rolesOf(String user, String database)
    {
        Document found = root().getDatabase(database).runCommand(new Document("usersInfo", user)).getList("users", Document.class).get(0);
        return found.getList("roles", Document.class).stream().map(role -> role.getString("role")).toList();
    }

    private boolean warned(String text)
    {
        return logged.stream().anyMatch(record -> String.valueOf(record.getMessage()).contains(text));
    }

    private static String[] on(String database, String collection)
    {
        return new String[] {"database", database, "collection", collection};
    }

    @Test
    void grantsReadingOnWholeDatabasesAndByCollection() throws Exception
    {
        publish(new MongoSnapshots().allow(List.of("alice"), List.of("read"), on("sales", "*"))
                .allow(List.of("bob"), List.of("read"), on("sales", "orders"))
                .allow(List.of("carol"), List.of("read"), on("sales", "*")).deny(List.of("carol"), List.of("read"), on("sales", "secret")));
        assertThat(privileges("grantforge_alice")).containsExactly("sales.: collStats,dbStats,find,killCursors,listCollections,listIndexes");
        assertThat(can("alice", reading("sales", "orders"))).isTrue();
        assertThat(can("alice", reading("hr", "salaries"))).isFalse();
        assertThat(can("alice", client -> collection(client, "sales", "orders").insertOne(new Document("_id", 100)))).isFalse();

        assertThat(privileges("grantforge_bob")).containsExactly("sales.orders: collStats,find,killCursors,listIndexes");
        assertThat(can("bob", reading("sales", "orders"))).isTrue();
        assertThat(can("bob", reading("sales", "items"))).isFalse();
        assertThat(can("bob", client -> client.getDatabase("sales").runCommand(new Document("listCollections", 1)))).as("names of what it may not read stay hidden").isFalse();
        try (MongoClient client = node().client("bob", "bob-secret", "admin")) {
            assertThat(client.getDatabase("sales").listCollectionNames().authorizedCollections(true).into(new ArrayList<>())).containsExactly("orders");
        }

        assertThat(can("carol", reading("sales", "orders"))).isTrue();
        assertThat(can("carol", reading("sales", "secret"))).isFalse();

        collection(root(), "sales", "later").insertOne(new Document("_id", 1));
        assertThat(can("alice", reading("sales", "later"))).as("a collection created later has it at once").isTrue();
        assertThat(can("carol", reading("sales", "later"))).as("a policy decides below, so it waits for the next sync").isFalse();
        agent().syncNow();
        assertThat(can("carol", reading("sales", "later"))).isTrue();
        collection(root(), "sales", "later").drop();
    }

    @Test
    void grantsWritingByKindIndexesOptionsAndDropping() throws Exception
    {
        publish(new MongoSnapshots().allow(List.of("dave"), List.of("insert"), on("team_a", "seed"))
                .allow(List.of("erin"), List.of("read", "update", "remove", "create_index", "drop_index", "alter", "bypass_validation", "change_stream"), on("team_a", "seed")));
        assertThat(can("dave", client -> collection(client, "team_a", "seed").insertOne(new Document()))).isTrue();
        assertThat(can("dave", client -> collection(client, "team_a", "seed").deleteMany(new Document()))).isFalse();
        assertThat(can("dave", client -> collection(client, "team_a", "seed").updateMany(new Document(), new Document("$set", new Document("a", 1))))).isFalse();
        assertThat(can("erin", client -> collection(client, "team_a", "seed").updateMany(new Document(), new Document("$set", new Document("a", 1))))).isTrue();
        assertThat(can("erin", client -> collection(client, "team_a", "seed").createIndex(Indexes.ascending("a")))).isTrue();
        assertThat(can("erin", client -> collection(client, "team_a", "seed").dropIndex(Indexes.ascending("a")))).isTrue();
        assertThat(can("erin", client -> client.getDatabase("team_a").runCommand(new Document("collMod", "seed")))).isTrue();
        assertThat(can("erin", client -> client.getDatabase("team_a").runCommand(new Document("validate", "seed")))).isTrue();
        assertThat(can("erin", client -> collection(client, "team_a", "seed").watch().maxAwaitTime(1, TimeUnit.MILLISECONDS).cursor().close())).isTrue();
        assertThat(can("erin", client -> collection(client, "team_a", "seed").drop())).isFalse();
        assertThat(can("erin", client -> collection(client, "team_a", "seed").deleteMany(new Document("a", 2)))).isTrue();
    }

    @Test
    void createsCollectionsAndDropsADatabaseOnlyWhereThatGivesNoMore() throws Exception
    {
        db("team_c");
        db("team_d");
        publish(new MongoSnapshots().allow(List.of("frank"), List.of("create"), "database", "team_c")
                .allow(List.of("frank"), List.of("drop"), on("team_c", "*"))
                .allow(List.of("frank"), List.of("drop_database"), "database", "team_c")
                .allow(List.of("grace"), List.of("drop_database"), "database", "team_d")
                .allow(List.of("grace"), List.of("drop"), on("team_d", "seed")));
        assertThat(can("frank", client -> client.getDatabase("team_c").createCollection("made"))).isTrue();
        assertThat(can("frank", client -> collection(client, "team_c", "made").drop())).isTrue();
        assertThat(can("frank", client -> client.getDatabase("team_c").drop())).isTrue();
        assertThat(can("grace", client -> client.getDatabase("team_d").drop())).as("a policy allows dropping only some of its collections").isFalse();
        assertThat(warned("dropping database team_d for user grace")).isTrue();
        assertThat(can("grace", client -> collection(client, "team_d", "seed").drop())).isTrue();
    }

    private void db(String name)
    {
        collection(root(), name, "seed").insertOne(new Document("_id", 1));
    }

    @Test
    void aViewIsReadWithoutItsSource() throws Exception
    {
        publish(new MongoSnapshots().allow(List.of("ivan"), List.of("read"), on("sales", "orders_public")));
        assertThat(can("ivan", reading("sales", "orders"))).isFalse();
        try (MongoClient client = node().client("ivan", "ivan-secret", "admin")) {
            Document first = collection(client, "sales", "orders_public").find().first();
            assertThat(first).containsKey("region").doesNotContainKey("card");
        }
    }

    @Test
    void mongoDbRolesAndGrantForgeGroupsAreGroups() throws Exception
    {
        publish(new MongoSnapshots().group("team", "hank").allow(List.of("@analysts"), List.of("read"), on("sales", "items"))
                .allow(List.of("@team"), List.of("read"), on("sales", "orders")));
        assertThat(can("carol", reading("sales", "items"))).as("carol holds the MongoDB role analysts").isTrue();
        assertThat(can("bob", reading("sales", "items"))).isFalse();
        assertThat(can("hank", reading("sales", "orders"))).as("GrantForge puts hank in the group").isTrue();
    }

    @Test
    void grantsWhatDecidesTheDeploymentAsAWhole() throws Exception
    {
        publish(new MongoSnapshots().allow(List.of("judy"), List.of("monitor", "list_databases"), "deployment", "*"));
        assertThat(privileges("grantforge_judy")).singleElement().asString().startsWith("cluster: ").contains("listDatabases", "serverStatus");
        assertThat(can("judy", client -> client.getDatabase("admin").runCommand(new Document("serverStatus", 1)))).isTrue();
        try (MongoClient client = node().client("judy", "judy-secret", "admin")) {
            assertThat(client.listDatabaseNames().into(new ArrayList<>())).contains("sales", "hr");
        }
        publish(new MongoSnapshots().allow(List.of("judy"), List.of("monitor"), "deployment", "other-*"));
        assertThat(roleExists("grantforge_judy")).as("the policy names another deployment").isFalse();
    }

    @Test
    void policyAndServiceChangesTakeEffectAndRolesGo() throws Exception
    {
        publish(new MongoSnapshots().allow(List.of("alice"), List.of("read"), on("sales", "orders")));
        assertThat(can("alice", reading("sales", "orders"))).isTrue();
        Reconciler.Outcome narrowed = publish(new MongoSnapshots().allow(List.of("alice"), List.of("read"), on("sales", "items")));
        assertThat(narrowed.updated()).as("the role's privileges are replaced in one step").isEqualTo(1);
        assertThat(narrowed.dropped()).isGreaterThanOrEqualTo(0);
        assertThat(can("alice", reading("sales", "orders"))).isFalse();
        assertThat(can("alice", reading("sales", "items"))).isTrue();

        publish(new MongoSnapshots().allow(List.of("alice"), List.of("read"), on("sales", "items")).disabled());
        assertThat(roleExists("grantforge_alice")).as("a service not in use grants nothing").isFalse();
        assertThat(can("alice", reading("sales", "items"))).isFalse();

        publish(new MongoSnapshots().allow(List.of("alice"), List.of("read"), on("sales", "items")).serviceType("hive"));
        assertThat(roleExists("grantforge_alice")).as("nor does a service of another type").isFalse();

        publish(new MongoSnapshots().allow(List.of("alice"), List.of("read"), on("sales", "items")));
        assertThat(can("alice", reading("sales", "items"))).isTrue();
        assertThat(requireNonNull(agent().syncNow(), "outcome").changed()).as("nothing left to change").isFalse();
    }

    @Test
    void undoesChangesMadeByHand() throws Exception
    {
        publish(new MongoSnapshots().allow(List.of("alice"), List.of("read"), on("sales", "items")));
        MongoDatabase admin = root().getDatabase("admin");
        admin.runCommand(new Document("grantPrivilegesToRole", "grantforge_alice").append("privileges",
                List.of(new Document("resource", new Document("db", "hr").append("collection", "salaries")).append("actions", List.of("find")))));
        assertThat(can("alice", reading("hr", "salaries"))).isTrue();
        assertThat(requireNonNull(agent().syncNow(), "outcome").updated()).isEqualTo(1);
        assertThat(can("alice", reading("hr", "salaries"))).isFalse();

        admin.runCommand(new Document("grantRolesToRole", "grantforge_alice").append("roles", List.of(new Document("role", "readAnyDatabase").append("db", "admin"))));
        assertThat(can("alice", reading("hr", "salaries"))).isTrue();
        agent().syncNow();
        assertThat(can("alice", reading("hr", "salaries"))).as("a role it was made to inherit goes too").isFalse();

        admin.runCommand(new Document("revokeRolesFromUser", "alice").append("roles", List.of(new Document("role", "grantforge_alice").append("db", "admin"))));
        assertThat(can("alice", reading("sales", "items"))).isFalse();
        assertThat(requireNonNull(agent().syncNow(), "outcome").grants()).isEqualTo(1);
        assertThat(can("alice", reading("sales", "items"))).isTrue();

        admin.runCommand(new Document("grantRolesToUser", "bob").append("roles", List.of(new Document("role", "grantforge_alice").append("db", "admin"))));
        assertThat(can("bob", reading("sales", "items"))).isTrue();
        agent().syncNow();
        assertThat(can("bob", reading("sales", "items"))).as("alice's role is alice's alone").isFalse();

        admin.runCommand(new Document("createRole", "grantforge_nobody").append("privileges", List.of()).append("roles", List.of()));
        agent().syncNow();
        assertThat(roleExists("grantforge_nobody")).as("a role nobody needs goes").isFalse();
    }

    @Test
    void grantsTheRoleToEveryIdentityOfAUser() throws Exception
    {
        publish(new MongoSnapshots().allow(List.of("alice"), List.of("read"), on("sales", "items")));
        assertThat(rolesOf("alice", "admin")).contains("grantforge_alice");
        assertThat(rolesOf("alice", "sales")).contains("grantforge_alice");
        try (MongoClient client = node().client("alice", "alice-other", "sales")) {
            assertThat(collection(client, "sales", "items").find().first()).isNotNull();
        }
    }

    @Test
    void leavesItselfSuperusersAndUnmanagedUsersAlone() throws Exception
    {
        publish(new MongoSnapshots().allow(List.of("etl", "boss", AGENT, "root"), List.of("read"), on("sales", "items")));
        assertThat(roleExists("grantforge_etl")).isFalse();
        assertThat(roleExists("grantforge_boss")).isFalse();
        assertThat(roleExists("grantforge_" + AGENT)).isFalse();
        assertThat(roleExists("grantforge_root")).isFalse();
        assertThat(warned("MongoDB user boss has the role root@admin, which no role binds")).isTrue();
    }

    @Test
    void readsRolesOutsideGrantForgeForWhatTheyGive() throws Exception
    {
        MongoSnapshots policies = new MongoSnapshots();
        long denial = policies.nextId();
        publish(policies.deny(List.of("dave"), List.of("read"), on("hr", "salaries")));
        assertThat(warned("MongoDB role readWrite@hr, which user dave has outside GrantForge, gives read on hr.salaries, which GrantForge policy " + denial + " denies")).isTrue();
        assertThat(can("dave", reading("hr", "salaries"))).as("what the agent cannot take away, it tells").isTrue();
        assertThat(warned("MongoDB user hank has the role userAdmin@hr outside GrantForge, which lets it administer users")).isTrue();
    }

    @Test
    void reportsAccessesFromTheProfilerAndRolesOutsideGrantForge() throws Exception
    {
        root().getDatabase("sales").runCommand(new Document("profile", 2));
        root().getDatabase("hr").runCommand(new Document("profile", 2));
        publish(new MongoSnapshots().allow(List.of("alice"), List.of("read"), on("sales", "orders")).allow(List.of("alice"), List.of("read"), on("sales", "items"))
                .allow(List.of("bob"), List.of("insert"), on("sales", "items")));
        agent().readAccessesNow();
        Thread.sleep(50);
        try (MongoClient client = node().client("alice", "alice-secret", "admin")) {
            collection(client, "sales", "orders").aggregate(List.of(new Document("$lookup", new Document("from", "items").append("localField", "_id").append("foreignField", "_id")
                    .append("as", "items")))).first();
        }
        try (MongoClient client = node().client("bob", "bob-secret", "admin")) {
            collection(client, "sales", "items").insertOne(new Document("card", "4111-do-not-report"));
        }
        assertThat(can("dave", reading("hr", "salaries"))).isTrue();
        eventually("the profiler is read", () -> agent().readAccessesNow() >= 0 && server().events().stream().map(event -> event.path("user").asText()).distinct()
                .filter(List.of("alice", "bob", "dave")::contains).count() == 3);

        eventually("the source and the joined collection are both reported", () -> server().events().stream().filter(event -> "alice".equals(event.path("user").asText()))
                .map(event -> event.path("resource").asText()).distinct().count() >= 2);
        List<JsonNode> alice = server().events().stream().filter(event -> "alice".equals(event.path("user").asText())).toList();
        assertThat(alice).extracting(event -> event.path("resource").asText()).contains("sales.orders", "sales.items");
        assertThat(alice.get(0).path("accessType").asText()).isEqualTo("read");
        assertThat(alice.get(0).path("outcome").asText()).isEqualTo("ALLOWED");
        assertThat(alice.get(0).path("enforcer").asText()).isEqualTo("GRANTFORGE");
        assertThat(alice.get(0).path("action").asText()).isEqualTo("aggregate");
        assertThat(alice.get(0).path("request").asText()).isEqualTo("aggregate sales.orders");
        assertThat(alice.get(0).path("clientIp").asText()).isNotEmpty();

        JsonNode bob = server().events().stream().filter(event -> "bob".equals(event.path("user").asText())).findFirst().orElseThrow();
        assertThat(bob.path("accessType").asText()).isEqualTo("insert");
        assertThat(bob.toString()).as("no document reaches GrantForge").doesNotContain("4111-do-not-report");

        JsonNode dave = server().events().stream().filter(event -> "dave".equals(event.path("user").asText())).findFirst().orElseThrow();
        assertThat(dave.path("enforcer").asText()).as("a role outside GrantForge allowed it").isEqualTo("NATIVE");
        assertThat(server().events()).noneMatch(event -> AGENT.equals(event.path("user").asText()));
        root().getDatabase("sales").runCommand(new Document("profile", 0));
        root().getDatabase("hr").runCommand(new Document("profile", 0));
    }

    @Test
    void signsInWithAClientCertificate() throws Exception
    {
        publish(new MongoSnapshots().allow(List.of("erin"), List.of("read"), on("sales", "items")));
        TestCertificates made = requireNonNull(certificates, "certificates");
        Map<String, String> values = settings("x509", "gfx_", "mongodb.uri", node().uri() + "&tls=true", "mongodb.auth.mechanism", "MONGODB-X509", "mongodb.tls.ca.file",
                made.file("ca.pem"), "mongodb.tls.certificate.key.file", made.file("client-pkcs8.pem"));
        try (MongoDbAgent second = MongoDbAgent.start(AgentConfig.parse(values, "test"))) {
            eventually("the second agent keeps a role for erin", () -> second.agent().snapshot() != null && second.syncNow() != null && roleExists("gfx_erin"));
            assertThat(rolesOf("erin", "admin")).contains("gfx_erin", "grantforge_erin");
        }
        root().getDatabase("admin").runCommand(new Document("dropRole", "gfx_erin"));
    }

    @Test
    void theShadedJarRunsAsAService() throws Exception
    {
        publish(new MongoSnapshots().allow(List.of("erin"), List.of("read"), on("sales", "*")));
        Map<String, String> values = settings("jar", "gfjar_", "mongodb.user", AGENT, "mongodb.password.file", file("jar.password", AGENT_PASSWORD), "sync.interval.ms", "1000");
        StringBuilder properties = new StringBuilder();
        values.forEach((key, value) -> properties.append(key).append('=').append(value.replace("\\", "\\\\")).append('\n'));
        String settingsFile = file("jar.properties", properties.toString());
        Path jar = Path.of(requireNonNull(System.getProperty("grantforge.mongodb.agent.jar"), "grantforge.mongodb.agent.jar"));
        Path output = files.resolve("jar.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-jar", jar.toString(), settingsFile)
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            eventually("the jar keeps a role for erin", () -> roleExists("gfjar_erin"));
        }
        catch (AssertionError late) {
            throw new AssertionError(late.getMessage() + "\n" + Files.readString(output), late);
        }
        finally {
            process.destroy();
            process.waitFor(30, TimeUnit.SECONDS);
            root().getDatabase("admin").runCommand(new Document("dropRole", "gfjar_erin"));
        }
        assertThat(Files.readString(output)).contains("keeps MongoDB " + version());
    }
}
