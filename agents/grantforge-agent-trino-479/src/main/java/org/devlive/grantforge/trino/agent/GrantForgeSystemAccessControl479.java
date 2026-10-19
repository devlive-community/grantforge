// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.trino.agent;

import io.trino.spi.connector.CatalogSchemaTableName;
import io.trino.spi.security.Privilege;
import io.trino.spi.security.SystemSecurityContext;
import io.trino.spi.security.TrinoPrincipal;

/**
 * The GrantForge access control of Trino 479 and later: that of Trino 470 to 478, and the checks Trino added since.
 * A table's branches are part of the table: listing them needs to see it, and creating, dropping or fast-forwarding one
 * alters it. Refreshing a view and changing a materialized view's owner or comment alter it too. Privileges on branches
 * are GrantForge's to manage, as all privileges are.
 */
public class GrantForgeSystemAccessControl479
        extends GrantForgeSystemAccessControl
{
    /**
     * Creates the access control.
     *
     * @param access the agent's checks
     */
    public GrantForgeSystemAccessControl479(TrinoAccess access)
    {
        super(access);
    }

    @Override
    public void checkCanShowBranches(SystemSecurityContext systemSecurityContext, CatalogSchemaTableName tableName)
    {
        access().checkVisible(caller(systemSecurityContext), "show branches", table(tableName));
    }

    @Override
    public void checkCanCreateBranch(SystemSecurityContext systemSecurityContext, CatalogSchemaTableName tableName, String branchName)
    {
        checkTable(systemSecurityContext, "create branch " + branchName, tableName, TrinoAccess.ALTER);
    }

    @Override
    public void checkCanDropBranch(SystemSecurityContext systemSecurityContext, CatalogSchemaTableName tableName, String branchName)
    {
        checkTable(systemSecurityContext, "drop branch " + branchName, tableName, TrinoAccess.ALTER);
    }

    @Override
    public void checkCanFastForwardBranch(SystemSecurityContext systemSecurityContext, CatalogSchemaTableName tableName, String sourceBranchName,
            String targetBranchName)
    {
        checkTable(systemSecurityContext, "fast-forward branch " + targetBranchName, tableName, TrinoAccess.ALTER);
    }

    @Override
    public void checkCanGrantTableBranchPrivilege(SystemSecurityContext context, Privilege privilege, CatalogSchemaTableName table, String branchName,
            TrinoPrincipal grantee, boolean grantOption)
    {
        throw managed(context, "grant branch privilege", table + "@" + branchName);
    }

    @Override
    public void checkCanDenyTableBranchPrivilege(SystemSecurityContext context, Privilege privilege, CatalogSchemaTableName table, String branchName,
            TrinoPrincipal grantee)
    {
        throw managed(context, "deny branch privilege", table + "@" + branchName);
    }

    @Override
    public void checkCanRevokeTableBranchPrivilege(SystemSecurityContext context, Privilege privilege, CatalogSchemaTableName table, String branchName,
            TrinoPrincipal revokee, boolean grantOption)
    {
        throw managed(context, "revoke branch privilege", table + "@" + branchName);
    }

    @Override
    public void checkCanRefreshView(SystemSecurityContext context, CatalogSchemaTableName viewName)
    {
        checkTable(context, "refresh view", viewName, TrinoAccess.ALTER);
    }

    /** Trino asks this for a materialized view's owner, also through {@link #checkCanSetEntityAuthorization}. */
    @Override
    @SuppressWarnings("removal")
    public void checkCanSetMaterializedViewAuthorization(SystemSecurityContext context, CatalogSchemaTableName view, TrinoPrincipal principal)
    {
        checkTable(context, "set materialized view authorization", view, TrinoAccess.ALTER);
    }

    /** Asked from Trino 484 on. */
    @Override
    public void checkCanSetMaterializedViewComment(SystemSecurityContext context, CatalogSchemaTableName materializedView)
    {
        checkTable(context, "comment on materialized view", materializedView, TrinoAccess.ALTER);
    }
}
