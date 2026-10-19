// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.DataMask;
import org.devlive.grantforge.agent.RowFilter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

/**
 * What a Trino coordinator asks of GrantForge, in Trino's terms but without its SPI, so that the agents of every Trino
 * line decide alike. Checks are decided against the service's policies and reported as access events; listings keep
 * what a user may use; queries of other users are seen and killed under policy, one's own always; row filters and
 * column masks come from the service's policies. A service that is not in use leaves Trino unchecked, but for Trino's
 * own privilege and role statements, which are refused whenever GrantForge is the access control.
 *
 * <p>What Trino needs to describe data to anyone stays open, its rows filtered by the same policies: every catalog's
 * {@code information_schema}, the {@code system} catalog's {@code jdbc} and {@code metadata} schemas, which drivers read,
 * and Trino's built-in functions. Privileges and roles are GrantForge's to manage, so Trino's {@code GRANT},
 * {@code REVOKE}, {@code DENY} and role statements are refused.
 */
public final class TrinoAccess
{
    public static final String SELECT = "select";
    public static final String INSERT = "insert";
    public static final String UPDATE = "update";
    public static final String DELETE = "delete";
    public static final String CREATE = "create";
    public static final String DROP = "drop";
    public static final String ALTER = "alter";
    public static final String EXECUTE = "execute";
    public static final String CREATE_FUNCTION = "create_function";
    public static final String DROP_FUNCTION = "drop_function";
    public static final String SET = "set";
    public static final String IMPERSONATE = "impersonate";
    public static final String VIEW_QUERY = "view_query";
    public static final String KILL_QUERY = "kill_query";
    public static final String READ = "read";
    public static final String WRITE = "write";

    /** Why Trino's own privilege and role statements are refused. */
    static final String MANAGED = "GrantForge manages privileges and roles; change them in GrantForge";

    static final String SYSTEM_CATALOG = "system";
    static final String INFORMATION_SCHEMA = "information_schema";
    static final String BUILTIN = "builtin";
    private static final Set<String> SYSTEM_METADATA = Set.of("jdbc", "metadata");

    /** What a user may do to a table, any of which shows it. */
    private static final String[] DATA = {SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER};
    /** What a user may do to a function. */
    private static final String[] ROUTINE = {EXECUTE, CREATE_FUNCTION, DROP_FUNCTION};
    /** What a user may do within a catalog or schema, any of which shows it. */
    private static final String[] WITHIN = {SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, EXECUTE, CREATE_FUNCTION, DROP_FUNCTION, SET};
    private static final int MAX_REQUEST = AccessEvent.MAX_REQUEST;

    private static final Logger LOG = Logger.getLogger(TrinoAccess.class.getName());

    /** Turns a refusal into the exception Trino reports. */
    @FunctionalInterface
    public interface Refusal
    {
        /**
         * Creates the exception.
         *
         * @param message why access is refused
         * @return the exception to throw
         */
        RuntimeException refuse(String message);
    }

    private final TrinoAgentRuntime runtime;
    private final Refusal refusal;

    /**
     * Creates the access checks of a running agent.
     *
     * @param runtime the agent
     * @param refusal makes Trino's access denied exception
     */
    public TrinoAccess(TrinoAgentRuntime runtime, Refusal refusal)
    {
        this.runtime = requireNonNull(runtime, "runtime");
        this.refusal = requireNonNull(refusal, "refusal");
    }

    /**
     * Returns the agent.
     *
     * @return it
     */
    public TrinoAgentRuntime runtime()
    {
        return runtime;
    }

    /**
     * Checks that a caller may do one of some access types to a resource, and reports it.
     *
     * @param caller who asks
     * @param action what Trino checks, such as {@code drop table}
     * @param resource the resource
     * @param accessTypes the access types, any of which suffices
     * @throws RuntimeException the refusal, when none is allowed
     */
    public void check(Caller caller, String action, TrinoResource resource, String... accessTypes)
    {
        String refused = decide(caller, action, resource, null, accessTypes);
        if (refused != null) {
            throw refusal.refuse(refused);
        }
    }

