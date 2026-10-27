// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.devlive.grantforge.agent.sync.Notices;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Brings PostgreSQL in step with the plans: one role per user that holds exactly what the user's plan grants, of which
 * the user is a member, and one restrictive row security policy of that role per filtered table. Each database changes
 * in one transaction, so users see a policy version applied all at once or not at all. Nothing else is touched: the
 * agent's roles are those whose names start with its prefix and whose comment it wrote, and its row security policies
 * those named after its roles, with one more on each table where the agent turned row security on: a permissive policy
 * for everybody that shows every row, so that turning it on changes nothing for others. A row filter PostgreSQL
 * refuses, or one whose text would change whom the policy binds, is replaced by one that shows the user no rows.
 *
 * <p>Privileges are compared with what PostgreSQL shows each time, so a change made by hand to the agent's roles is
 * undone at the next sync; filters are compared with what the agent created last, as PostgreSQL writes conditions back
 * in its own words.
 */
final class Reconciler
{
    private static final Logger LOG = Logger.getLogger(Reconciler.class.getName());

    /** PostgreSQL's longest name, in bytes. */
    static final int MAX_NAME = 63;
    /** A filter that shows no rows, for a filter that cannot be applied as it is. */
    static final String NO_ROWS = "false";

    private final PostgreSql postgres;
    private final String home;
    private final String prefix;
    private final String everybody;
    private final Map<FilterKey, AppliedFilter> applied = new HashMap<>();
    private final Set<TableKey> turnedOn = new HashSet<>();
    private final Notices notices = new Notices(LOG);

    /** A row filter of a role on a table of a database. */
    private record FilterKey(String database, TableRef table, String role)
    {
    }

    /** A table of a database. */
    private record TableKey(String database, TableRef table)
    {
    }

    /** What the agent last made a row policy filter: the policy's condition, and the condition PostgreSQL showed afterwards. */
    private record AppliedFilter(String expression, @Nullable String shown)
    {
    }

    /**
     * What one sync changed.
     *
     * @param users the users planned
     * @param created the roles created
     * @param dropped the roles dropped
     * @param statements the statements that granted or revoked privileges
     * @param rowPolicies the row security policies created, replaced or dropped
     * @param failed the users and databases whose changes failed
     */
    record Outcome(int users, int created, int dropped, int statements, int rowPolicies, int failed)
    {
        boolean changed()
        {
            return created + dropped + statements + rowPolicies + failed > 0;
        }
    }

    Reconciler(PostgreSql postgres, String home, String prefix)
    {
        this.postgres = postgres;
        this.home = home;
        this.prefix = prefix;
        this.everybody = prefix.substring(0, prefix.length() - 1);
    }

