// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.doris.agent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Brings Doris in step with the plans: one role per user that holds exactly what the user's plan grants, granted to
 * each identity of the user, and one row policy of that role per filtered table. Nothing else is touched: the agent's
 * roles and row policies are those whose names start with its prefix. What a role holds on objects as a whole is read
 * back from {@code SHOW ROLES} at each sync, so a change made by hand is undone and nothing is sent when nothing
 * changed.
 *
 * <p>Doris shows column privileges only per user, merged over all of a user's grants. The agent grants a role the
 * columns its plan names wherever the user cannot read them all, and revokes from its role the other columns the user
 * may read; a revoke that leaves such a column readable means a grant outside GrantForge gives it, and is not tried
 * again while the plan stays the same.
 *
 * <p>Changes never widen access on the way: a new or changed row filter is in place before the privileges it narrows,
 * a filter that changes is replaced behind a restrictive policy that shows no rows, and a filter goes only after the
 * privileges it narrowed. A row filter Doris cannot run on its table, or refuses, is replaced by one that shows the user no
 * rows.
 */
final class Reconciler
{
    private static final Logger LOG = Logger.getLogger(Reconciler.class.getName());

    /** A filter that shows no rows, for a filter that cannot be applied as it is. */
    static final String NO_ROWS = "1 = 0";

    /** What the name of a row policy that holds a table back while its filter changes ends with. */
    static final String HOLD = "_hold";

    /** What the agent writes in the comment of each of its roles. */
    static final String COMMENT = "GrantForge agent role of user ";

    private final Doris doris;
    private final String prefix;
    private final Map<ServerState.RowPolicyKey, AppliedFilter> filters = new HashMap<>();
    private final Map<String, Map<TableRef, Set<String>>> outsideColumns = new HashMap<>();
    private Map<String, Plan> lastPlans = Map.of();

    /** What the agent last made a row policy filter: the policy's condition, and the predicate Doris showed afterwards. */
    private record AppliedFilter(String expression, String shown)
    {
    }

    /**
     * What one sync changed.
     *
     * @param users the users planned
     * @param roles the roles created
     * @param dropped the roles dropped
     * @param privileges the privileges granted or revoked
     * @param rowPolicies the row policies created, replaced or dropped
     * @param failed the users whose changes failed
     */
    record Outcome(int users, int roles, int dropped, int privileges, int rowPolicies, int failed)
    {
        boolean changed()
        {
            return roles + dropped + privileges + rowPolicies + failed > 0;
        }
    }

    Reconciler(Doris doris, String prefix)
    {
        this.doris = doris;
        this.prefix = prefix;
    }

    /**
     * Returns the role the agent keeps for a user: the prefix and the user name, which Doris takes as a role name when it
     * holds letters, digits, underscores and hyphens only and stays within 64 characters; otherwise the name's other
     * characters become underscores and a hash of the name keeps roles of different users apart.
     *
     * @param user the user name
     * @return the role's name
     */
    String role(String user)
    {
        String plain = prefix + user;
        if (plain.length() <= 64 && plain.matches("[A-Za-z][A-Za-z0-9_-]*")) {
            return plain;
        }
        String hash = String.format(Locale.ROOT, "%08x", user.hashCode());
        String cleaned = user.replaceAll("[^A-Za-z0-9_-]", "_");
        int room = 64 - prefix.length() - hash.length() - 1;
        return prefix + (cleaned.length() > room ? cleaned.substring(0, room) : cleaned) + "_" + hash;
    }

