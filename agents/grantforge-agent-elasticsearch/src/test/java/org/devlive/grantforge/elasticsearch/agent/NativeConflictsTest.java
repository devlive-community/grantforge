// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.elasticsearch.agent;

import org.devlive.grantforge.agent.Snapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class NativeConflictsTest
{
    @Test
    void warnsOnceOfRolesOutsideGrantForgeThatGiveWhatAPolicyDenies()
    {
        FakeElasticsearch search = ServerStateTest.cluster();
        search.roles.set("odd", FakeElasticsearch.json("{\"indices\": [{\"names\": [\"/regex/\", \"logs-*\"], \"privileges\":"
                + " [\"custom_privilege\"]}]}"));
        search.user("bob", "odd", "dave_reader");
        ServerState state = ServerState.read(search, "grantforge_", Set.of(), Set.of());
        Snapshot snapshot = new ElasticsearchSnapshots().deny(List.of("bob"), List.of("read"), "index", "hr", "field", "*")
                .allow(List.of("bob"), List.of("read"), "index", "logs-eu", "field", "*").snapshot();
        List<String> warnings = new CopyOnWriteArrayList<>();
        Handler capture = new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                warnings.add(record.getMessage());
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
        Logger logger = Logger.getLogger(NativeConflicts.class.getName());
        logger.addHandler(capture);
        try {
            NativeConflicts conflicts = new NativeConflicts();
            conflicts.check(state, snapshot, Instant.now());
            conflicts.check(state, snapshot, Instant.now());
        }
        finally {
            logger.removeHandler(capture);
        }
        assertThat(warnings).containsExactly("Elasticsearch user root has the superuser role, which no role binds; GrantForge keeps no role for it",
                "Elasticsearch role dave_reader, which user bob has outside GrantForge, gives read on hr, which GrantForge policy 1 denies");
    }

    @Test
    void readsPrivilegesAndActionsBackIntoAccessTypes()
    {
        assertThat(NativeConflicts.given("write")).containsExactly("index", "delete");
        assertThat(NativeConflicts.given("all")).contains("manage", "read", "delete_index");
        assertThat(NativeConflicts.given("manage")).contains("delete_index", "view_index_metadata");
        assertThat(NativeConflicts.given("indices:data/read/*")).containsExactly("read");
        assertThat(NativeConflicts.given("indices:data/write/delete")).containsExactly("delete");
        assertThat(NativeConflicts.given("cluster:monitor/*")).as("the cluster's actions give nothing on indices").isEmpty();
        assertThat(NativeConflicts.given("custom_privilege")).isEmpty();
    }
}
