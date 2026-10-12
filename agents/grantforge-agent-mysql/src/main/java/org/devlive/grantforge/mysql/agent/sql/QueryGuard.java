// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.SQLObjectImpl;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOperator;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.statement.SQLDeleteStatement;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLJoinTableSource;
import com.alibaba.druid.sql.ast.statement.SQLSelect;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSubqueryTableSource;
import com.alibaba.druid.sql.ast.statement.SQLTableSource;
import com.alibaba.druid.sql.ast.statement.SQLUpdateStatement;
import com.alibaba.druid.sql.parser.Lexer;
import com.alibaba.druid.sql.parser.ParserException;
import com.alibaba.druid.sql.parser.SQLExprParser;
import com.alibaba.druid.sql.parser.SQLParserFeature;
import com.alibaba.druid.sql.parser.SQLParserUtils;
import com.alibaba.druid.sql.parser.Token;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;
import org.devlive.grantforge.agent.AccessEvent;
import org.devlive.grantforge.agent.AgentDecision;
import org.devlive.grantforge.agent.DataMask;
import org.devlive.grantforge.agent.RowFilter;
import org.devlive.grantforge.policy.engine.AccessRequest;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Checks analyzed statements against the policies and applies masks and row filters. Every access a statement needs
 * must be allowed; a read of a table with masked columns or a row filter for the user becomes a derived table that
 * shows the masked values and only the filtered rows, under the table's own name, so the rest of the statement reads
 * it as before. UPDATE and DELETE change only the rows the filter shows and may not use masked columns; a view may not
 * read what masks or filters change, as MySQL runs it later without the proxy.
 */
public final class QueryGuard
{
    private final Catalog catalog;

    /**
     * Creates a guard.
     *
     * @param catalog the server's schema, for the columns a derived table lists
     */
    public QueryGuard(Catalog catalog)
    {
        this.catalog = requireNonNull(catalog, "catalog");
    }

    /**
     * Checks statements for a user.
     *
     * @param sql the client's text, for audit events
     * @param plans the statements, analyzed from that text
     * @param user the MySQL user the client signed in as
     * @param clientIp the client's address, or {@code null}
     * @param policies the policies
     * @param prepared whether the text is a prepared statement, whose parameters a rewrite must keep in order
     * @return the verdict
     */
    public Verdict check(String sql, List<StatementPlan> plans, String user, @Nullable String clientIp, Policies policies, boolean prepared)
    {
        if (!policies.enforced()) {
            return new Verdict(null, null, List.of());
        }
        List<AccessEvent> events = new ArrayList<>();
        for (StatementPlan plan : plans) {
            for (Access access : plan.accesses()) {
                AgentDecision decision = policies.decide(request(user, access.accessType(), access, clientIp));
                AccessEvent.Builder event = AccessEvent.builder(user, access.resource(), access.accessType(), decision.allowed())
                        .decidedBy(decision).clientIp(clientIp).resourceType(access.level()).action(plan.action()).request(sql);
                if (!decision.allowed()) {
                    // The proxy denies without a policy: GrantForge enforced it either way.
                    events.add(event.enforcedByGrantForge().build());
                    return new Verdict(null, denied(user, access), events);
                }
                events.add(event.build());
            }
        }
        try {
            boolean rewritten = false;
            for (StatementPlan plan : plans) {
                rewritten |= rewrite(plan, user, clientIp, policies, prepared);
            }
            return new Verdict(rewritten ? print(plans) : null, null, events);
        }
        catch (Refusal refusal) {
            return new Verdict(null, refusal, events);
        }
    }

    private static Refusal denied(String user, Access access)
    {
        String what = access.accessType().toUpperCase(Locale.ROOT);
        return switch (access.level()) {
            case "column" -> new Refusal(Refusal.COLUMN_DENIED, what + " command denied to user '" + user + "' for column '" + access.column()
                    + "' in table '" + access.table() + "' by GrantForge");
            case "table" -> new Refusal(Refusal.TABLE_DENIED, what + " command denied to user '" + user + "' for table '" + access.table()
                    + "' by GrantForge");
            default -> new Refusal(Refusal.DATABASE_DENIED, "Access denied for user '" + user + "' to database '" + access.database()
                    + "' by GrantForge");
        };
    }

    private static AccessRequest request(String user, String accessType, Access access, @Nullable String clientIp)
    {
        AccessRequest.Builder request = AccessRequest.builder(user, accessType).resource("database", access.database());
        if (access.table() != null) {
            request.resource("table", access.table());
        }
        if (access.column() != null) {
            request.resource("column", access.column());
        }
        if (clientIp != null) {
            request.context(Collections.singletonMap("clientAddress", clientIp));
        }
        return request.build();
    }

