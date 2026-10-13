// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.hive.agent;

import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.ql.parse.SemanticException;
import org.apache.hadoop.hive.ql.security.HiveAuthenticationProvider;
import org.apache.hadoop.hive.ql.security.authorization.plugin.AbstractHiveAuthorizer;
import org.apache.hadoop.hive.ql.security.authorization.plugin.DisallowTransformHook;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAccessControlException;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAuthzContext;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAuthzPluginException;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveAuthzSessionContext;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveOperationType;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrincipal;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrivilege;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrivilegeInfo;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HivePrivilegeObject;
import org.apache.hadoop.hive.ql.security.authorization.plugin.HiveRoleGrant;
import org.apache.hadoop.hive.ql.security.authorization.plugin.SettableConfigUpdater;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.DataMask;
import org.devlive.grantforge.agent.RowFilter;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * The authorizer of one HiveServer2 session, as Apache Ranger's: every statement's objects are checked against one
 * policy snapshot and the first refusal fails the statement; SHOW DATABASES and SHOW TABLES list only what the user may
 * use; queries see masked columns and filtered rows. Grants and roles are GrantForge's, so Hive's GRANT, REVOKE and
 * role statements are refused. Each decision is reported as an access event.
 *
 * <p>A service that is not in use leaves Hive unchecked, as it is without an authorizer; without a snapshot, or when
 * policies cannot be read, everything is refused.
 */