    /**
     * Returns whether a caller may do one of some access types to a resource, and reports it, for the checks Trino
     * words itself, such as running a function.
     *
     * @param caller who asks
     * @param action what Trino checks
     * @param resource the resource
     * @param accessTypes the access types, any of which suffices
     * @return {@code true} if one is allowed
     */
    public boolean allows(Caller caller, String action, TrinoResource resource, String... accessTypes)
    {
        return decide(caller, action, resource, null, accessTypes) == null;
    }

    /**
     * Checks that a caller may do something to some columns of a table, such as read them, and reports it as one event:
     * every column must be allowed, and the first refused one is named. A check of no columns is one of the table.
     *
     * @param caller who asks
     * @param action what Trino checks
     * @param catalog the catalog
     * @param schema the schema
     * @param table the table
     * @param columns the columns
     * @param accessType the access type
     * @throws RuntimeException the refusal, when a column is not allowed
     */
    public void checkColumns(Caller caller, String action, String catalog, String schema, String table, Collection<String> columns,
            String accessType)
    {
        TrinoResource whole = TrinoResource.table(catalog, schema, table);
        if (columns.isEmpty()) {
            check(caller, action, whole, accessType);
            return;
        }
        if (open(whole)) {
            return;
        }
        Policies policies = runtime.policies();
        if (!policies.enforced()) {
            return;
        }
        Set<String> sorted = new TreeSet<>(columns);
        Policies.Verdict last = null;
        for (String column : sorted) {
            TrinoResource resource = TrinoResource.column(catalog, schema, table, column);
            Policies.Verdict verdict = policies.decide(caller, resource, accessType);
            if (!verdict.allowed()) {
                report(caller, action, resource, verdict, null);
                throw refusal.refuse(message(caller, resource, verdict, policies, accessType));
            }
            last = verdict;
        }
        report(caller, action, whole, requireNonNull(last, "verdict"), "columns " + String.join(", ", sorted));
    }

    /**
     * Returns the refusal of one of Trino's own privilege or role statements, which GrantForge never lets through.
     *
     * @param caller who asks
     * @param action the statement, such as {@code grant table privilege}
     * @param what what it names
     * @return the exception to throw
     */
    public RuntimeException managed(Caller caller, String action, String what)
    {
        return refusal.refuse(caller.user() + " may not " + action + " on " + what + ": " + MANAGED);
    }

    /**
     * Checks that a caller may see a resource, to list what is in it or show how it was made: some permission on it or
     * within it does.
     *
     * @param caller who asks
     * @param action what Trino checks, such as {@code show tables}
     * @param resource the catalog, schema, table or function
     * @throws RuntimeException the refusal, when the caller may not see it
     */
    public void checkVisible(Caller caller, String action, TrinoResource resource)
    {
        if (!visible(caller, runtime.policies(), resource)) {
            throw refusal.refuse(caller.user() + " may not " + action + " in " + resource.display() + ": no GrantForge policy allows anything there");
        }
    }

    /**
     * Returns whether a caller may use a catalog at all.
     *
     * @param caller who asks
     * @param catalog the catalog
     * @return {@code true} if some permission in it allows something
     */
    public boolean canAccessCatalog(Caller caller, String catalog)
    {
        return visible(caller, runtime.policies(), TrinoResource.catalog(catalog));
    }

    /**
     * Keeps the catalogs a caller may use.
     *
     * @param caller who asks
     * @param catalogs the catalogs
     * @return those the caller may use
     */
    public Set<String> filterCatalogs(Caller caller, Set<String> catalogs)
    {
        Policies policies = runtime.policies();
        return keep(catalogs, catalog -> visible(caller, policies, TrinoResource.catalog(catalog)));
    }

    /**
     * Keeps the schemas of a catalog a caller may use.
     *
     * @param caller who asks
     * @param catalog the catalog
     * @param schemas the schemas
     * @return those the caller may use
     */
    public Set<String> filterSchemas(Caller caller, String catalog, Set<String> schemas)
    {
        Policies policies = runtime.policies();
        return keep(schemas, schema -> visible(caller, policies, TrinoResource.schema(catalog, schema)));
    }

    /**
     * Keeps the tables of a catalog a caller may use.
     *
     * @param caller who asks
     * @param catalog the catalog
     * @param tables the tables, in Trino's terms
     * @param schema the schema of a table
     * @param name the name of a table
     * @param <T> how Trino names a table
     * @return those the caller may use
     */
    public <T> Set<T> filterTables(Caller caller, String catalog, Set<T> tables, Function<T, String> schema, Function<T, String> name)
    {
        Policies policies = runtime.policies();
        return keep(tables, table -> visible(caller, policies, TrinoResource.table(catalog, schema.apply(table), name.apply(table))));
    }

