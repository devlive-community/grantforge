// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.clickhouse.agent;

import com.clickhouse.client.api.sql.SQLUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Brings ClickHouse in step with the plans: one role per user that holds exactly what the user's plan grants, granted
 * to the user as a default role, and one row policy of that role per filtered table. Nothing else is touched: the
 * agent's roles and row policies are those whose names start with its prefix. What the agent changed last is
 * remembered, so a change made by hand to one of its roles or row policies is undone at the next sync, and nothing is
 * sent when nothing changed.
 *
 * <p>Changes never widen access on the way: a new or changed row filter is in place before the privileges it narrows,
 * and a filter goes only after the privileges it narrowed. A row filter ClickHouse refuses, or one that would apply to
 * others than the user, is replaced by one that shows the user no rows.
 */
final class Reconciler
{
    private static final Logger LOG = Logger.getLogger(Reconciler.class.getName());

    /** A filter that shows no rows, for a filter that cannot be applied as it is. */
    static final String NO_ROWS = "0";

    private final ClickHouse clickhouse;
    private final String prefix;
    private final Map<String, Applied> applied = new HashMap<>();
    private final Map<ServerState.RowPolicyKey, AppliedFilter> filters = new HashMap<>();

    /** What the agent last made a role hold: the statement, and the privileges ClickHouse showed afterwards. */
    private record Applied(String statement, @Nullable String privileges)
    {
    }

    /** What the agent last made a row policy filter: the policy's condition, and the filter ClickHouse showed afterwards. */
    private record AppliedFilter(String expression, String shown)
    {
    }

    /**
     * What one sync changed.
     *
     * @param users the users planned
     * @param roles the roles whose privileges were set
     * @param dropped the roles dropped
     * @param rowPolicies the row policies created, replaced or dropped
     * @param failed the users whose changes failed
     */
    record Outcome(int users, int roles, int dropped, int rowPolicies, int failed)
    {
        boolean changed()
        {
            return roles + dropped + rowPolicies + failed > 0;
        }
    }

    Reconciler(ClickHouse clickhouse, String prefix)
    {
        this.clickhouse = clickhouse;
        this.prefix = prefix;
    }

    /**
     * Returns the role the agent keeps for a user.
     *
     * @param user the user name
     * @return the role's name
     */
    String role(String user)
    {
        return prefix + user;
    }