public final class GrantForgeHiveAuthorizer
        extends AbstractHiveAuthorizer
{
    private static final String MANAGED = " is managed in GrantForge, not in Hive";
    private static final String PRE_HOOKS = "hive.exec.pre.hooks";

    private final Supplier<Policies> sessions;
    private final Consumer<AccessEvent> audit;
    private final HiveAuthenticationProvider authenticator;
    private final @Nullable HiveAuthzSessionContext session;

    /**
     * Creates an authorizer.
     *
     * @param sessions the policies of one snapshot per statement
     * @param audit where access events go
     * @param authenticator who the session's user is
     * @param session the session, or {@code null} outside HiveServer2
     */
    public GrantForgeHiveAuthorizer(Supplier<Policies> sessions, Consumer<AccessEvent> audit, HiveAuthenticationProvider authenticator,
            @Nullable HiveAuthzSessionContext session)
    {
        this.sessions = requireNonNull(sessions, "sessions");
        this.audit = requireNonNull(audit, "audit");
        this.authenticator = requireNonNull(authenticator, "authenticator");
        this.session = session;
    }

    @Override
    public VERSION getVersion()
    {
        return VERSION.V1;
    }

    @Override
    public void checkPrivileges(HiveOperationType operation, @Nullable List<HivePrivilegeObject> inputs,
            @Nullable List<HivePrivilegeObject> outputs, @Nullable HiveAuthzContext context) throws HiveAuthzPluginException, HiveAccessControlException
    {
        String user = user();
        String name = operation.name();
        Policies policies = policies();
        if (!policies.enforced()) {
            return;
        }
        String clientIp = context == null ? null : context.getIpAddress();
        String command = context == null ? null : context.getCommandString();
        if ("DFS".equals(name)) {
            // As in Ranger: dfs commands reach the file system with HiveServer2's own rights.
            audit.accept(AccessEvent.builder(user, "dfs", "dfs", false).enforcedByGrantForge().clientIp(clientIp).action(name).request(command)
                    .build());
            throw new HiveAccessControlException("Permission denied: user [" + user + "] does not have privilege for [DFS] command");
        }
        Set<HiveCheck> checks = new LinkedHashSet<>();
        try {
            collect(name, inputs, true, checks);
            collect(name, outputs, false, checks);
            if (empty(inputs) && empty(outputs)) {
                checks.addAll(HiveAccess.withoutObjects(name));
            }
        }
        catch (IllegalArgumentException unnamed) {
            throw new HiveAccessControlException("Permission denied: " + unnamed.getMessage(), unnamed);
        }
        List<String> groups = groups();
        for (HiveCheck check : checks) {
            AgentDecision decision;
            boolean masked;
            try {
                decision = policies.decide(check.request(user, groups, clientIp));
                masked = decision.allowed() && HiveAccess.bypassesMasks(name, check) && masked(policies, check, user, groups, clientIp);
            }
            catch (RuntimeException broken) {
                throw new HiveAuthzPluginException("GrantForge cannot decide " + check + ": " + broken.getMessage(), broken);
            }
            boolean allowed = decision.allowed() && !masked;
            AccessEvent.Builder event = AccessEvent.builder(user, check.display(), check.accessType(), allowed).decidedBy(decision)
                    .clientIp(clientIp).resourceType(check.level()).action(name).request(command);
            if (allowed) {
                audit.accept(event.build());
                continue;
            }
            // Without a policy GrantForge refuses: it enforced the refusal either way.
            audit.accept(event.enforcedByGrantForge().build());
            String access = AccessRequest.ANY_ACCESS.equals(check.accessType()) ? "USE" : check.accessType().toUpperCase(Locale.ROOT);
            throw new HiveAccessControlException("Permission denied: user [" + user + "] does not have [" + access + "] privilege on ["
                    + check.display() + "]" + (masked ? ", whose values are masked or rows filtered for the user" : ""));
        }
    }

    private static boolean empty(@Nullable List<HivePrivilegeObject> objects)
    {
        return objects == null || objects.isEmpty();
    }

    private static void collect(String operation, @Nullable List<HivePrivilegeObject> objects, boolean input, Set<HiveCheck> checks)
    {
        if (objects != null) {
            for (HivePrivilegeObject object : objects) {
                checks.addAll(HiveAccess.checks(operation, object, input));
            }
        }
    }

    /** Whether the user sees the table of a check through a row filter or a mask on one of its columns. */
    private static boolean masked(Policies policies, HiveCheck check, String user, List<String> groups, @Nullable String clientIp)
    {
        List<String> table = check.table();
        HiveCheck read = new HiveCheck(HiveAccess.SELECT, HiveAccess.DATABASE, table.get(0), HiveAccess.TABLE, table.get(1));
        if (policies.rowFilter(read.request(user, groups, clientIp)) != null) {
            return true;
        }
        DataMask mask = policies.mask(read.builder(user, groups, clientIp).below(true).build());
        return mask != null && !"mask_none".equals(mask.maskType());
    }

    @Override
    public List<HivePrivilegeObject> filterListCmdObjects(@Nullable List<HivePrivilegeObject> objects, @Nullable HiveAuthzContext context)
            throws HiveAuthzPluginException, HiveAccessControlException
    {
        if (objects == null || objects.isEmpty()) {
            return objects == null ? Collections.<HivePrivilegeObject>emptyList() : objects;
        }
        String user = user();
        Policies policies = policies();
        if (!policies.enforced()) {
            return objects;
        }
        String clientIp = context == null ? null : context.getIpAddress();
        List<String> groups = groups();
        List<HivePrivilegeObject> visible = new ArrayList<>(objects.size());
        for (HivePrivilegeObject object : objects) {
            HiveCheck check = listed(object);
            try {
                if (check != null && policies.decide(check.request(user, groups, clientIp)).allowed()) {
                    visible.add(object);
                }
            }
            catch (RuntimeException broken) {
                throw new HiveAuthzPluginException("GrantForge cannot decide " + check + ": " + broken.getMessage(), broken);
            }
        }
        return visible;
    }

    /** The check of a listed database or table; other objects are not listed, as in Ranger. */
    private static @Nullable HiveCheck listed(HivePrivilegeObject object)
    {
        String type = object.getType() == null ? "" : object.getType().name();
        try {
            if ("DATABASE".equals(type)) {
                String database = object.getDbname();
                return new HiveCheck(AccessRequest.ANY_ACCESS, HiveAccess.DATABASE, database == null ? object.getObjectName() : database);
            }
            if ("TABLE_OR_VIEW".equals(type)) {
                return new HiveCheck(AccessRequest.ANY_ACCESS, HiveAccess.DATABASE, object.getDbname(), HiveAccess.TABLE, object.getObjectName());
            }
        }
        catch (IllegalArgumentException unnamed) {
            return null;
        }
        return null;
    }

    @Override
    public boolean needTransform()
    {
        return true;
    }

    @Override
    public List<HivePrivilegeObject> applyRowFilterAndColumnMasking(@Nullable HiveAuthzContext context, @Nullable List<HivePrivilegeObject> objects)
            throws SemanticException
    {
        List<HivePrivilegeObject> transformed = new ArrayList<>();
        if (objects == null || objects.isEmpty()) {
            return transformed;
        }
        String user;
        Policies policies;
        try {
            user = user();
            policies = policies();
        }
        catch (HiveAccessControlException | HiveAuthzPluginException unusable) {
            throw new SemanticException(unusable.getMessage(), unusable);
        }
        if (!policies.enforced()) {
            return transformed;
        }
        String clientIp = context == null ? null : context.getIpAddress();
        List<String> groups = groups();
        for (HivePrivilegeObject object : objects) {
            if (object.getType() != null && !"TABLE_OR_VIEW".equals(object.getType().name())) {
                continue;
            }
            try {
                if (transform(object, policies, user, groups, clientIp)) {
                    transformed.add(object);
                }
            }
            catch (RuntimeException broken) {
                throw new SemanticException("GrantForge cannot mask or filter " + object.getDbname() + "." + object.getObjectName() + ": "
                        + broken.getMessage(), broken);
            }
        }
        return transformed;
    }

    /** Sets the row filter and column transformers of a table; whether any applies. */
    // One request per column.
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static boolean transform(HivePrivilegeObject object, Policies policies, String user, List<String> groups, @Nullable String clientIp)
    {
        String database = object.getDbname();
        String table = object.getObjectName();
        boolean changed = false;
        RowFilter filter = policies.rowFilter(new HiveCheck(HiveAccess.SELECT, HiveAccess.DATABASE, database, HiveAccess.TABLE, table)
                .request(user, groups, clientIp));
        if (filter != null) {
            object.setRowFilterExpression(filter.expression());
            changed = true;
        }
        List<String> columns = object.getColumns();
        if (columns != null && !columns.isEmpty()) {
            List<String> transformers = new ArrayList<>(columns.size());
            for (String column : columns) {
                DataMask mask = policies.mask(new HiveCheck(HiveAccess.SELECT, HiveAccess.DATABASE, database, HiveAccess.TABLE, table,
                        HiveAccess.COLUMN, column).request(user, groups, clientIp));
                String quoted = quote(column);
                if (mask == null || "mask_none".equals(mask.maskType())) {
                    transformers.add(quoted);
                    continue;
                }
                transformers.add(expression(mask, quoted));
                changed = true;
            }
            object.setCellValueTransformers(transformers);
        }
        return changed;
    }

    /** A mask's expression; a mask the agent cannot write shows nothing, as Ranger shows a custom mask without a value. */
    private static String expression(DataMask mask, String column)
    {
        try {
            String expression = mask.expression(column);
            return expression == null ? "NULL" : expression;
        }
        catch (IllegalStateException valueless) {
            return "NULL";
        }
    }

    /**
     * Quotes a column for HiveQL.
     *
     * @param column the column
     * @return it in backticks
     */
    static String quote(String column)
    {
        return "`" + column.replace("`", "``") + "`";
    }

    @Override
    public void applyAuthorizationConfigPolicy(HiveConf conf) throws HiveAuthzPluginException
    {
        // As Hive's SQL standard authorization and Ranger do: no TRANSFORM scripts, and only safe settings for users.
        HiveAuthzSessionContext context = session;
        if (context != null && context.getClientType() == HiveAuthzSessionContext.CLIENT_TYPE.HIVESERVER2) {
            String hooks = conf.get(PRE_HOOKS, "").trim();
            String disallow = DisallowTransformHook.class.getName();
            conf.set(PRE_HOOKS, hooks.isEmpty() ? disallow : hooks + "," + disallow);
            SettableConfigUpdater.setHiveConfWhiteList(conf);
        }
    }

    @Override
    public List<String> getCurrentRoleNames() throws HiveAuthzPluginException
    {
        try {
            List<String> roles = new ArrayList<>(policies().rolesOf(user()));
            Collections.sort(roles);
            return roles;
        }
        catch (HiveAccessControlException unknown) {
            throw new HiveAuthzPluginException(unknown.getMessage(), unknown);
        }
    }

    private String user() throws HiveAccessControlException
    {
        String user = authenticator.getUserName();
        if (user == null || user.isEmpty()) {
            throw new HiveAccessControlException("Permission denied: the user of the session is not known");
        }
        return user;
    }

    private List<String> groups()
    {
        List<String> groups = authenticator.getGroupNames();
        return groups == null ? Collections.<String>emptyList() : groups;
    }

    private Policies policies() throws HiveAuthzPluginException
    {
        try {
            return requireNonNull(sessions.get(), "policies");
        }
        catch (RuntimeException unavailable) {
            throw new HiveAuthzPluginException("GrantForge policies are not available: " + unavailable.getMessage(), unavailable);
        }
    }

    @Override
    public void grantPrivileges(List<HivePrincipal> principals, List<HivePrivilege> privileges, HivePrivilegeObject object,
            HivePrincipal grantor, boolean grantOption) throws HiveAuthzPluginException
    {
        throw new HiveAuthzPluginException("GRANT" + MANAGED);
    }

    @Override
    public void revokePrivileges(List<HivePrincipal> principals, List<HivePrivilege> privileges, HivePrivilegeObject object,
            HivePrincipal grantor, boolean grantOption) throws HiveAuthzPluginException
    {
        throw new HiveAuthzPluginException("REVOKE" + MANAGED);
    }

    @Override
    public void createRole(String role, HivePrincipal grantor) throws HiveAuthzPluginException
    {
        throw new HiveAuthzPluginException("CREATE ROLE" + MANAGED);
    }

    @Override
    public void dropRole(String role) throws HiveAuthzPluginException
    {
        throw new HiveAuthzPluginException("DROP ROLE" + MANAGED);
    }

    @Override
    public List<HiveRoleGrant> getPrincipalGrantInfoForRole(String role) throws HiveAuthzPluginException
    {
        throw new HiveAuthzPluginException("SHOW PRINCIPALS" + MANAGED);
    }

    @Override
    public List<HiveRoleGrant> getRoleGrantInfoForPrincipal(HivePrincipal principal) throws HiveAuthzPluginException
    {
        throw new HiveAuthzPluginException("SHOW ROLE GRANT" + MANAGED);
    }

    @Override
    public void grantRole(List<HivePrincipal> principals, List<String> roles, boolean grantOption, HivePrincipal grantor)
            throws HiveAuthzPluginException
    {
        throw new HiveAuthzPluginException("GRANT ROLE" + MANAGED);
    }

    @Override
    public void revokeRole(List<HivePrincipal> principals, List<String> roles, boolean grantOption, HivePrincipal grantor)
            throws HiveAuthzPluginException
    {
        throw new HiveAuthzPluginException("REVOKE ROLE" + MANAGED);
    }

    @Override
    public List<String> getAllRoles() throws HiveAuthzPluginException
    {
        throw new HiveAuthzPluginException("SHOW ROLES" + MANAGED);
    }

    @Override
    public List<HivePrivilegeInfo> showPrivileges(HivePrincipal principal, HivePrivilegeObject object) throws HiveAuthzPluginException
    {
        throw new HiveAuthzPluginException("SHOW GRANT" + MANAGED);
    }

    @Override
    public void setCurrentRole(String role) throws HiveAuthzPluginException
    {
        throw new HiveAuthzPluginException("SET ROLE" + MANAGED + "; every role GrantForge gives the user is in effect");
    }
}
