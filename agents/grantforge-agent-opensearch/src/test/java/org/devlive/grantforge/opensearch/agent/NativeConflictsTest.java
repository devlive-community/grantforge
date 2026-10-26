// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.opensearch.agent;

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
        FakeOpenSearch search = ServerStateTest.cluster();
        search.roles.set("odd", FakeOpenSearch.json("{\"index_permissions\": [{\"index_patterns\": [\"/regex/\", \"logs-*\"], \"allowed_actions\":"
                + " [\"custom_group\"]}]}"));
        search.mappings.set("odd", FakeOpenSearch.json("{\"users\": [\"bob\"]}"));
        ServerState state = ServerState.read(search, "grantforge_", Set.of(), Set.of());
        Snapshot snapshot = new OpenSearchSnapshots().deny(List.of("bob"), List.of("read"), "index", "hr", "field", "*")
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
        assertThat(warnings).containsExactly("OpenSearch user root is mapped to all_access, which no role binds; GrantForge keeps no role for it",
                "OpenSearch role readall, which user bob has outside GrantForge, gives read on hr, which GrantForge policy 1 denies");
    }

    @Test
    void readsActionGroupsAndActionsBackIntoAccessTypes()
    {
        assertThat(NativeConflicts.given("crud")).containsExactly("read", "index", "delete");
        assertThat(NativeConflicts.given("indices_all")).contains("manage", "read");
        assertThat(NativeConflicts.given("indices:data/read/*")).containsExactly("read");
        assertThat(NativeConflicts.given("indices:data/write/delete")).containsExactly("delete");
        assertThat(NativeConflicts.given("cluster:monitor/*")).as("the cluster's actions give nothing on indices").isEmpty();
        assertThat(NativeConflicts.given("custom_group")).isEmpty();
    }
}
