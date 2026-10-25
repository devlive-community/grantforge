// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Finds the grants the agent did not make that give users what a GrantForge policy denies them, and warns of each once:
 * ClickHouse adds up privileges, so a role the agent keeps cannot take away what a user holds directly or through
 * another role. A grant on several databases or tables is reported with the first one a policy denies.
 */
final class NativeConflicts
{
    private static final Logger LOG = Logger.getLogger(NativeConflicts.class.getName());

    /** The most new conflicts one sync reports one by one. */
    static final int MAX_REPORTED = 20;

    private final Set<String> reported = new HashSet<>();

    /**
     * A grant that gives a user something a policy denies.
     *
     * @param user the user
     * @param through the role it holds the grant through, or {@code null} for a grant of its own
     * @param grant the grant as ClickHouse writes it
     * @param resource the first resource of the grant a policy denies
     * @param accessType the access type denied
     * @param policyId the denying policy, or {@code null} when policies take part of it away
     */
    record Conflict(String user, @Nullable String through, String grant, String resource, String accessType, @Nullable Long policyId)
    {
        String key()
        {
            return String.join("\u001f", user, String.valueOf(through), grant);
        }

        String message()
        {
            return "ClickHouse user " + user + " holds " + grant + (through == null ? " directly" : " through role " + through)
                    + ", outside GrantForge, which gives " + accessType + " on " + resource + " although "
                    + (policyId == null ? "GrantForge policies deny part of it" : "GrantForge policy " + policyId + " denies it")
                    + "; the agent cannot take it away";
        }
    }

    /**
     * Finds the conflicts and warns of those not reported before; those that are gone may be reported again later.
     *
     * @param state what the server holds
     * @param snapshot the policies
     * @param now the moment the policies are applied at
     * @return the conflicts found
     */
    // A list per user and role that holds grants.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    List<Conflict> check(ServerState state, Snapshot snapshot, Instant now)
    {
        Map<String, List<ServerState.NativeGrant>> byUser = new LinkedHashMap<>();
        Map<String, List<ServerState.NativeGrant>> byRole = new LinkedHashMap<>();
        for (ServerState.NativeGrant grant : state.nativeGrants()) {
            if (grant.user() != null) {
                byUser.computeIfAbsent(grant.user(), ignored -> new ArrayList<>()).add(grant);
            }
            else if (grant.role() != null) {
                byRole.computeIfAbsent(grant.role(), ignored -> new ArrayList<>()).add(grant);
            }
        }
        List<Conflict> found = new ArrayList<>();
        for (Account account : state.accounts()) {
            for (ServerState.NativeGrant grant : byUser.getOrDefault(account.name(), List.of())) {
                conflict(snapshot, state.inventory(), account, null, grant, now, found);
            }
            for (String role : account.groups()) {
                for (ServerState.NativeGrant grant : byRole.getOrDefault(role, List.of())) {
                    conflict(snapshot, state.inventory(), account, role, grant, now, found);
                }
            }
        }
        Set<String> current = new HashSet<>();
        int fresh = 0;
        for (Conflict conflict : found) {
            current.add(conflict.key());
            if (!reported.contains(conflict.key())) {
                fresh++;
                if (fresh <= MAX_REPORTED) {
                    LOG.warning(conflict.message());
                }
            }
        }
        if (fresh > MAX_REPORTED) {
            LOG.warning((fresh - MAX_REPORTED) + " more grants outside GrantForge give ClickHouse users what GrantForge policies deny");
        }
        reported.retainAll(current);
        reported.addAll(current);
        return found;
    }

    private static void conflict(Snapshot snapshot, Inventory inventory, Account account, @Nullable String through, ServerState.NativeGrant grant,
            Instant now, List<Conflict> found)
    {
        for (String type : Privileges.accessTypes(grant.privilege())) {
            Conflict conflict = first(snapshot, inventory, account, through, grant, type, now);
            if (conflict != null) {
                found.add(conflict);
                return;
            }
        }
    }

    /** The first resource of a grant on which a policy denies an access type of it, if any. */
    private static @Nullable Conflict first(Snapshot snapshot, Inventory inventory, Account account, @Nullable String through,
            ServerState.NativeGrant grant, String type, Instant now)
    {
        if (Privileges.SERVER_TYPES.contains(type)) {
            if (grant.database() != null) {
                return null;
            }
            AgentDecision decision = snapshot.decide(request(account, type, now).resource("server", "*").build());
            return decision.outcome() == AgentDecision.Outcome.DENIED ? new Conflict(account.name(), through, grant.text(), "*", type,
                    decision.policyId()) : null;
        }
        List<String> databases = new ArrayList<>();
        if (grant.database() == null) {
            inventory.databases().forEach(databases::add);
        }
        else {
            databases.add(grant.database());
        }
        for (String database : databases) {
            if (Privileges.DATABASE_TYPES.contains(type)) {
                if (grant.table() != null) {
                    continue;
                }
                AgentDecision decision = snapshot.decide(request(account, type, now).resource("database", database).build());
                if (decision.outcome() == AgentDecision.Outcome.DENIED) {
                    return new Conflict(account.name(), through, grant.text(), database, type, decision.policyId());
                }
                continue;
            }
            List<String> tables = grant.table() == null ? inventory.tables(database) : List.of(grant.table());
            for (String table : tables) {
                AccessRequest.Builder request = request(account, type, now).resource("database", database).resource("table", table);
                boolean columns = Privileges.COLUMN_TYPES.contains(type);
                if (columns && grant.column() != null) {
                    request.resource("column", grant.column());
                }
                AccessRequest asked = request.build();
                AgentDecision decision = snapshot.decide(asked);
                String resource = database + "." + table + (columns && grant.column() != null ? "." + grant.column() : "");
                if (decision.outcome() == AgentDecision.Outcome.DENIED) {
                    return new Conflict(account.name(), through, grant.text(), resource, type, decision.policyId());
                }
                if (columns && grant.column() == null && snapshot.restrictedBelow(asked)) {
                    return new Conflict(account.name(), through, grant.text(), resource, type, null);
                }
            }
        }
        return null;
    }

    private static AccessRequest.Builder request(Account account, String type, Instant now)
    {
        return AccessRequest.builder(account.name(), type).groups(account.groups().toArray(new String[0])).time(now);
    }
}