    /**
     * Brings Doris in step.
     *
     * @param state what the cluster holds
     * @param plans the plan of each user; {@code null} plans grant nothing to anybody
     * @return what changed
     * @throws DorisFailure when no frontend can be reached; changes made so far stay
     */
    Outcome reconcile(ServerState state, @Nullable Function<Account, Plan> plans)
    {
        Map<Account, Plan> planned = new LinkedHashMap<>();
        Set<String> kept = new LinkedHashSet<>();
        int failed = 0;
        for (Account account : state.accounts()) {
            try {
                Plan plan = plans == null ? Plan.NOTHING : plans.apply(account);
                planned.put(account, plan);
                if (!plan.empty()) {
                    kept.add(role(account.name()));
                }
            }
            catch (RuntimeException broken) {
                if (broken instanceof DorisFailure failure && failure.unreachable()) {
                    throw broken;
                }
                // The user's role stays as it is until its plan can be made.
                LOG.log(Level.WARNING, "The GrantForge policies of Doris user " + account.name() + " cannot be planned: " + broken.getMessage(), broken);
                kept.add(role(account.name()));
                failed++;
            }
        }

        int dropped = 0;
        int rowPolicies = 0;
        for (String role : state.roles().keySet()) {
            if (!kept.contains(role)) {
                doris.execute("DROP ROLE IF EXISTS " + Privilege.quote(role));
                outsideColumns.remove(role);
                dropped++;
            }
        }

        int roles = 0;
        int privileges = 0;
        Set<ServerState.RowPolicyKey> wanted = new HashSet<>();
        for (Map.Entry<Account, Plan> entry : planned.entrySet()) {
            Account account = entry.getKey();
            Plan plan = entry.getValue();
            if (plan.empty()) {
                continue;
            }
            String role = role(account.name());
            plan.filters().keySet().forEach(table -> wanted.add(new ServerState.RowPolicyKey(role, table)));
            try {
                if (!state.roles().containsKey(role)) {
                    doris.execute("CREATE ROLE " + Privilege.quote(role) + " COMMENT " + Privilege.literal(COMMENT + account.name()));
                    roles++;
                }
                for (Map.Entry<TableRef, Plan.Filter> filter : plan.filters().entrySet()) {
                    if (filter(state, account, role, filter.getKey(), filter.getValue())) {
                        rowPolicies++;
                    }
                }
                privileges += privileges(role, state.roles().getOrDefault(role, Set.of()), plan);
                privileges += columns(role, account, plan);
                for (String identity : account.identities()) {
                    if (!account.granted().getOrDefault(identity, Set.of()).contains(role)) {
                        doris.execute("GRANT " + Privilege.literal(role) + " TO " + identity);
                        privileges++;
                    }
                }
                rowPolicies += dropStale(state, role, plan);
            }
            catch (DorisFailure refused) {
                if (refused.unreachable()) {
                    throw refused;
                }
                LOG.warning("Doris refused the GrantForge role of user " + account.name() + ": " + refused.getMessage());
                failed++;
            }
        }

        for (Map.Entry<ServerState.RowPolicyKey, ServerState.RowPolicy> policy : state.rowPolicies().entrySet()) {
            ServerState.RowPolicyKey key = policy.getKey();
            String role = ownRole(key);
            if (role != null && !wanted.contains(key) && !kept.contains(role)) {
                dropRowPolicy(key);
                rowPolicies++;
            }
        }
        Map<String, Plan> byName = new HashMap<>();
        planned.forEach((account, plan) -> byName.put(account.name(), plan));
        lastPlans = Map.copyOf(byName);
        return new Outcome(planned.size(), roles, dropped, privileges, rowPolicies, failed);
    }

    /**
     * Returns what the last sync planned for each user.
     *
     * @return the plans, by user name
     */
    Map<String, Plan> plans()
    {
        return lastPlans;
    }

    /** The role one of the agent's row policies belongs to, or {@code null} for a policy that is not the agent's. */
    private @Nullable String ownRole(ServerState.RowPolicyKey key)
    {
        String name = key.name();
        if (!name.startsWith(prefix)) {
            return null;
        }
        return name.endsWith(HOLD) ? name.substring(0, name.length() - HOLD.length()) : name;
    }

