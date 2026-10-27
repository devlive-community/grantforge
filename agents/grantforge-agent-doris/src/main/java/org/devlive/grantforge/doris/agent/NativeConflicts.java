// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.Snapshot;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Finds what gives users, outside GrantForge, what a GrantForge policy denies them, and warns of each once: Doris adds
 * up privileges, so a role the agent keeps cannot take away what a user holds directly or through another role, and it
 * joins permissive row policies with OR, so another one widens the rows the agent's filter shows. Users with
 * {@code ADMIN_PRIV} or {@code NODE_PRIV} are reported too, as no role binds them.
 */
final class NativeConflicts
{
    private static final Logger LOG = Logger.getLogger(NativeConflicts.class.getName());

    /** What a superuser's access is said to be. */
    static final String EVERYTHING = "everything";

    /** The most new conflicts one sync reports one by one. */
    static final int MAX_REPORTED = 20;

    private final Set<String> reported = new HashSet<>();

    /**
     * Something outside GrantForge that gives a user what a policy denies.
     *
     * @param user the user
     * @param through where it comes from, such as {@code role analysts}
     * @param grant what it gives, as Doris writes it
     * @param resource the resource a policy denies
     * @param accessType the access type denied
     * @param policyId the denying policy, or {@code null} when policies take part of it away
     */
    record Conflict(String user, String through, String grant, String resource, String accessType, @Nullable Long policyId)
    {
        String key()
        {
            return String.join("\u001f", user, through, grant, accessType);
        }

        String message()
        {
            if (EVERYTHING.equals(accessType)) {
                return "Doris user " + user + " holds " + grant + ", which no role binds; GrantForge policies do not apply to it";
            }
            return "Doris user " + user + " holds " + grant + " " + through + ", outside GrantForge, which gives " + accessType + " on " + resource
                    + " although " + (policyId == null ? "GrantForge policies deny part of it" : "GrantForge policy " + policyId + " denies it")
                    + "; the agent cannot take it away";
        }
    }

