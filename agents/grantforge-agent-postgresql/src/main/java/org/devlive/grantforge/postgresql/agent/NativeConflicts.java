// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Finds the privileges the agent did not give that give users what a GrantForge policy denies them, and warns of each
 * once: PostgreSQL adds up privileges, so a role the agent keeps cannot take away what a user holds itself, through
 * another role or through PUBLIC, nor what it may do with the tables it owns.
 */
final class NativeConflicts
{
    private static final Logger LOG = Logger.getLogger(NativeConflicts.class.getName());

    /** The most new conflicts one sync reports one by one. */
    static final int MAX_REPORTED = 20;
    /** How a conflict names what PUBLIC holds. */
    static final String PUBLIC = "PUBLIC";

    private final Set<String> reported = new HashSet<>();

    /**
     * A privilege that gives a user something a policy denies.
     *
     * @param user the user
     * @param through how the user holds it: {@code null} for itself, else a role's name, or {@code PUBLIC}
     * @param privilege the privilege, or the ownership of a table
     * @param resource the resource a policy denies
     * @param accessType the access type denied
     * @param policyId the denying policy, or {@code null} when policies take part of it away
     */
    record Conflict(String user, @Nullable String through, String privilege, String resource, String accessType, @Nullable Long policyId)
    {
        String key()
        {
            return String.join("\u001f", user, String.valueOf(through), privilege, accessType);
        }

        String message()
        {
            String holds = privilege.startsWith("OWNER ") ? "owns " + privilege.substring(privilege.indexOf(" ON ") + 4) : "holds " + privilege;
            String how = through == null ? "" : " through " + (PUBLIC.equals(through) ? PUBLIC : "role " + through);
            return "PostgreSQL user " + user + " " + holds + how + ", outside GrantForge, which gives " + accessType + " on " + resource + " although "
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
    List<Conflict> check(ServerState state, Snapshot snapshot, Instant now)
    {
        List<Conflict> found = new ArrayList<>();
        for (Account account : state.accounts()) {
            for (ServerState.DatabaseState database : state.databases().values()) {
                for (ServerState.NativeGrant grant : database.nativeGrants()) {
                    String grantee = grant.grantee();
                    if (grantee != null && !grantee.equals(account.name()) && !account.groups().contains(grantee)) {
                        continue;
                    }
                    String through = grantee == null ? PUBLIC : grantee.equals(account.name()) ? null : grantee;
                    Conflict conflict = conflict(snapshot, database, account, through, grant.privilege(), now);
                    if (conflict != null) {
                        found.add(conflict);
                    }
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
            LOG.warning((fresh - MAX_REPORTED) + " more privileges outside GrantForge give PostgreSQL users what GrantForge policies deny");
        }
        reported.retainAll(current);
        reported.addAll(current);
        return found;
    }

    /** What events name the object of a privilege as, such as {@code sales.crm.orders.card}. */
    private static String resource(String database, Privilege privilege)
    {
        return switch (privilege.kind()) {
            case DATABASE -> privilege.object();
            case SCHEMA -> database + "." + privilege.object();
            default -> String.join(".", database, String.valueOf(privilege.schema()), privilege.object())
                    + (privilege.column() == null ? "" : "." + privilege.column());
        };
    }

    /** The first access type of a privilege a policy denies on its object, if any. */
    private static @Nullable Conflict conflict(Snapshot snapshot, ServerState.DatabaseState database, Account account, @Nullable String through,
            Privilege privilege, Instant now)
    {
        String db = database.name();
        List<String> types = new ArrayList<>();
        if ("OWNER".equals(privilege.keyword())) {
            types.addAll(Privilege.TABLE_TYPES);
            types.addAll(Privilege.COLUMN_TYPES);
        }
        else {
            String type = Privilege.accessType(privilege.kind(), privilege.keyword());
            if (type == null) {
                return null;
            }
            types.add(type);
        }
        for (String type : types) {
            AccessRequest.Builder request = AccessRequest.builder(account.name(), type).groups(account.groups().toArray(new String[0])).time(now)
                    .resource("database", db);
            switch (privilege.kind()) {
                case DATABASE -> {
                    // The database is the request's own.
                }
                case SCHEMA -> request.resource("schema", privilege.object());
                default -> {
                    request.resource("schema", String.valueOf(privilege.schema())).resource("table", privilege.object());
                    if (privilege.column() != null) {
                        request.resource("column", privilege.column());
                    }
                }
            }
            String resource = resource(db, privilege);
            AccessRequest asked = request.build();
            AgentDecision decision = snapshot.decide(asked);
            if (decision.outcome() == AgentDecision.Outcome.DENIED) {
                return new Conflict(account.name(), through, privilege.toString(), resource, type, decision.policyId());
            }
            boolean wholeTable = privilege.kind() == Privilege.Kind.TABLE && Privilege.COLUMN_TYPES.contains(type);
            if (wholeTable && snapshot.restrictedBelow(asked)) {
                return new Conflict(account.name(), through, privilege.toString(), resource, type, null);
            }
        }
        return null;
    }
}
