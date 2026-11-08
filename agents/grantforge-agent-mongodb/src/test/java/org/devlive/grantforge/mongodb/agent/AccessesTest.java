// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessesTest
{
    private static Accesses.Access on(String type, String database, String collection)
    {
        return new Accesses.Access(type, database, collection);
    }

    private static List<Accesses.Access> of(String command, String namespace)
    {
        return Accesses.of(command, namespace, new Document());
    }

    @Test
    void namesTheAccessTypeOfEachCommand()
    {
        for (String read : List.of("find", "aggregate", "count", "distinct", "listIndexes")) {
            assertThat(of(read, "sales.orders")).containsExactly(on(Actions.READ, "sales", "orders"));
        }
        assertThat(of("insert", "sales.orders")).containsExactly(on(Actions.INSERT, "sales", "orders"));
        assertThat(of("update", "sales.orders")).containsExactly(on(Actions.UPDATE, "sales", "orders"));
        assertThat(of("delete", "sales.orders")).containsExactly(on(Actions.REMOVE, "sales", "orders"));
        assertThat(of("createIndexes", "sales.orders")).containsExactly(on(Actions.CREATE_INDEX, "sales", "orders"));
        assertThat(of("dropIndexes", "sales.orders")).containsExactly(on(Actions.DROP_INDEX, "sales", "orders"));
        assertThat(of("collMod", "sales.orders")).containsExactly(on(Actions.ALTER, "sales", "orders"));
        assertThat(of("drop", "sales.orders")).containsExactly(on(Actions.DROP, "sales", "orders"));
    }

    @Test
    void tellsRemovingFromUpdatingInFindAndModify()
    {
        assertThat(Accesses.of("findAndModify", "sales.orders", new Document("remove", true))).containsExactly(on(Actions.REMOVE, "sales", "orders"));
        assertThat(Accesses.of("findandmodify", "sales.orders", new Document("update", new Document()))).containsExactly(on(Actions.UPDATE, "sales", "orders"));
    }

    @Test
    void namesDatabasesAndTheDeployment()
    {
        assertThat(of("create", "sales.orders")).containsExactly(new Accesses.Access(Actions.CREATE, "sales", null));
        assertThat(of("dropDatabase", "sales")).containsExactly(new Accesses.Access(Actions.DROP_DATABASE, "sales", null));
        assertThat(of("listDatabases", "admin")).containsExactly(new Accesses.Access(Actions.LIST_DATABASES, null, null));
        assertThat(of("serverStatus", "admin")).containsExactly(new Accesses.Access(Actions.MONITOR, null, null));
    }

    @Test
    void leavesOutWhatNoPolicyDecides()
    {
        assertThat(of("hello", "admin")).isEmpty();
        assertThat(of("find", "config.transactions")).isEmpty();
        assertThat(of("find", "admin.system.users")).isEmpty();
        assertThat(of("find", "sales.system.profile")).isEmpty();
        assertThat(of("aggregate", "sales.$cmd.aggregate")).isEmpty();
        assertThat(of("find", "sales")).isEmpty();
        assertThat(of("create", "admin.orders")).isEmpty();
    }

    @Test
    void followsAPipelineToTheCollectionsItNames()
    {
        Document arguments = new Document("pipeline", List.of(new Document("$lookup", new Document("from", "items").append("pipeline",
                        List.of(new Document("$unionWith", new Document("coll", "archive").append("pipeline", List.of(new Document("$graphLookup", new Document("from", "tree")))))))),
                new Document("$unionWith", "old"), new Document("$facet", new Document("one", List.of(new Document("$lookup", new Document("from", "faceted"))))),
                new Document("$match", new Document("a", 1)), new Document("$out", "report")));
        assertThat(Accesses.of("aggregate", "sales.orders", arguments)).containsExactly(on(Actions.READ, "sales", "orders"), on(Actions.READ, "sales", "items"),
                on(Actions.READ, "sales", "archive"), on(Actions.READ, "sales", "tree"), on(Actions.READ, "sales", "old"), on(Actions.READ, "sales", "faceted"),
                on(Actions.INSERT, "sales", "report"));
    }

    @Test
    void readsTargetsInOtherDatabases()
    {
        Document arguments = new Document("pipeline", List.of(new Document("$merge", new Document("into", new Document("db", "reports").append("coll", "daily"))),
                new Document("$out", new Document("db", "reports").append("coll", "weekly")), new Document("$out", new Document("db", "reports")),
                new Document("$merge", new Document("into", "here"))));
        assertThat(Accesses.of("aggregate", "sales.orders", arguments)).containsExactly(on(Actions.READ, "sales", "orders"), on(Actions.INSERT, "reports", "daily"),
                on(Actions.INSERT, "reports", "weekly"), on(Actions.INSERT, "sales", "here"));
        assertThat(Accesses.of("aggregate", "sales.orders", new Document("pipeline", "odd"))).containsExactly(on(Actions.READ, "sales", "orders"));
        assertThat(Accesses.of("aggregate", "sales.orders", new Document("pipeline", List.of("odd")))).containsExactly(on(Actions.READ, "sales", "orders"));
    }

    @Test
    void stopsFollowingAPipelineNestedTooDeep()
    {
        Document deepest = new Document("$lookup", new Document("from", "bottom"));
        for (int depth = 0; depth < 12; depth++) {
            deepest = new Document("$lookup", new Document("from", "level").append("pipeline", List.of(deepest)));
        }
        assertThat(Accesses.of("aggregate", "sales.orders", new Document("pipeline", List.of(deepest)))).containsExactly(on(Actions.READ, "sales", "orders"),
                on(Actions.READ, "sales", "level"));
    }

    @Test
    void aChangeStreamIsNoReading()
    {
        Document arguments = new Document("pipeline", List.of(new Document("$changeStream", new Document())));
        assertThat(Accesses.of("aggregate", "sales.orders", arguments)).containsExactly(on(Actions.CHANGE_STREAM, "sales", "orders"));
        assertThat(Accesses.of("aggregate", "sales.orders", new Document("pipeline", List.of()))).containsExactly(on(Actions.READ, "sales", "orders"));
    }

    @Test
    void skippingValidationIsToldApart()
    {
        assertThat(Accesses.of("insert", "sales.orders", new Document("bypassDocumentValidation", true))).containsExactly(on(Actions.INSERT, "sales", "orders"),
                on(Actions.BYPASS_VALIDATION, "sales", "orders"));
        assertThat(Accesses.of("hello", "sales.orders", new Document("bypassDocumentValidation", true))).isEmpty();
    }

    @Test
    void readsTheNamespacesOfABulkWrite()
    {
        Document arguments = new Document("nsInfo", List.of(new Document("ns", "sales.orders"), new Document("ns", "hr.people"), new Document("ns", "nodot")))
                .append("ops", List.of(new Document("insert", 0), new Document("update", 1), new Document("delete", 1), new Document("insert", 2), new Document("insert", 9)));
        assertThat(Accesses.of("bulkWrite", "admin.$cmd", arguments)).containsExactly(on(Actions.INSERT, "sales", "orders"), on(Actions.UPDATE, "hr", "people"),
                on(Actions.REMOVE, "hr", "people"));
        assertThat(Accesses.of("bulkWrite", "admin.$cmd", new Document())).isEmpty();
        assertThat(Accesses.of("bulkWrite", "admin.$cmd", new Document("nsInfo", List.of()))).isEmpty();
    }
}