    /**
     * Keeps the columns of a table a caller may use.
     *
     * @param caller who asks
     * @param catalog the catalog
     * @param schema the schema
     * @param table the table
     * @param columns the columns
     * @return those the caller may use
     */
    public Set<String> filterColumns(Caller caller, String catalog, String schema, String table, Set<String> columns)
    {
        Policies policies = runtime.policies();
        return keep(columns, column -> visible(caller, policies, TrinoResource.column(catalog, schema, table, column)));
    }

    /**
     * Keeps the functions of a catalog a caller may use.
     *
     * @param caller who asks
     * @param catalog the catalog
     * @param functions the functions, in Trino's terms
     * @param schema the schema of a function
     * @param name the name of a function
     * @param <T> how Trino names a function
     * @return those the caller may use
     */
    public <T> Set<T> filterFunctions(Caller caller, String catalog, Set<T> functions, Function<T, String> schema, Function<T, String> name)
    {
        Policies policies = runtime.policies();
        return keep(functions, function -> visible(caller, policies, TrinoResource.function(catalog, schema.apply(function), name.apply(function))));
    }

    /**
     * Checks that a caller may see or kill a query of a user: anyone may their own.
     *
     * @param caller who asks
     * @param owner the user who runs the query
     * @param accessType {@link #VIEW_QUERY} or {@link #KILL_QUERY}
     * @throws RuntimeException the refusal, when the caller may not
     */
    public void checkQueryOwner(Caller caller, String owner, String accessType)
    {
        if (!caller.user().equals(owner)) {
            check(caller, VIEW_QUERY.equals(accessType) ? "view query" : "kill query", TrinoResource.user(owner), accessType);
        }
    }

    /**
     * Keeps the users whose queries a caller may see: one's own, and those a policy allows.
     *
     * @param caller who asks
     * @param owners the users, in Trino's terms
     * @param user the name of a user
     * @param <T> how Trino names a user
     * @return those whose queries the caller may see
     */
    public <T> Collection<T> filterQueryOwners(Caller caller, Collection<T> owners, Function<T, String> user)
    {
        Policies policies = runtime.policies();
        if (!policies.enforced()) {
            return owners;
        }
        List<T> kept = new ArrayList<>();
        for (T owner : owners) {
            String name = user.apply(owner);
            if (caller.user().equals(name) || policies.decide(caller, TrinoResource.user(name), VIEW_QUERY).allowed()) {
                kept.add(owner);
            }
        }
        return kept;
    }

    /**
     * Returns the rows of a table a caller sees, as a Trino SQL condition on the table's columns.
     *
     * @param caller who reads
     * @param catalog the catalog
     * @param schema the schema
     * @param table the table
     * @return the condition, or {@code null} to show every row
     */
    public @Nullable String rowFilter(Caller caller, String catalog, String schema, String table)
    {
        TrinoResource resource = TrinoResource.table(catalog, schema, table);
        Policies policies = runtime.policies();
        if (open(resource) || !policies.enforced()) {
            return null;
        }
        try {
            RowFilter filter = policies.rowFilter(caller, resource);
            return filter == null ? null : filter.expression();
        }
        catch (RuntimeException broken) {
            LOG.log(Level.WARNING, "GrantForge cannot find the row filter of {0} for {1}, so no rows are shown: {2}",
                    new Object[] {resource.display(), caller.user(), broken.getMessage()});
            return "false";
        }
    }

    /**
     * Returns how a column is shown to a caller, as a Trino SQL expression of the column's type.
     *
     * @param caller who reads
     * @param catalog the catalog
     * @param schema the schema
     * @param table the table
     * @param column the column
     * @param type the column's type, as Trino writes it
     * @return the expression, or {@code null} to show the column as it is
     */
    public @Nullable String mask(Caller caller, String catalog, String schema, String table, String column, String type)
    {
        TrinoResource resource = TrinoResource.column(catalog, schema, table, column);
        Policies policies = runtime.policies();
        if (open(resource) || !policies.enforced()) {
            return null;
        }
        try {
            DataMask mask = policies.mask(caller, resource);
            return mask == null ? null : Masks.expression(mask, column, type);
        }
        catch (RuntimeException broken) {
            LOG.log(Level.WARNING, "GrantForge cannot find the mask of {0} for {1}, so it reads as null: {2}",
                    new Object[] {resource.display(), caller.user(), broken.getMessage()});
            return "CAST(NULL AS " + type + ")";
        }
    }

