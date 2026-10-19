// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import io.trino.spi.QueryId;
import io.trino.spi.connector.CatalogSchemaName;
import io.trino.spi.connector.CatalogSchemaRoutineName;
import io.trino.spi.connector.CatalogSchemaTableName;
import io.trino.spi.connector.ColumnSchema;
import io.trino.spi.connector.EntityKindAndName;
import io.trino.spi.connector.EntityPrivilege;
import io.trino.spi.connector.SchemaTableName;
import io.trino.spi.function.SchemaFunctionName;
import io.trino.spi.security.Identity;
import io.trino.spi.security.Privilege;
import io.trino.spi.security.SystemAccessControl;
import io.trino.spi.security.SystemSecurityContext;
import io.trino.spi.security.TrinoPrincipal;
import io.trino.spi.security.ViewExpression;

import java.security.Principal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Trino's system access control in GrantForge's terms. Every check Trino asks becomes a decision on a catalog, schema,
 * table, column, routine, session property, user or the system information, and is reported; listings keep what a user
 * may use; row filters and column masks are the service's. Anyone may run a query, and see and kill their own; Trino's
 * own privilege and role statements are refused, as GrantForge manages them.
 */
// Later SPIs deprecate the checks Trino 470 to 478 call, which this class answers for every line.
@SuppressWarnings({"deprecation", "removal"})
public class GrantForgeSystemAccessControl
        implements SystemAccessControl
{
    private final TrinoAccess access;

    /**
     * Creates the access control.
     *
     * @param access the agent's checks
     */
    public GrantForgeSystemAccessControl(TrinoAccess access)
    {
        this.access = access;
    }

    /**
     * Returns the agent's checks, for the access controls of later Trino lines.
     *
     * @return them
     */
    protected final TrinoAccess access()
    {
        return access;
    }

    /**
     * Returns who asks within a query.
     *
     * @param context the query's context
     * @return the caller
     */
    protected static Caller caller(SystemSecurityContext context)
    {
        Identity identity = context.getIdentity();
        return new Caller(identity.getUser(), identity.getGroups(), context.getQueryId().getId());
    }

    private static Caller caller(Identity identity)
    {
        return new Caller(identity.getUser(), identity.getGroups(), null);
    }

    /**
     * Names a table as GrantForge does.
     *
     * @param table the table
     * @return the resource
     */
    protected static TrinoResource table(CatalogSchemaTableName table)
    {
        return TrinoResource.table(table.getCatalogName(), table.getSchemaTableName().getSchemaName(), table.getSchemaTableName().getTableName());
    }

    private static TrinoResource schema(CatalogSchemaName schema)
    {
        return TrinoResource.schema(schema.getCatalogName(), schema.getSchemaName());
    }

    private static TrinoResource function(CatalogSchemaRoutineName routine)
    {
        return TrinoResource.function(routine.getCatalogName(), routine.getSchemaRoutineName().getSchemaName(),
                routine.getSchemaRoutineName().getRoutineName());
    }

    /**
     * Checks one access type on a table.
     *
     * @param context the query's context
     * @param action what Trino checks
     * @param table the table
     * @param accessType the access type
     */
    protected final void checkTable(SystemSecurityContext context, String action, CatalogSchemaTableName table, String accessType)
    {
        access.check(caller(context), action, table(table), accessType);
    }

    /**
     * Checks that a table may be altered and the name it takes created.
     *
     * @param context the query's context
     * @param action what Trino checks
     * @param table the table
     * @param name its new name
     */
    private void checkRename(SystemSecurityContext context, String action, CatalogSchemaTableName table, CatalogSchemaTableName name)
    {
        checkTable(context, action, table, TrinoAccess.ALTER);
        checkTable(context, action, name, TrinoAccess.CREATE);
    }

    /**
     * Returns the refusal of one of Trino's own privilege or role statements.
     *
     * @param context the query's context
     * @param action the statement
     * @param what what it names
     * @return the exception to throw
     */
    protected final RuntimeException managed(SystemSecurityContext context, String action, Object what)
    {
        return access.managed(caller(context), action, String.valueOf(what));
    }

    // Queries, users and the system

    @Override
    public void checkCanImpersonateUser(Identity identity, String userName)
    {
        access.check(caller(identity), "impersonate user", TrinoResource.user(userName), TrinoAccess.IMPERSONATE);
    }

    /** Trino checks impersonation with {@link #checkCanImpersonateUser}; this older check lets every session through to it. */
    @Override
    public void checkCanSetUser(Optional<Principal> principal, String userName)
    {
        // Impersonation is decided by checkCanImpersonateUser.
    }

    @Override
    public void checkCanExecuteQuery(Identity identity, QueryId queryId)
    {
        // Anyone may run a query: what it reads and writes is checked.
    }

    @Override
    public void checkCanViewQueryOwnedBy(Identity identity, Identity queryOwner)
    {
        access.checkQueryOwner(caller(identity), queryOwner.getUser(), TrinoAccess.VIEW_QUERY);
    }

    @Override
    public Collection<Identity> filterViewQueryOwnedBy(Identity identity, Collection<Identity> queryOwners)
    {
        return access.filterQueryOwners(caller(identity), queryOwners, Identity::getUser);
    }

    @Override
    public void checkCanKillQueryOwnedBy(Identity identity, Identity queryOwner)
    {
        access.checkQueryOwner(caller(identity), queryOwner.getUser(), TrinoAccess.KILL_QUERY);
    }

    @Override
    public void checkCanReadSystemInformation(Identity identity)
    {
        access.check(caller(identity), "read system information", TrinoResource.systemInformation(), TrinoAccess.READ);
    }

    @Override
    public void checkCanWriteSystemInformation(Identity identity)
    {
        access.check(caller(identity), "write system information", TrinoResource.systemInformation(), TrinoAccess.WRITE);
    }

    @Override
    public void checkCanSetSystemSessionProperty(Identity identity, QueryId queryId, String propertyName)
    {
        access.check(new Caller(identity.getUser(), identity.getGroups(), queryId.getId()), "set system session property",
                TrinoResource.systemSessionProperty(propertyName), TrinoAccess.SET);
    }

    @Override
    public void checkCanSetCatalogSessionProperty(SystemSecurityContext context, String catalogName, String propertyName)
    {
        access.check(caller(context), "set catalog session property", TrinoResource.sessionProperty(catalogName, propertyName), TrinoAccess.SET);
    }

    // Catalogs

    @Override
    public boolean canAccessCatalog(SystemSecurityContext context, String catalogName)
    {
        return access.canAccessCatalog(caller(context), catalogName);
    }

    @Override
    public void checkCanCreateCatalog(SystemSecurityContext context, String catalog)
    {
        access.check(caller(context), "create catalog", TrinoResource.catalog(catalog), TrinoAccess.CREATE);
    }

    @Override
    public void checkCanDropCatalog(SystemSecurityContext context, String catalog)
    {
        access.check(caller(context), "drop catalog", TrinoResource.catalog(catalog), TrinoAccess.DROP);
    }

    @Override
    public Set<String> filterCatalogs(SystemSecurityContext context, Set<String> catalogs)
    {
        return access.filterCatalogs(caller(context), catalogs);
    }

    @Override
    public void checkCanShowSchemas(SystemSecurityContext context, String catalogName)
    {
        access.checkVisible(caller(context), "show schemas", TrinoResource.catalog(catalogName));
    }

    // Schemas

    @Override
    public void checkCanCreateSchema(SystemSecurityContext context, CatalogSchemaName schema, Map<String, Object> properties)
    {
        access.check(caller(context), "create schema", schema(schema), TrinoAccess.CREATE);
    }

    @Override
    public void checkCanDropSchema(SystemSecurityContext context, CatalogSchemaName schema)
    {
        access.check(caller(context), "drop schema", schema(schema), TrinoAccess.DROP);
    }

    @Override
    public void checkCanRenameSchema(SystemSecurityContext context, CatalogSchemaName schema, String newSchemaName)
    {
        access.check(caller(context), "rename schema", schema(schema), TrinoAccess.ALTER);
        access.check(caller(context), "rename schema", TrinoResource.schema(schema.getCatalogName(), newSchemaName), TrinoAccess.CREATE);
    }

    @Override
    public void checkCanSetSchemaAuthorization(SystemSecurityContext context, CatalogSchemaName schema, TrinoPrincipal principal)
    {
        access.check(caller(context), "set schema authorization", schema(schema), TrinoAccess.ALTER);
    }

    @Override
    public void checkCanShowCreateSchema(SystemSecurityContext context, CatalogSchemaName schemaName)
    {
        access.checkVisible(caller(context), "show create schema", schema(schemaName));
    }

    @Override
    public Set<String> filterSchemas(SystemSecurityContext context, String catalogName, Set<String> schemaNames)
    {
        return access.filterSchemas(caller(context), catalogName, schemaNames);
    }

    @Override
    public void checkCanShowTables(SystemSecurityContext context, CatalogSchemaName schema)
    {
        access.checkVisible(caller(context), "show tables", schema(schema));
    }

    @Override
    public void checkCanShowFunctions(SystemSecurityContext context, CatalogSchemaName schema)
    {
        access.checkVisible(caller(context), "show functions", schema(schema));
    }

    // Tables, views and materialized views

    @Override
    public Set<SchemaTableName> filterTables(SystemSecurityContext context, String catalogName, Set<SchemaTableName> tableNames)
    {
        return access.filterTables(caller(context), catalogName, tableNames, SchemaTableName::getSchemaName, SchemaTableName::getTableName);
    }

    @Override
    public void checkCanShowCreateTable(SystemSecurityContext context, CatalogSchemaTableName table)
    {
        access.checkVisible(caller(context), "show create table", table(table));
    }

    @Override
    public void checkCanShowColumns(SystemSecurityContext context, CatalogSchemaTableName table)
    {
        access.checkVisible(caller(context), "show columns", table(table));
    }

    @Override
    public Set<String> filterColumns(SystemSecurityContext context, CatalogSchemaTableName table, Set<String> columns)
    {
        return access.filterColumns(caller(context), table.getCatalogName(), table.getSchemaTableName().getSchemaName(),
                table.getSchemaTableName().getTableName(), columns);
    }

    @Override
    public void checkCanCreateTable(SystemSecurityContext context, CatalogSchemaTableName table, Map<String, Object> properties)
    {
        checkTable(context, "create table", table, TrinoAccess.CREATE);
    }

    @Override
    public void checkCanDropTable(SystemSecurityContext context, CatalogSchemaTableName table)
    {
        checkTable(context, "drop table", table, TrinoAccess.DROP);
    }

    @Override
    public void checkCanRenameTable(SystemSecurityContext context, CatalogSchemaTableName table, CatalogSchemaTableName newTable)
    {
        checkRename(context, "rename table", table, newTable);
    }

    @Override
    public void checkCanSetTableProperties(SystemSecurityContext context, CatalogSchemaTableName table, Map<String, Optional<Object>> properties)
    {
        checkTable(context, "set table properties", table, TrinoAccess.ALTER);
    }

    @Override
    public void checkCanSetTableComment(SystemSecurityContext context, CatalogSchemaTableName table)
    {
        checkTable(context, "comment on table", table, TrinoAccess.ALTER);
    }

    @Override
    public void checkCanSetViewComment(SystemSecurityContext context, CatalogSchemaTableName view)
    {
        checkTable(context, "comment on view", view, TrinoAccess.ALTER);
    }

    @Override
    public void checkCanSetColumnComment(SystemSecurityContext context, CatalogSchemaTableName table)
    {
        checkTable(context, "comment on column", table, TrinoAccess.ALTER);
    }

    @Override
    public void checkCanAddColumn(SystemSecurityContext context, CatalogSchemaTableName table)
    {
        checkTable(context, "add column", table, TrinoAccess.ALTER);
    }

    @Override
    public void checkCanAlterColumn(SystemSecurityContext context, CatalogSchemaTableName table)
    {
        checkTable(context, "alter column", table, TrinoAccess.ALTER);
    }

    @Override
    public void checkCanDropColumn(SystemSecurityContext context, CatalogSchemaTableName table)
    {
        checkTable(context, "drop column", table, TrinoAccess.ALTER);
    }

    @Override
    public void checkCanRenameColumn(SystemSecurityContext context, CatalogSchemaTableName table)
    {
        checkTable(context, "rename column", table, TrinoAccess.ALTER);
    }

    @Override
    public void checkCanSetTableAuthorization(SystemSecurityContext context, CatalogSchemaTableName table, TrinoPrincipal principal)
    {
        checkTable(context, "set table authorization", table, TrinoAccess.ALTER);
    }

    @Override
    public void checkCanSelectFromColumns(SystemSecurityContext context, CatalogSchemaTableName table, Set<String> columns)
    {
        SchemaTableName name = table.getSchemaTableName();
        access.checkColumns(caller(context), "select", table.getCatalogName(), name.getSchemaName(), name.getTableName(), columns, TrinoAccess.SELECT);
    }

    @Override
    public void checkCanInsertIntoTable(SystemSecurityContext context, CatalogSchemaTableName table)
    {
        checkTable(context, "insert", table, TrinoAccess.INSERT);
    }

    @Override
    public void checkCanDeleteFromTable(SystemSecurityContext context, CatalogSchemaTableName table)
    {
        checkTable(context, "delete", table, TrinoAccess.DELETE);
    }

    @Override
    public void checkCanTruncateTable(SystemSecurityContext context, CatalogSchemaTableName table)
    {
        checkTable(context, "truncate table", table, TrinoAccess.DELETE);
    }

    @Override
    public void checkCanUpdateTableColumns(SystemSecurityContext context, CatalogSchemaTableName table, Set<String> updatedColumnNames)
    {
        SchemaTableName name = table.getSchemaTableName();
        access.checkColumns(caller(context), "update", table.getCatalogName(), name.getSchemaName(), name.getTableName(), updatedColumnNames,
                TrinoAccess.UPDATE);
    }

    @Override
    public void checkCanCreateView(SystemSecurityContext context, CatalogSchemaTableName view)
    {
        checkTable(context, "create view", view, TrinoAccess.CREATE);
    }

    @Override
    public void checkCanRenameView(SystemSecurityContext context, CatalogSchemaTableName view, CatalogSchemaTableName newView)
    {
        checkRename(context, "rename view", view, newView);
    }

    @Override
    public void checkCanSetViewAuthorization(SystemSecurityContext context, CatalogSchemaTableName view, TrinoPrincipal principal)
    {
        checkTable(context, "set view authorization", view, TrinoAccess.ALTER);
    }

    @Override
    public void checkCanDropView(SystemSecurityContext context, CatalogSchemaTableName view)
    {
        checkTable(context, "drop view", view, TrinoAccess.DROP);
    }

    /** A view whose owner runs it reads these columns for whoever queries it, so the owner must be able to read them. */
    @Override
    public void checkCanCreateViewWithSelectFromColumns(SystemSecurityContext context, CatalogSchemaTableName table, Set<String> columns)
    {
        SchemaTableName name = table.getSchemaTableName();
        access.checkColumns(caller(context), "create view with select", table.getCatalogName(), name.getSchemaName(), name.getTableName(), columns,
                TrinoAccess.SELECT);
    }

    @Override
    public void checkCanCreateMaterializedView(SystemSecurityContext context, CatalogSchemaTableName materializedView, Map<String, Object> properties)
    {
        checkTable(context, "create materialized view", materializedView, TrinoAccess.CREATE);
    }

    /** Refreshing a materialized view writes its rows anew. */
    @Override
    public void checkCanRefreshMaterializedView(SystemSecurityContext context, CatalogSchemaTableName materializedView)
    {
        checkTable(context, "refresh materialized view", materializedView, TrinoAccess.INSERT);
    }

    @Override
    public void checkCanSetMaterializedViewProperties(SystemSecurityContext context, CatalogSchemaTableName materializedView,
            Map<String, Optional<Object>> properties)
    {
        checkTable(context, "set materialized view properties", materializedView, TrinoAccess.ALTER);
    }

    @Override
    public void checkCanDropMaterializedView(SystemSecurityContext context, CatalogSchemaTableName materializedView)
    {
        checkTable(context, "drop materialized view", materializedView, TrinoAccess.DROP);
    }

    @Override
    public void checkCanRenameMaterializedView(SystemSecurityContext context, CatalogSchemaTableName view, CatalogSchemaTableName newView)
    {
        checkRename(context, "rename materialized view", view, newView);
    }

    /** A table procedure, such as {@code ALTER TABLE ... EXECUTE optimize}, changes how the table is stored. */
    @Override
    public void checkCanExecuteTableProcedure(SystemSecurityContext context, CatalogSchemaTableName table, String procedure)
    {
        checkTable(context, "execute table procedure " + procedure, table, TrinoAccess.ALTER);
    }

    @Override
    public List<ViewExpression> getRowFilters(SystemSecurityContext context, CatalogSchemaTableName tableName)
    {
        SchemaTableName name = tableName.getSchemaTableName();
        String condition = access.rowFilter(caller(context), tableName.getCatalogName(), name.getSchemaName(), name.getTableName());
        return condition == null ? List.of() : List.of(expression(tableName, condition));
    }

    @Override
    public Map<ColumnSchema, ViewExpression> getColumnMasks(SystemSecurityContext context, CatalogSchemaTableName tableName, List<ColumnSchema> columns)
    {
        Caller caller = caller(context);
        SchemaTableName name = tableName.getSchemaTableName();
        Map<ColumnSchema, ViewExpression> masks = new LinkedHashMap<>();
        for (ColumnSchema column : columns) {
            String masked = access.mask(caller, tableName.getCatalogName(), name.getSchemaName(), name.getTableName(), column.getName(),
                    column.getType().getDisplayName());
            if (masked != null) {
                masks.put(column, expression(tableName, masked));
            }
        }
        return masks;
    }

    /** An expression Trino evaluates for the querying user, with the table's catalog and schema for the names it uses. */
    private static ViewExpression expression(CatalogSchemaTableName table, String expression)
    {
        return ViewExpression.builder().catalog(table.getCatalogName()).schema(table.getSchemaTableName().getSchemaName()).expression(expression)
                .build();
    }

    // Routines

    @Override
    public boolean canExecuteFunction(SystemSecurityContext context, CatalogSchemaRoutineName functionName)
    {
        return access.allows(caller(context), "execute function", function(functionName), TrinoAccess.EXECUTE);
    }

    /** A view whose owner runs it runs these functions for whoever queries it, so the owner must be able to run them. */
    @Override
    public boolean canCreateViewWithExecuteFunction(SystemSecurityContext context, CatalogSchemaRoutineName functionName)
    {
        return access.allows(caller(context), "create view with function", function(functionName), TrinoAccess.EXECUTE);
    }

    @Override
    public void checkCanCreateFunction(SystemSecurityContext context, CatalogSchemaRoutineName functionName)
    {
        access.check(caller(context), "create function", function(functionName), TrinoAccess.CREATE_FUNCTION);
    }

    @Override
    public void checkCanDropFunction(SystemSecurityContext context, CatalogSchemaRoutineName functionName)
    {
        access.check(caller(context), "drop function", function(functionName), TrinoAccess.DROP_FUNCTION);
    }

    @Override
    public void checkCanShowCreateFunction(SystemSecurityContext context, CatalogSchemaRoutineName functionName)
    {
        access.checkVisible(caller(context), "show create function", function(functionName));
    }

    @Override
    public Set<SchemaFunctionName> filterFunctions(SystemSecurityContext context, String catalogName, Set<SchemaFunctionName> functionNames)
    {
        return access.filterFunctions(caller(context), catalogName, functionNames, FunctionNames::schema, FunctionNames::function);
    }

    @Override
    public void checkCanExecuteProcedure(SystemSecurityContext context, CatalogSchemaRoutineName procedure)
    {
        access.check(caller(context), "call procedure", TrinoResource.procedure(procedure.getCatalogName(),
                procedure.getSchemaRoutineName().getSchemaName(), procedure.getSchemaRoutineName().getRoutineName()), TrinoAccess.EXECUTE);
    }

    // Privileges and roles, which GrantForge manages

    @Override
    public void checkCanGrantSchemaPrivilege(SystemSecurityContext context, Privilege privilege, CatalogSchemaName schema, TrinoPrincipal grantee,
            boolean grantOption)
    {
        throw managed(context, "grant schema privilege", schema);
    }

    @Override
    public void checkCanDenySchemaPrivilege(SystemSecurityContext context, Privilege privilege, CatalogSchemaName schema, TrinoPrincipal grantee)
    {
        throw managed(context, "deny schema privilege", schema);
    }

    @Override
    public void checkCanRevokeSchemaPrivilege(SystemSecurityContext context, Privilege privilege, CatalogSchemaName schema, TrinoPrincipal revokee,
            boolean grantOption)
    {
        throw managed(context, "revoke schema privilege", schema);
    }

    @Override
    public void checkCanGrantTablePrivilege(SystemSecurityContext context, Privilege privilege, CatalogSchemaTableName table, TrinoPrincipal grantee,
            boolean grantOption)
    {
        throw managed(context, "grant table privilege", table);
    }

    @Override
    public void checkCanDenyTablePrivilege(SystemSecurityContext context, Privilege privilege, CatalogSchemaTableName table, TrinoPrincipal grantee)
    {
        throw managed(context, "deny table privilege", table);
    }

    @Override
    public void checkCanRevokeTablePrivilege(SystemSecurityContext context, Privilege privilege, CatalogSchemaTableName table,
            TrinoPrincipal revokee, boolean grantOption)
    {
        throw managed(context, "revoke table privilege", table);
    }

    @Override
    public void checkCanGrantEntityPrivilege(SystemSecurityContext context, EntityPrivilege privilege, EntityKindAndName entity,
            TrinoPrincipal grantee, boolean grantOption)
    {
        throw managed(context, "grant privilege", entity.name());
    }

    @Override
    public void checkCanDenyEntityPrivilege(SystemSecurityContext context, EntityPrivilege privilege, EntityKindAndName entity, TrinoPrincipal grantee)
    {
        throw managed(context, "deny privilege", entity.name());
    }

    @Override
    public void checkCanRevokeEntityPrivilege(SystemSecurityContext context, EntityPrivilege privilege, EntityKindAndName entity,
            TrinoPrincipal revokee, boolean grantOption)
    {
        throw managed(context, "revoke privilege", entity.name());
    }

    @Override
    public void checkCanCreateRole(SystemSecurityContext context, String role, Optional<TrinoPrincipal> grantor)
    {
        throw managed(context, "create role", role);
    }

    @Override
    public void checkCanDropRole(SystemSecurityContext context, String role)
    {
        throw managed(context, "drop role", role);
    }

    @Override
    public void checkCanGrantRoles(SystemSecurityContext context, Set<String> roles, Set<TrinoPrincipal> grantees, boolean adminOption,
            Optional<TrinoPrincipal> grantor)
    {
        throw managed(context, "grant roles", roles);
    }

    @Override
    public void checkCanRevokeRoles(SystemSecurityContext context, Set<String> roles, Set<TrinoPrincipal> grantees, boolean adminOption,
            Optional<TrinoPrincipal> grantor)
    {
        throw managed(context, "revoke roles", roles);
    }

    @Override
    public void checkCanShowRoles(SystemSecurityContext context)
    {
        // Trino's roles are only shown: GrantForge manages none of them.
    }

    @Override
    public void checkCanShowCurrentRoles(SystemSecurityContext context)
    {
        // As checkCanShowRoles.
    }

    @Override
    public void checkCanShowRoleGrants(SystemSecurityContext context)
    {
        // As checkCanShowRoles.
    }

    /** Sends the folded access events and stops the agent with the coordinator. */
    @Override
    public void shutdown()
    {
        access.runtime().close();
    }

    @Override
    public String toString()
    {
        return "GrantForge";
    }
}
