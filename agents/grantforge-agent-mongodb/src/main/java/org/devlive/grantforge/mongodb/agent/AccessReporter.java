// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mongodb.agent;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Reports what a command touched as access events, each saying whether GrantForge's policies decide as MongoDB did. The
 * event names the command and its namespace, never its arguments, which can hold documents and passwords. MongoDB refuses a
 * command as a whole without saying which of the collections it named was the reason, so a refusal is reported for those
 * the policies deny, or for the first when they deny none.
 */
final class AccessReporter
{
    private final String self;
    private final Supplier<@Nullable Snapshot> snapshots;
    private final Function<String, Set<String>> groups;
    private final Supplier<@Nullable Inventory> inventory;
    private final Consumer<AccessEvent> sink;

    /**
     * Reports to a sink.
     *
     * @param self the agent's own user, whose commands are not reported
     * @param snapshots the policies
     * @param groups the groups each user has
     * @param inventory the deployment, or {@code null} before the first sync
     * @param sink what takes the events
     */
    AccessReporter(String self, Supplier<@Nullable Snapshot> snapshots, Function<String, Set<String>> groups, Supplier<@Nullable Inventory> inventory,
            Consumer<AccessEvent> sink)
    {
        this.self = self;
        this.snapshots = snapshots;
        this.groups = groups;
        this.inventory = inventory;
        this.sink = sink;
    }

    /**
     * A command someone ran.
     *
     * @param user the user's name
     * @param command the command's name
     * @param namespace its namespace
     * @param accesses what it touched
     * @param allowed whether MongoDB allowed it
     * @param at when
     * @param address the client's address, or {@code null}
     */
    record Command(String user, String command, String namespace, List<Accesses.Access> accesses, boolean allowed, Instant at, @Nullable String address)
    {
        /** Copies what it touched. */
        Command
        {
            accesses = List.copyOf(accesses);
        }
    }

    /**
     * Reports a command.
     *
     * @param command the command
     */
    void report(Command command)
    {
        if (command.user().isEmpty() || command.user().equals(self) || command.user().startsWith("__") || command.accesses().isEmpty()) {
            return;
        }
        Snapshot snapshot = snapshots.get();
        Inventory known = inventory.get();
        Set<String> held = groups.apply(command.user());
        List<Accesses.Access> reported = new ArrayList<>(command.accesses());
        if (!command.allowed() && reported.size() > 1) {
            List<Accesses.Access> denied = reported.stream().filter(access -> snapshot != null
                    && snapshot.decide(request(command, access, held, known)).outcome() == AgentDecision.Outcome.DENIED).toList();
            reported = denied.isEmpty() ? List.of(reported.get(0)) : denied;
        }
        for (Accesses.Access access : reported) {
            AccessEvent.Builder built = AccessEvent.builder(command.user(), name(access, known), access.type(), command.allowed()).occurredAt(command.at())
                    .clientIp(command.address()).resourceType(level(access)).action(command.command()).request((command.command() + " " + command.namespace()).strip());
            if (snapshot != null) {
                AgentDecision decision = snapshot.decide(request(command, access, held, known));
                if (decision.allowed() == command.allowed()) {
                    built.decidedBy(decision).enforcedByGrantForge();
                }
            }
            sink.accept(built.build());
        }
    }

    private static AccessRequest request(Command command, Accesses.Access access, Set<String> held, @Nullable Inventory known)
    {
        AccessRequest.Builder request = AccessRequest.builder(command.user(), access.type()).groups(held.toArray(new String[0])).time(command.at());
        String database = access.database();
        String collection = access.collection();
        if (database == null) {
            request.resource(Planner.DEPLOYMENT, deployment(known));
        }
        else {
            request.resource(Planner.DATABASE, database);
            if (collection != null) {
                request.resource(Planner.COLLECTION, collection);
            }
        }
        return request.build();
    }

    private static String deployment(@Nullable Inventory known)
    {
        return known == null ? Inventory.STANDALONE : known.deployment();
    }

    private static String name(Accesses.Access access, @Nullable Inventory known)
    {
        String database = access.database();
        String collection = access.collection();
        if (database == null) {
            return deployment(known);
        }
        return collection == null ? database : database + "." + collection;
    }

    private static String level(Accesses.Access access)
    {
        if (access.database() == null) {
            return Planner.DEPLOYMENT;
        }
        return access.collection() == null ? Planner.DATABASE : Planner.COLLECTION;
    }
}