    /**
     * Returns the role the agent keeps for a user: its name after the prefix, or, when that is longer than PostgreSQL's
     * names may be, as much of it as fits and a digest of the whole name.
     *
     * @param user the user name
     * @return the role's name
     */
    String role(String user)
    {
        String name = prefix + user;
        if (name.getBytes(StandardCharsets.UTF_8).length <= MAX_NAME) {
            return name;
        }
        String digest;
        try {
            digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(user.getBytes(StandardCharsets.UTF_8))).substring(0, 8);
        }
        catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("every Java platform has SHA-256", missing);
        }
        StringBuilder kept = new StringBuilder(MAX_NAME);
        int room = MAX_NAME - digest.length() - 1;
        for (int point : name.codePoints().toArray()) {
            int size = Character.toString(point).getBytes(StandardCharsets.UTF_8).length;
            if (size > room) {
                break;
            }
            kept.appendCodePoint(point);
            room -= size;
        }
        return kept + "_" + digest;
    }

    /**
     * Returns the name of the policy that keeps every row visible on a table where the agent turned row security on.
     *
     * @return the prefix without its last underscore, which no role of the agent's is named
     */
    String everybody()
    {
        return everybody;
    }

    /**
     * Brings PostgreSQL in step.
     *
     * @param state what the server holds
     * @param plans the plan of each user; {@code null} plans grant nothing to anybody
     * @return what changed
     * @throws PostgreSqlFailure when no server can be reached; changes made so far stay
     */
    // A counter per database and the sets of each role's changes are what the sync keeps track of.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    Outcome reconcile(ServerState state, @Nullable Function<Account, Plan> plans)
    {
        Map<String, Plan> planned = new LinkedHashMap<>();
        Set<String> frozen = new HashSet<>();
        int failed = 0;
        for (Account account : state.accounts()) {
            String role = role(account.name());
            try {
                Plan plan = plans == null ? Plan.NOTHING : plans.apply(account);
                for (String note : plan.notes()) {
                    notices.once(Level.WARNING, note);
                }
                if (!plan.empty()) {
                    planned.put(role, plan);
                }
            }
            catch (RuntimeException broken) {
                // The user's role stays as it is until its plan can be made.
                LOG.log(Level.WARNING, "The GrantForge policies of PostgreSQL user " + account.name() + " cannot be planned: " + broken.getMessage(),
                        broken);
                frozen.add(role);
                failed++;
            }
        }

        int created = 0;
        for (Account account : state.accounts()) {
            String role = role(account.name());
            if (!planned.containsKey(role)) {
                continue;
            }
            try {
                if (!state.roles().contains(role)) {
                    postgres.execute(home, "CREATE ROLE " + Sql.identifier(role) + " NOLOGIN");
                    postgres.execute(home, "COMMENT ON ROLE " + Sql.identifier(role) + " IS " + Sql.literal(ServerState.MARK + " of user "
                            + account.name()));
                    created++;
                }
                member(state, account, role);
            }
            catch (PostgreSqlFailure refused) {
                if (refused.unreachable()) {
                    throw refused;
                }
                LOG.warning("PostgreSQL refused the GrantForge role of user " + account.name() + ": " + refused.getMessage());
                planned.remove(role);
                frozen.add(role);
                failed++;
            }
        }

        Set<String> kept = new HashSet<>(planned.keySet());
        kept.addAll(frozen);
        Set<String> dropping = new TreeSet<>(state.roles());
        dropping.removeAll(kept);
        int statements = 0;
        int rowPolicies = 0;
        for (ServerState.DatabaseState database : state.databases().values()) {
            int[] changes = new int[2];
            try {
                postgres.transaction(database.name(), session -> {
                    changes[1] = filters(session, database, planned, frozen);
                    changes[0] = privileges(session, database, planned, dropping);
                });
                statements += changes[0];
                rowPolicies += changes[1];
            }
            catch (PostgreSqlFailure refused) {
                if (refused.unreachable()) {
                    throw refused;
                }
                LOG.warning("PostgreSQL refused GrantForge's changes in database " + database.name() + ", which stays as it was: " + refused.getMessage());
                applied.keySet().removeIf(key -> key.database().equals(database.name()));
                failed++;
            }
        }

        int dropped = 0;
        for (String role : dropping) {
            try {
                for (String database : state.databases().keySet()) {
                    postgres.execute(database, "DROP OWNED BY " + Sql.identifier(role));
                }
                postgres.execute(home, "DROP ROLE IF EXISTS " + Sql.identifier(role));
                dropped++;
            }
            catch (PostgreSqlFailure refused) {
                if (refused.unreachable()) {
                    throw refused;
                }
                LOG.warning("PostgreSQL refused to drop GrantForge role " + role + ": " + refused.getMessage());
                failed++;
            }
        }
        return new Outcome(state.accounts().size(), created, dropped, statements, rowPolicies, failed);
    }

    /** Makes the user a member of its role that has the role's privileges without SET ROLE, unless it is. */
    private void member(ServerState state, Account account, String role)
    {
        String grant = "GRANT " + Sql.identifier(role) + " TO " + Sql.identifier(account.name());
        boolean options = state.version() >= 160_000;
        if (!account.granted().contains(role)) {
            postgres.execute(home, options ? grant + " WITH INHERIT TRUE" : grant);
        }
        else if (!account.inheriting().contains(role)) {
            if (options) {
                postgres.execute(home, grant + " WITH INHERIT TRUE");
            }
            else {
                notices.once(Level.WARNING, "PostgreSQL user " + account.name() + " does not inherit the privileges of its roles (NOINHERIT), so what"
                        + " GrantForge gives it applies only after SET ROLE " + role);
            }
        }
    }

    /** Grants and revokes the privileges of the agent's roles in a database; returns how many statements ran. */
    // The privileges to revoke and to grant, per role.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private int privileges(PostgreSql.Session session, ServerState.DatabaseState database, Map<String, Plan> planned, Set<String> dropping)
    {
        int statements = 0;
        for (Map.Entry<String, Plan> entry : planned.entrySet()) {
            String role = entry.getKey();
            Set<Privilege> wanted = entry.getValue().privileges(database.name());
            Set<Privilege> held = database.held(role);
            Set<Privilege> revoked = new HashSet<>(held);
            revoked.removeAll(wanted);
            Set<Privilege> granted = new HashSet<>(wanted);
            granted.removeAll(held);
            statements += run(session, Privilege.statements(false, role, revoked));
            statements += run(session, Privilege.statements(true, role, granted));
        }
        for (String role : dropping) {
            statements += run(session, Privilege.statements(false, role, database.held(role)));
        }
        return statements;
    }

    private static int run(PostgreSql.Session session, List<String> statements)
    {
        statements.forEach(session::execute);
        return statements.size();
    }

    /** Puts the planned row filters of a database in place and drops the others; returns how many policies changed. */
    private int filters(PostgreSql.Session session, ServerState.DatabaseState database, Map<String, Plan> planned, Set<String> frozen)
    {
        Map<TableRef, Map<String, Plan.Filter>> wanted = new LinkedHashMap<>();
        planned.forEach((role, plan) -> plan.filters(database.name()).forEach((table, filter) ->
                wanted.computeIfAbsent(table, ignored -> new LinkedHashMap<>()).put(role, filter)));
        Set<TableRef> tables = new LinkedHashSet<>(wanted.keySet());
        database.security().forEach((table, security) -> {
            if (security.policies().keySet().stream().anyMatch(this::ours)) {
                tables.add(table);
            }
        });
        int changed = 0;
        for (TableRef table : tables) {
            changed += table(session, database.name(), table, database.security().getOrDefault(table, ServerState.Security.NONE),
                    wanted.getOrDefault(table, Map.of()), frozen);
        }
        return changed;
    }

    /** Whether a row security policy is the agent's: named after one of its roles, or the one for everybody. */
    private boolean ours(String policy)
    {
        return policy.equals(everybody) || policy.startsWith(prefix);
    }

    // A key per filter put in place.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private int table(PostgreSql.Session session, String database, TableRef table, ServerState.Security security, Map<String, Plan.Filter> wanted,
            Set<String> frozen)
    {
        TableKey key = new TableKey(database, table);
        boolean seesAll = security.policies().containsKey(everybody);
        int changed = 0;
        if (!wanted.isEmpty()) {
            if (!security.enabled() || !seesAll && turnedOn.contains(key)) {
                session.execute("ALTER TABLE " + table.sql() + " ENABLE ROW LEVEL SECURITY");
                if (!seesAll) {
                    session.execute("CREATE POLICY " + Sql.identifier(everybody) + " ON " + table.sql()
                            + " AS PERMISSIVE FOR ALL TO PUBLIC USING (true) WITH CHECK (true)");
                    changed++;
                }
                turnedOn.add(key);
            }
            for (Map.Entry<String, Plan.Filter> entry : wanted.entrySet()) {
                if (filter(session, database, table, entry.getKey(), entry.getValue(), security.policies().get(entry.getKey()))) {
                    changed++;
                }
            }
        }
        for (String policy : security.policies().keySet()) {
            if (ours(policy) && !policy.equals(everybody) && !wanted.containsKey(policy) && !frozen.contains(policy)) {
                session.execute("DROP POLICY " + Sql.identifier(policy) + " ON " + table.sql());
                applied.remove(new FilterKey(database, table, policy));
                changed++;
            }
        }
        boolean frozenFilters = security.policies().keySet().stream().anyMatch(frozen::contains);
        if (wanted.isEmpty() && seesAll && !frozenFilters) {
            long others = security.policies().keySet().stream().filter(policy -> !ours(policy)).count();
            if (others == 0) {
                session.execute("DROP POLICY " + Sql.identifier(everybody) + " ON " + table.sql());
                session.execute("ALTER TABLE " + table.sql() + " DISABLE ROW LEVEL SECURITY");
                turnedOn.remove(key);
                changed++;
            }
            else {
                notices.once(Level.INFO, "Table " + database + "." + table + " has row security policies of its own besides GrantForge's, so GrantForge"
                        + " leaves its row security on and its policy " + everybody + " in place");
            }
        }
        return changed;
    }

    /**
     * Puts a row filter of a role in place unless it is.
     *
     * @return whether the policy was created or replaced
     */
    private boolean filter(PostgreSql.Session session, String database, TableRef table, String role, Plan.Filter filter,
            ServerState.@Nullable RowPolicy existing)
    {
        FilterKey key = new FilterKey(database, table, role);
        AppliedFilter last = applied.get(key);
        if (existing != null && existing.filters(role) && last != null && last.expression().equals(filter.expression())
                && Objects.equals(last.shown(), existing.using())) {
            return false;
        }
        String drop = "DROP POLICY IF EXISTS " + Sql.identifier(role) + " ON " + table.sql();
        session.execute(drop);
        PostgreSqlFailure refused = session.attempt(create(role, table, filter.expression()));
        ServerState.RowPolicy created = refused == null ? policy(session, table, role) : null;
        if (refused != null) {
            LOG.severe("PostgreSQL refused the row filter of GrantForge policy " + filter.policyId() + " for role " + role + " on " + database + "."
                    + table + ", so its user sees no rows of it: " + refused.getMessage());
        }
        else if (created == null || !created.filters(role)) {
            LOG.severe("The row filter of GrantForge policy " + filter.policyId() + " for role " + role + " on " + database + "." + table
                    + " changes whom the row security policy binds; it is replaced by one that shows no rows");
            session.execute(drop);
        }
        if (refused != null || created == null || !created.filters(role)) {
            session.execute(create(role, table, NO_ROWS));
            created = policy(session, table, role);
        }
        applied.put(key, new AppliedFilter(filter.expression(), created == null ? null : created.using()));
        return true;
    }

    private static String create(String role, TableRef table, String expression)
    {
        return "CREATE POLICY " + Sql.identifier(role) + " ON " + table.sql() + " AS RESTRICTIVE FOR SELECT TO " + Sql.identifier(role)
                + " USING (" + expression + ")";
    }

    private static ServerState.@Nullable RowPolicy policy(PostgreSql.Session session, TableRef table, String name)
    {
        List<Row> rows = session.query("SELECT p.polpermissive AS permissive, p.polcmd::text AS command, ARRAY(SELECT COALESCE(r.rolname, '')"
                + " FROM unnest(p.polroles) AS o LEFT JOIN pg_roles r ON r.oid = o) AS roles, pg_get_expr(p.polqual, p.polrelid) AS using"
                + " FROM pg_policy p WHERE p.polrelid = ?::regclass AND p.polname = ?", table.sql(), name);
        if (rows.isEmpty()) {
            return null;
        }
        Row row = rows.get(0);
        return new ServerState.RowPolicy(row.flag("permissive"), row.string("command"), row.texts("roles"), row.text("using"));
    }
}