    /** Decides a check, reports it and returns the refusal, or {@code null} when it is allowed. */
    private @Nullable String decide(Caller caller, String action, TrinoResource resource, @Nullable String detail, String... accessTypes)
    {
        if (open(resource)) {
            return null;
        }
        Policies policies = runtime.policies();
        if (!policies.enforced()) {
            return null;
        }
        Policies.Verdict verdict = policies.decide(caller, resource, accessTypes);
        report(caller, action, resource, verdict, detail);
        return verdict.allowed() ? null : message(caller, resource, verdict, policies, accessTypes);
    }

    private void report(Caller caller, String action, TrinoResource resource, Policies.Verdict verdict, @Nullable String detail)
    {
        StringBuilder request = new StringBuilder();
        if (caller.query() != null) {
            request.append("query ").append(caller.query());
        }
        if (detail != null) {
            request.append(request.length() == 0 ? "" : "; ").append(detail);
        }
        String text = request.length() > MAX_REQUEST ? request.substring(0, MAX_REQUEST - 3) + "..." : request.toString();
        runtime.audit(AccessEvent.builder(caller.user(), resource.display(), verdict.accessType(), verdict.allowed()).decidedBy(verdict.decision())
                .enforcedByGrantForge().resourceType(resource.level()).action(action).request(text.isEmpty() ? null : text).build());
    }

    /** Words a refusal: what the user lacks on what, and whether a policy denied it, none allowed it, or none came yet. */
    private static String message(Caller caller, TrinoResource resource, Policies.Verdict verdict, Policies policies, String... accessTypes)
    {
        String lacking = caller.user() + " may not " + String.join(" or ", accessTypes) + " " + resource.display() + ": ";
        if (!policies.received()) {
            return lacking + "GrantForge has sent this coordinator no policies yet";
        }
        Long policy = verdict.decision().policyId();
        return lacking + (verdict.denied() && policy != null ? "GrantForge policy " + policy + " denies it" : "no GrantForge policy allows it");
    }

    /** Whether a caller may see a resource: what is always open, or some permission on it or within it. */
    private static boolean visible(Caller caller, Policies policies, TrinoResource resource)
    {
        if (open(resource) || !policies.enforced()) {
            return true;
        }
        String level = resource.level();
        if (TrinoResource.CATALOG.equals(level) && SYSTEM_CATALOG.equals(resource.value(TrinoResource.CATALOG))) {
            return true;
        }
        String[] types = switch (level) {
            case TrinoResource.CATALOG, TrinoResource.SCHEMA -> WITHIN;
            case TrinoResource.FUNCTION -> ROUTINE;
            default -> DATA;
        };
        return policies.uses(caller, resource, types);
    }

    /**
     * Returns whether a resource is open to everyone: the schemas that describe data, whose rows the listings filter,
     * and Trino's built-in functions.
     *
     * @param resource the resource
     * @return {@code true} if it is
     */
    static boolean open(TrinoResource resource)
    {
        String catalog = resource.value(TrinoResource.CATALOG);
        String schema = resource.value(TrinoResource.SCHEMA);
        if (catalog == null || schema == null) {
            return false;
        }
        if (INFORMATION_SCHEMA.equals(schema)) {
            return TrinoResource.TABLE.equals(resource.level()) || TrinoResource.COLUMN.equals(resource.level())
                    || TrinoResource.SCHEMA.equals(resource.level());
        }
        if (!SYSTEM_CATALOG.equals(catalog)) {
            return false;
        }
        if (TrinoResource.FUNCTION.equals(resource.level())) {
            return BUILTIN.equals(schema);
        }
        return SYSTEM_METADATA.contains(schema) && !TrinoResource.PROCEDURE.equals(resource.level());
    }

    private static <T> Set<T> keep(Set<T> values, Predicate<T> kept)
    {
        Set<T> result = new LinkedHashSet<>();
        for (T value : values) {
            if (kept.test(value)) {
                result.add(value);
            }
        }
        return result;
    }
}
