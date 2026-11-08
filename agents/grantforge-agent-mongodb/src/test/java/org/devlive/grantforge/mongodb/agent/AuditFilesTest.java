// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.EventFields;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

class AuditFilesTest
{
    private static final String CHECK = """
            { "atype" : "authCheck", "ts" : { "$date" : "2026-11-07T07:34:16.544+00:00" }, "local" : { "ip" : "172.17.0.5", "port" : 27017 }, \
            "remote" : { "ip" : "10.0.0.9", "port" : 33886 }, "users" : [ { "user" : "%s", "db" : "admin" } ], "roles" : [], \
            "param" : { "command" : "%s", "ns" : "%s", "args" : { "%s" : "x", "documents" : [ { "card" : "4111" } ], "lsid" : { "id" : { "$binary" : "kHpgMlRkSyyR9PSUGHArDw==", "$type" : "04" } } } }, "result" : %d }
            """.replace("\n", "") + "\n";

    @TempDir
    Path directory;

    private static String line(String user, String command, String namespace, int result)
    {
        return CHECK.formatted(user, command, namespace, command, result);
    }

    @Test
    void readsWhatAnAuthCheckEventTellsOf()
    {
        AccessReporter.Command command = requireNonNull(AuditFiles.command(line("alice", "find", "sales.orders", 0)), "command");
        assertThat(command.user()).isEqualTo("alice");
        assertThat(command.command()).isEqualTo("find");
        assertThat(command.namespace()).isEqualTo("sales.orders");
        assertThat(command.allowed()).isTrue();
        assertThat(command.address()).isEqualTo("10.0.0.9");
        assertThat(command.at()).isEqualTo(Instant.parse("2026-11-07T07:34:16.544Z"));
        assertThat(command.accesses()).containsExactly(new Accesses.Access(Actions.READ, "sales", "orders"));
        assertThat(requireNonNull(AuditFiles.command(line("alice", "find", "sales.orders", 13)), "command").allowed()).isFalse();
    }

    @Test
    void leavesOutWhatTellsOfNoCommand()
    {
        assertThat(AuditFiles.command("not json")).isNull();
        assertThat(AuditFiles.command("{ \"atype\" : \"authenticate\" }")).isNull();
        assertThat(AuditFiles.command("{ \"atype\" : \"authCheck\" }")).isNull();
        assertThat(AuditFiles.command("{ \"atype\" : \"authCheck\", \"param\" : { \"command\" : \"find\" }, \"users\" : [] }")).isNull();
        assertThat(AuditFiles.command("{ \"atype\" : \"authCheck\", \"param\" : { }, \"users\" : [ { \"user\" : \"a\" } ] }")).isNull();
    }

    @Test
    void readsAnEventThatLeavesThingsOut()
    {
        AccessReporter.Command command = requireNonNull(AuditFiles.command(
                "{ \"atype\" : \"authCheck\", \"param\" : { \"command\" : \"find\", \"ns\" : \"sales.orders\" }, \"users\" : [ { \"user\" : \"a\" } ] }"), "command");
        assertThat(command.allowed()).isTrue();
        assertThat(command.address()).isNull();
        assertThat(command.at()).isNotNull();
    }

    @Test
    void followsTheFilesAndReportsWithoutTheArguments() throws IOException
    {
        Path file = directory.resolve("audit.json");
        Files.writeString(file, line("old", "find", "sales.orders", 0));
        List<AccessEvent> events = new ArrayList<>();
        AccessReporter reporter = new AccessReporter("grantforge", () -> null, user -> Set.of(), () -> null, events::add);
        AuditFiles files = new AuditFiles(new AgentConfig.Audit(List.of(file), Duration.ZERO, Duration.ofSeconds(5), 10), directory.resolve("position"), reporter);
        assertThat(files.poll()).as("read from its end at first").isZero();
        Files.writeString(file, line("alice", "insert", "sales.orders", 13) + "{ \"atype\" : \"logout\" }\n" + line("grantforge", "find", "sales.orders", 0), StandardOpenOption.APPEND);
        assertThat(files.poll()).isEqualTo(3);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(EventFields.of(event)).containsEntry("user", "alice").containsEntry("accessType", "insert").containsEntry("outcome", "DENIED");
            assertThat(EventFields.of(event).toString()).doesNotContain("4111");
        });
    }
}