    /**
     * Brings ClickHouse in step.
     *
     * @param state what the server holds
     * @param plans the plan of each user; {@code null} plans grant nothing to anybody
     * @return what changed
     * @throws ClickHouseFailure when no server can be reached; changes made so far stay
     */
    // A key per filtered table and a record per role changed are what the sync keeps track of.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    Outcome reconcile(ServerState state, @Nullable Function<Account, Plan> plans)
    {
        Map<Account, Plan> planned = new HashMap<>();
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
                // The user's role stays as it is until its plan can be made.
                LOG.log(Level.WARNING, "The GrantForge policies of ClickHouse user " + account.name() + " cannot be planned: " + broken.getMessage(),
                        broken);
                kept.add(role(account.name()));
                failed++;
            }
        }

        int dropped = 0;
        for (String role : state.roles()) {
            if (!kept.contains(role)) {
                clickhouse.execute("DROP ROLE IF EXISTS " + quote(role));
                applied.remove(role);
                dropped++;
            }
        }

        int roles = 0;
        int rowPolicies = 0;
        Set<ServerState.RowPolicyKey> wanted = new HashSet<>();
        List<String> changedRoles = new ArrayList<>();
        for (Map.Entry<Account, Plan> entry : planned.entrySet()) {
            Account account = entry.getKey();
            Plan plan = entry.getValue();
            if (plan.empty()) {
                continue;
            }
            String role = role(account.name());
            plan.filters().keySet().forEach(table -> wanted.add(new ServerState.RowPolicyKey(role, table)));
            try {
                if (!state.roles().contains(role)) {
                    clickhouse.execute("CREATE ROLE IF NOT EXISTS " + quote(role));
                }
                for (Map.Entry<TableRef, Plan.Filter> filter : plan.filters().entrySet()) {
                    if (filter(state, role, filter.getKey(), filter.getValue())) {
                        rowPolicies++;
                    }
                }
                String statement = plan.grantStatement(quote(role));
                Applied last = applied.get(role);
                if (last == null || !last.statement().equals(statement) || !Objects.equals(last.privileges(), state.privileges().get(role))) {
                    clickhouse.execute(statement);
                    applied.put(role, new Applied(statement, null));
                    changedRoles.add(role);
                    roles++;
                }
                member(account, role);
                rowPolicies += dropStale(state, role, plan);
            }
            catch (ClickHouseFailure refused) {
                if (refused.code() == ClickHouseFailure.UNREACHABLE) {
                    throw refused;
                }
                LOG.warning("ClickHouse refused the GrantForge role of user " + account.name() + ": " + refused.getMessage());
                applied.remove(role);
                failed++;
            }
        }

        for (ServerState.RowPolicyKey key : state.rowPolicies().keySet()) {
            if (!wanted.contains(key) && !kept.contains(key.role())) {
                dropRowPolicy(key);
                rowPolicies++;
            }
        }
        if (!changedRoles.isEmpty()) {
            Map<String, String> now = ServerState.privileges(clickhouse, prefix);
            for (String role : changedRoles) {
                Applied last = applied.get(role);
                if (last != null) {
                    applied.put(role, new Applied(last.statement(), now.get(role)));
                }
            }
        }
        return new Outcome(planned.size(), roles, dropped, rowPolicies, failed);
    }

    /** Grants the role to its user and makes it a default role, unless it is both. */
    private void member(Account account, String role)
    {
        String user = quote(account.name());
        if (!account.granted().contains(role)) {
            clickhouse.execute("GRANT " + quote(role) + " TO " + user);
        }
        if (account.defaultAll() && account.defaultExcept().contains(role)) {
            List<String> except = account.defaultExcept().stream().filter(other -> !other.equals(role)).toList();
            clickhouse.execute("SET DEFAULT ROLE ALL" + (except.isEmpty() ? "" : " EXCEPT " + quoted(except)) + " TO " + user);
        }
        else if (!account.defaultAll() && !account.defaultRoles().contains(role)) {
            List<String> defaults = new ArrayList<>(account.defaultRoles());
            defaults.add(role);
            clickhouse.execute("SET DEFAULT ROLE " + quoted(defaults) + " TO " + user);
        }
    }

    /**
     * Puts a row filter in place unless it is.
     *
     * @return whether the row policy was created or replaced
     */
    private boolean filter(ServerState state, String role, TableRef table, Plan.Filter filter)
    {
        ServerState.RowPolicyKey key = new ServerState.RowPolicyKey(role, table);
        ServerState.RowPolicy existing = state.rowPolicies().get(key);
        AppliedFilter last = filters.get(key);
        if (existing != null && existing.onlyFor(role) && last != null && last.expression().equals(filter.expression())
                && last.shown().equals(existing.filter())) {
            return false;
        }
        String shown;
        try {
            createRowPolicy(role, table, filter.expression());
            ServerState.RowPolicy created = rowPolicy(key);
            if (created != null && created.onlyFor(role)) {
                shown = created.filter();
            }
            else {
                LOG.severe("The row filter of GrantForge policy " + filter.policyId() + " for ClickHouse user " + role.substring(prefix.length())
                        + " on " + table + " changes whom ClickHouse's row policy applies to; it is replaced by one that shows no rows");
                shown = noRows(key);
            }
        }
        catch (ClickHouseFailure refused) {
            if (refused.code() == ClickHouseFailure.UNREACHABLE) {
                throw refused;
            }
            LOG.severe("ClickHouse refused the row filter of GrantForge policy " + filter.policyId() + " for user " + role.substring(prefix.length())
                    + " on " + table + ", so the user sees no rows of it: " + refused.getMessage());
            shown = noRows(key);
        }
        filters.put(key, new AppliedFilter(filter.expression(), shown));
        return true;
    }

    private String noRows(ServerState.RowPolicyKey key)
    {
        createRowPolicy(key.role(), key.table(), NO_ROWS);
        ServerState.RowPolicy created = rowPolicy(key);
        return created == null ? NO_ROWS : created.filter();
    }

    private void createRowPolicy(String role, TableRef table, String expression)
    {
        clickhouse.execute("CREATE ROW POLICY OR REPLACE " + quote(role) + " ON " + table.sql() + " AS PERMISSIVE FOR SELECT USING (" + expression
                + ") TO " + quote(role));
    }

    private ServerState.@Nullable RowPolicy rowPolicy(ServerState.RowPolicyKey key)
    {
        List<Row> rows = clickhouse.query("SELECT select_filter, is_restrictive, apply_to_all, apply_to_list, apply_to_except FROM system.row_policies"
                + " WHERE short_name = {name:String} AND database = {database:String} AND table = {table:String}",
                Map.of("name", key.role(), "database", key.table().database(), "table", key.table().table()));
        if (rows.isEmpty()) {
            return null;
        }
        Row row = rows.get(0);
        return new ServerState.RowPolicy(row.string("select_filter"), row.flag("is_restrictive"), row.flag("apply_to_all"), row.texts("apply_to_list"),
                row.texts("apply_to_except"));
    }

    /** Drops the role's row policies of tables its plan no longer filters, after its privileges no longer need them. */
    private int dropStale(ServerState state, String role, Plan plan)
    {
        int dropped = 0;
        for (ServerState.RowPolicyKey key : state.rowPolicies().keySet()) {
            if (key.role().equals(role) && !plan.filters().containsKey(key.table())) {
                dropRowPolicy(key);
                dropped++;
            }
        }
        return dropped;
    }

    private void dropRowPolicy(ServerState.RowPolicyKey key)
    {
        clickhouse.execute("DROP ROW POLICY IF EXISTS " + quote(key.role()) + " ON " + key.table().sql());
        filters.remove(key);
    }

    private static String quote(String name)
    {
        return SQLUtils.enquoteIdentifier(name);
    }

    private static String quoted(List<String> names)
    {
        return names.stream().map(Reconciler::quote).collect(Collectors.joining(", "));
    }
}