    /** Grants what the role lacks and revokes what it holds beyond its plan, on objects as a whole. */
    private int privileges(String role, Set<Privilege> held, Plan plan)
    {
        Set<Privilege> wanted = plan.privilegeSet();
        int changed = 0;
        for (Map.Entry<String, List<Privilege>> missing : byTarget(plan.privileges().stream().filter(privilege -> !held.contains(privilege)).toList())
                .entrySet()) {
            String on = missing.getValue().get(0).on();
            if (on == null) {
                LOG.warning("GrantForge cannot grant " + missing.getValue() + " to role " + role + ", as the name holds a dot");
                continue;
            }
            doris.execute("GRANT " + list(missing.getValue()) + " ON " + on + " TO ROLE " + Privilege.literal(role));
            changed += missing.getValue().size();
        }
        for (Map.Entry<String, List<Privilege>> extra : byTarget(held.stream().filter(privilege -> !wanted.contains(privilege)).sorted(Privilege.ORDER)
                .toList()).entrySet()) {
            String on = extra.getValue().get(0).on();
            if (on == null) {
                LOG.warning("GrantForge role " + role + " holds " + extra.getValue() + ", which cannot be revoked by name; revoke it by hand");
                continue;
            }
            doris.execute("REVOKE " + list(extra.getValue()) + " ON " + on + " FROM ROLE " + Privilege.literal(role));
            changed += extra.getValue().size();
        }
        return changed;
    }

    // A list per object.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static Map<String, List<Privilege>> byTarget(List<Privilege> privileges)
    {
        Map<String, List<Privilege>> byTarget = new LinkedHashMap<>();
        for (Privilege privilege : privileges) {
            byTarget.computeIfAbsent((privilege.workloadGroup() ? "\u0001" : "") + privilege.target(), ignored -> new ArrayList<>()).add(privilege);
        }
        return byTarget;
    }

    private static String list(List<Privilege> privileges)
    {
        return privileges.stream().map(Privilege::privilege).distinct().collect(Collectors.joining(", "));
    }

    /**
     * Grants the role the columns its plan names where the user cannot read them all, and revokes the others the user may
     * read; columns a revoke left readable are a grant outside GrantForge and are not revoked again for the same plan.
     */
    // A set per table looked at.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private int columns(String role, Account account, Plan plan)
    {
        int changed = 0;
        Map<TableRef, Set<String>> outside = outsideColumns.computeIfAbsent(role, ignored -> new HashMap<>());
        Set<TableRef> tables = new LinkedHashSet<>(plan.columns().keySet());
        tables.addAll(account.columnTables());
        tables.addAll(outside.keySet());
        for (TableRef table : tables) {
            Set<String> names = plan.columns().getOrDefault(table, Collections.emptySortedSet());
            Set<String> planned = new HashSet<>();
            names.forEach(column -> planned.add(column.toLowerCase(Locale.ROOT)));
            if (!planned.isEmpty() && !account.readableByAll(table).containsAll(planned)) {
                doris.execute("GRANT SELECT_PRIV(" + columnList(names) + ") ON " + table.sql() + " TO ROLE "
                        + Privilege.literal(role));
                changed++;
            }
            List<String> extra = account.readableAsShown(table).stream().filter(column -> !planned.contains(column.toLowerCase(Locale.ROOT)))
                    .sorted().toList();
            Set<String> known = outside.getOrDefault(table, Set.of());
            if (extra.isEmpty()) {
                outside.remove(table);
            }
            else if (!known.equals(Set.copyOf(extra))) {
                doris.execute("REVOKE SELECT_PRIV(" + columnList(extra) + ") ON " + table.sql() + " FROM ROLE " + Privilege.literal(role));
                outside.put(table, Set.copyOf(extra));
                changed++;
            }
        }
        return changed;
    }

    private static String columnList(Collection<String> columns)
    {
        return columns.stream().map(Privilege::quote).collect(Collectors.joining(", "));
    }