    private boolean rewrite(StatementPlan plan, String user, @Nullable String clientIp, Policies policies, boolean prepared) throws Refusal
    {
        boolean changed = false;
        for (TableName target : plan.targets()) {
            Restriction restriction = restriction(target, user, clientIp, policies, prepared);
            for (String column : plan.used(target)) {
                if (restriction.masks.containsKey(column)) {
                    throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge does not allow " + plan.action() + " to use the masked column "
                            + target + "." + column);
                }
            }
            if (restriction.filter != null) {
                if (!target.equals(plan.filtered())) {
                    throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge applies the row filter of " + target + " only to single-table "
                            + plan.action() + " statements");
                }
                limit(plan.statement(), restriction.filter);
                changed = true;
            }
        }
        if (plan.explain()) {
            return changed;
        }
        for (StatementPlan.Read read : plan.reads()) {
            Restriction restriction = restriction(read.table(), user, clientIp, policies, prepared);
            if (restriction.masks.isEmpty() && restriction.filter == null) {
                continue;
            }
            if (plan.view()) {
                throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge does not allow a view over " + read.table()
                        + ", whose masks or row filter MySQL would not apply when the view is read");
            }
            derive(plan.statement(), read, restriction);
            changed = true;
        }
        return changed;
    }

    /** The masks and row filter that apply to a user's reads of a table. */
    private record Restriction(List<String> columns, Map<String, SQLExpr> masks, @Nullable SQLExpr filter)
    {
    }

    private Restriction restriction(TableName table, String user, @Nullable String clientIp, Policies policies, boolean prepared)
            throws Refusal
    {
        List<String> columns = catalog.columns(table);
        Map<String, SQLExpr> masks = new LinkedHashMap<>();
        Access tableAccess = new Access(table.database(), table.table(), null, "select");
        RowFilter rowFilter = policies.rowFilter(request(user, "select", tableAccess, clientIp));
        SQLExpr filter = rowFilter == null ? null : expression(rowFilter.expression(), "row filter policy " + rowFilter.policyId(), prepared);
        if (columns == null) {
            if (filter != null) {
                throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge cannot apply the row filter of " + table + ": its columns are unknown");
            }
            return new Restriction(List.of(), masks, null);
        }
        for (String column : columns) {
            DataMask mask = policies.mask(request(user, "select", column(table, column), clientIp));
            if (mask == null) {
                continue;
            }
            String quoted = Identifiers.quote(column);
            String text;
            try {
                text = mask.expression(quoted);
            }
            catch (IllegalStateException incomplete) {
                throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge cannot mask " + table + "." + column + ": " + incomplete.getMessage(), incomplete);
            }
            if (text == null) {
                throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge cannot mask " + table + "." + column + ": mask type " + mask.maskType()
                        + " has no MySQL expression");
            }
            if (!text.equals(quoted)) {
                masks.put(column, expression(text, "masking policy " + mask.policyId(), prepared));
            }
        }
        return new Restriction(columns, masks, filter);
    }

    private static Access column(TableName table, String column)
    {
        return new Access(table.database(), table.table(), column, "select");
    }

    /** Parses a policy's expression on its own, so it cannot reach into the statement around it. */
    private static SQLExpr expression(String text, String source, boolean prepared) throws Refusal
    {
        if (prepared && text.indexOf('?') >= 0) {
            throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge cannot apply the " + source + " to a prepared statement: it contains ?");
        }
        try {
            SQLExprParser parser = SQLParserUtils.createExprParser(text, DbType.mysql);
            SQLExpr expr = parser.expr();
            if (parser.getLexer().token() != Token.EOF) {
                throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge cannot apply the " + source + ": it is more than one expression");
            }
            return expr;
        }
        catch (ParserException broken) {
            throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge cannot apply the " + source + ": " + broken.getMessage(), broken);
        }
    }

    /** Replaces a read of a table with a derived table of the same name that masks and filters. */
    private static void derive(SQLStatement statement, StatementPlan.Read read, Restriction restriction) throws Refusal
    {
        SQLExprTableSource source = read.source();
        SQLSelectQueryBlock query = new SQLSelectQueryBlock(DbType.mysql);
        for (String column : restriction.columns) {
            SQLExpr mask = restriction.masks.get(column);
            if (mask == null) {
                query.addSelectItem(name(column));
            }
            else {
                query.addSelectItem(mask.clone(), Identifiers.quote(column));
            }
        }
        SQLExprTableSource inner = source.clone();
        inner.setAlias(null);
        query.setFrom(inner);
        if (restriction.filter != null) {
            query.setWhere(restriction.filter.clone());
        }
        String alias = source.getAlias() != null ? source.getAlias() : Identifiers.quote(read.table().table());
        SQLSubqueryTableSource derived = new SQLSubqueryTableSource(new SQLSelect(query), alias);
        replace(source, derived);
        // db.table.column names the table, which the derived table now stands for under its alias.
        String unquoted = Identifiers.unquote(alias);
        statement.accept(new SQLASTVisitorAdapter()
        {
            @Override
            public boolean visit(SQLPropertyExpr x)
            {
                if (x.getOwner() instanceof SQLPropertyExpr table && table.getOwner() instanceof SQLIdentifierExpr database
                        && Identifiers.unquote(database.getName()).equalsIgnoreCase(read.table().database())
                        && Identifiers.unquote(table.getName()).equalsIgnoreCase(read.table().table())) {
                    x.setOwner(new SQLIdentifierExpr(Identifiers.quote(unquoted)));
                }
                return true;
            }
        });
    }

    private static SQLIdentifierExpr name(String column)
    {
        return new SQLIdentifierExpr(Identifiers.quote(column));
    }

    // The same node, not an equal one, is replaced where it stands.
    @SuppressWarnings({"PMD.CompareObjectsWithEquals", "ReferenceEquality"})
    private static void replace(SQLTableSource old, SQLTableSource replacement) throws Refusal
    {
        SQLObject parent = old.getParent();
        if (parent instanceof SQLSelectQueryBlock block && block.getFrom() == old) {
            block.setFrom(replacement);
        }
        else if (parent instanceof SQLJoinTableSource join && join.getLeft() == old) {
            join.setLeft(replacement);
        }
        else if (parent instanceof SQLJoinTableSource join && join.getRight() == old) {
            join.setRight(replacement);
        }
        else if (parent instanceof SQLUpdateStatement update && update.getTableSource() == old) {
            update.setTableSource(replacement);
        }
        else if (parent instanceof SQLDeleteStatement delete && delete.getFrom() == old) {
            delete.setFrom(replacement);
        }
        else if (parent instanceof SQLDeleteStatement delete && delete.getUsing() == old) {
            delete.setUsing(replacement);
        }
        else {
            throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge cannot apply masks or row filters to " + old + " where it stands");
        }
    }

    /** Adds a row filter to the WHERE of a single-table UPDATE or DELETE. */
    private static void limit(SQLStatement statement, SQLExpr filter) throws Refusal
    {
        if (statement instanceof SQLUpdateStatement update) {
            update.setWhere(and(filter.clone(), update.getWhere()));
        }
        else if (statement instanceof SQLDeleteStatement delete) {
            delete.setWhere(and(filter.clone(), delete.getWhere()));
        }
        else {
            throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge cannot apply a row filter to this statement");
        }
    }

    private static SQLExpr and(SQLExpr filter, @Nullable SQLExpr where)
    {
        return where == null ? filter : new SQLBinaryOpExpr(filter, SQLBinaryOperator.BooleanAnd, where, DbType.mysql);
    }

    private static void uncomment(SQLStatement statement)
    {
        statement.accept(new SQLASTVisitorAdapter()
        {
            @Override
            public void preVisit(SQLObject x)
            {
                if (x instanceof SQLObjectImpl node) {
                    if (node.getBeforeCommentsDirect() != null) {
                        node.getBeforeCommentsDirect().clear();
                    }
                    if (node.getAfterCommentsDirect() != null) {
                        node.getAfterCommentsDirect().clear();
                    }
                }
            }
        });
    }

    /**
     * Prints the rewritten statements without the comments they carried: Druid prints a comment where it was attached,
     * and a line comment there could hide the masks or the filter. The text is read again to make sure none is left.
     */
    private static String print(List<StatementPlan> plans) throws Refusal
    {
        StringBuilder sql = new StringBuilder();
        for (StatementPlan plan : plans) {
            SQLStatement statement = plan.statement();
            uncomment(statement);
            if (!sql.isEmpty()) {
                sql.append(";\n");
            }
            sql.append(SQLUtils.toSQLString(statement, DbType.mysql));
        }
        String text = sql.toString();
        Lexer lexer = SQLParserUtils.createLexer(text, DbType.mysql);
        lexer.config(SQLParserFeature.KeepComments, true);
        for (lexer.nextToken(); lexer.token() != Token.EOF; lexer.nextToken()) {
            if (lexer.token() == Token.ERROR) {
                throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge could not rewrite the statement safely");
            }
        }
        List<String> comments = lexer.readAndResetComments();
        if (comments != null && !comments.isEmpty()) {
            throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge could not rewrite the statement without its comments");
        }
        return text;
    }
}