    /**
     * Finds the conflicts and warns of those not reported before; those that are gone may be reported again later.
     *
     * @param state what the cluster holds
     * @param snapshot the policies
     * @param plans what the agent planned for each user, by user name
     * @param ownRole the role the agent keeps for a user
     * @param now the moment the policies are applied at
     * @return the conflicts found
     */
    // A conflict and a set per user and grant looked at.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    List<Conflict> check(ServerState state, Snapshot snapshot, Map<String, Plan> plans, Function<String, String> ownRole,
            Instant now)
    {
        List<Conflict> found = new ArrayList<>();
        for (String user : state.superusers()) {
            found.add(new Conflict(user, "directly", "ADMIN_PRIV or NODE_PRIV", "*", EVERYTHING, null));
        }
        for (Account account : state.accounts()) {
            Plan plan = plans.getOrDefault(account.name(), Plan.NOTHING);
            Set<Privilege> fromRoles = new HashSet<>();
            for (String role : account.groups()) {
                Set<Privilege> held = state.otherRoles().getOrDefault(role, Set.of());
                fromRoles.addAll(held);
                for (Privilege privilege : held) {
                    conflict(snapshot, account, "through role " + role, privilege, now, found);
                }
            }
            Set<Privilege> mine = state.roles().getOrDefault(ownRole.apply(account.name()), plan.privilegeSet());
            Set<Privilege> direct = new HashSet<>();
            account.identities().forEach(identity -> direct.addAll(state.userPrivileges().getOrDefault(identity, Set.of())));
            direct.removeAll(fromRoles);
            direct.removeAll(mine);
            for (Privilege privilege : direct) {
                if (!system(privilege)) {
                    conflict(snapshot, account, "directly", privilege, now, found);
                }
            }
            for (TableRef table : account.columnTables()) {
                Set<String> planned = new HashSet<>();
                plan.columns().getOrDefault(table, Collections.emptySortedSet()).forEach(column -> planned.add(column.toLowerCase(Locale.ROOT)));
                for (String column : account.readableAsShown(table)) {
                    if (planned.contains(column.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                    AgentDecision decision = snapshot.decide(request(account, Privileges.SELECT, now).resource("catalog", table.catalog())
                            .resource("database", table.database()).resource("table", table.table()).resource("column", column).build());
                    if (decision.outcome() == AgentDecision.Outcome.DENIED) {
                        found.add(new Conflict(account.name(), "through a grant of the user or another role", "SELECT_PRIV(" + column + ") ON " + table,
                                table + "." + column, Privileges.SELECT, decision.policyId()));
                    }
                }
            }
            for (Map.Entry<ServerState.RowPolicyKey, ServerState.RowPolicy> policy : state.rowPolicies().entrySet()) {
                ServerState.RowPolicy value = policy.getValue();
                TableRef table = policy.getKey().table();
                boolean applies = value.role() != null && account.groups().contains(value.role())
                        || value.user() != null && account.identities().contains(value.user());
                Plan.Filter filter = plan.filters().get(table);
                if (applies && !value.restrictive() && filter != null) {
                    found.add(new Conflict(account.name(), value.role() == null ? "directly" : "through role " + value.role(), "row policy "
                            + policy.getKey().name() + " USING " + value.predicate(), table.toString(), "rows its filter hides", filter.policyId()));
                }
            }
        }
        report(found);
        return found;
    }

    /** Warns of the conflicts not reported before, at most {@value #MAX_REPORTED} of them one by one. */
    private void report(List<Conflict> found)
    {
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
            LOG.warning((fresh - MAX_REPORTED) + " more grants outside GrantForge give Doris users what GrantForge policies deny");
        }
        reported.retainAll(current);
        reported.addAll(current);
    }

    /** Whether a privilege is on one of Doris's own databases, which every user may read. */
    private static boolean system(Privilege privilege)
    {
        String[] parts = privilege.target().split("\\.", -1);
        return !privilege.workloadGroup() && parts.length == 3 && ServerState.SYSTEM_DATABASES.contains(parts[1]);
    }

    /** Adds a conflict when a policy denies an access type of a privilege on its object, or part of it below. */
    private static void conflict(Snapshot snapshot, Account account, String through, Privilege privilege, Instant now, List<Conflict> found)
    {
        for (String type : Privileges.accessTypes(privilege.privilege(), privilege.level())) {
            AccessRequest asked = resource(request(account, type, now), privilege, type);
            if (asked == null) {
                continue;
            }
            AgentDecision decision = snapshot.decide(asked);
            String resource = privilege.workloadGroup() ? privilege.target() : privilege.target().replace(".*", "");
            if (decision.outcome() == AgentDecision.Outcome.DENIED) {
                found.add(new Conflict(account.name(), through, privilege.toString(), resource, type, decision.policyId()));
                return;
            }
            if (!"table".equals(privilege.level()) || Privileges.SELECT.equals(type)) {
                if (snapshot.restrictedBelow(asked)) {
                    found.add(new Conflict(account.name(), through, privilege.toString(), resource, type, null));
                    return;
                }
            }
        }
    }

    /** The request for an access type on a privilege's object, or {@code null} when the policies cannot name it there. */
    private static @Nullable AccessRequest resource(AccessRequest.Builder request, Privilege privilege, String type)
    {
        if (privilege.workloadGroup()) {
            if (!Privileges.USAGE.equals(type)) {
                return null;
            }
            return request.resource("workload_group", "%".equals(privilege.target()) ? "*" : privilege.target()).build();
        }
        if (Privileges.USAGE.equals(type)) {
            return null;
        }
        String[] parts = privilege.target().split("\\.", -1);
        if (parts.length != 3 || "*".equals(parts[0])) {
            return null;
        }
        request.resource("catalog", parts[0]);
        if (!"*".equals(parts[1])) {
            request.resource("database", parts[1]);
            if (!"*".equals(parts[2])) {
                request.resource("table", parts[2]);
            }
        }
        return request.build();
    }

    private static AccessRequest.Builder request(Account account, String type, Instant now)
    {
        return AccessRequest.builder(account.name(), type).groups(account.groups().toArray(new String[0])).time(now);
    }
}