    /**
     * Puts a row filter in place unless it is.
     *
     * @return whether the row policy was created or replaced
     */
    private boolean filter(ServerState state, Account account, String role, TableRef table, Plan.Filter filter)
    {
        ServerState.RowPolicyKey key = new ServerState.RowPolicyKey(role, table);
        ServerState.RowPolicy existing = state.rowPolicies().get(key);
        AppliedFilter last = filters.get(key);
        if (existing != null && !existing.restrictive() && role.equals(existing.role()) && existing.user() == null && kept(existing, last, filter)) {
            filters.put(key, new AppliedFilter(filter.expression(), existing.predicate()));
            return false;
        }
        ServerState.RowPolicyKey hold = new ServerState.RowPolicyKey(role + HOLD, table);
        if (existing != null) {
            createRowPolicy(hold, role, true, NO_ROWS);
            dropRowPolicy(key);
        }
        String expression = filter.expression();
        try {
            // Some releases take a filter whose columns the table does not have, and then fail the user's every query of it.
            doris.query("SELECT 1 FROM " + table.sql() + " WHERE (" + expression + ") LIMIT 0");
            createRowPolicy(key, role, false, expression);
        }
        catch (DorisFailure refused) {
            if (refused.unreachable()) {
                throw refused;
            }
            LOG.severe("Doris refused the row filter of GrantForge policy " + filter.policyId() + " for user " + account.name() + " on " + table
                    + ", so the user sees no rows of it: " + refused.getMessage());
            expression = NO_ROWS;
            createRowPolicy(key, role, false, NO_ROWS);
        }
        if (existing != null) {
            dropRowPolicy(hold);
        }
        filters.put(key, new AppliedFilter(filter.expression(), "(" + expression + ")"));
        return true;
    }

    /** Whether the agent's row policy already filters as the plan says: as it was last made, or as Doris shows it. */
    private static boolean kept(ServerState.RowPolicy existing, @Nullable AppliedFilter last, Plan.Filter filter)
    {
        if (last != null) {
            return last.expression().equals(filter.expression()) && same(existing.predicate(), last.shown());
        }
        return same(existing.predicate(), filter.expression());
    }

    /**
     * Returns whether Doris's predicate is the filter as the agent wrote it, which Doris shows in parentheses and, in
     * some releases, with its names in backquotes.
     *
     * @param shown what Doris shows
     * @param expression the filter
     * @return {@code true} if they differ in outer parentheses, backquotes and spaces only
     */
    static boolean same(String shown, String expression)
    {
        return bare(shown).equals(bare(expression));
    }

    private static String bare(String text)
    {
        String bare = text.replace("`", "").strip().replaceAll("\\s+", " ");
        while (bare.startsWith("(") && bare.endsWith(")") && balanced(bare.substring(1, bare.length() - 1))) {
            bare = bare.substring(1, bare.length() - 1).strip();
        }
        return bare;
    }

    private static boolean balanced(String text)
    {
        int depth = 0;
        for (char character : text.toCharArray()) {
            depth += character == '(' ? 1 : character == ')' ? -1 : 0;
            if (depth < 0) {
                return false;
            }
        }
        return depth == 0;
    }

    private void createRowPolicy(ServerState.RowPolicyKey key, String role, boolean restrictive, String expression)
    {
        doris.execute("CREATE ROW POLICY " + Privilege.quote(key.name()) + " ON " + key.table().sql() + " AS " + (restrictive ? "RESTRICTIVE" : "PERMISSIVE")
                + " TO ROLE " + Privilege.quote(role) + " USING (" + expression + ")");
    }

    /**
     * Drops the role's row policies of tables its plan no longer filters, after its privileges no longer need them, and
     * any policy left holding a table back by a change that did not finish.
     */
    private int dropStale(ServerState state, String role, Plan plan)
    {
        int dropped = 0;
        for (ServerState.RowPolicyKey key : state.rowPolicies().keySet()) {
            boolean stale = key.name().equals(role) && !plan.filters().containsKey(key.table());
            if (stale || key.name().equals(role + HOLD)) {
                dropRowPolicy(key);
                dropped++;
            }
        }
        return dropped;
    }

    private void dropRowPolicy(ServerState.RowPolicyKey key)
    {
        doris.execute("DROP ROW POLICY IF EXISTS " + Privilege.quote(key.name()) + " ON " + key.table().sql());
        filters.remove(key);
    }
}
