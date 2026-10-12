// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.mysql.agent.sql;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLName;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLAllColumnExpr;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.ast.statement.SQLAlterTableStatement;
import com.alibaba.druid.sql.ast.statement.SQLAlterViewStatement;
import com.alibaba.druid.sql.ast.statement.SQLAssignItem;
import com.alibaba.druid.sql.ast.statement.SQLBeginStatement;
import com.alibaba.druid.sql.ast.statement.SQLCommitStatement;
import com.alibaba.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.druid.sql.ast.statement.SQLCreateIndexStatement;
import com.alibaba.druid.sql.ast.statement.SQLCreateRoleStatement;
import com.alibaba.druid.sql.ast.statement.SQLCreateTableStatement;
import com.alibaba.druid.sql.ast.statement.SQLCreateViewStatement;
import com.alibaba.druid.sql.ast.statement.SQLDeleteStatement;
import com.alibaba.druid.sql.ast.statement.SQLDropDatabaseStatement;
import com.alibaba.druid.sql.ast.statement.SQLDropIndexStatement;
import com.alibaba.druid.sql.ast.statement.SQLDropRoleStatement;
import com.alibaba.druid.sql.ast.statement.SQLDropTableStatement;
import com.alibaba.druid.sql.ast.statement.SQLDropUserStatement;
import com.alibaba.druid.sql.ast.statement.SQLDropViewStatement;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLGrantStatement;
import com.alibaba.druid.sql.ast.statement.SQLJoinTableSource;
import com.alibaba.druid.sql.ast.statement.SQLReleaseSavePointStatement;
import com.alibaba.druid.sql.ast.statement.SQLRenameUserStatement;
import com.alibaba.druid.sql.ast.statement.SQLReplaceStatement;
import com.alibaba.druid.sql.ast.statement.SQLRevokeStatement;
import com.alibaba.druid.sql.ast.statement.SQLRollbackStatement;
import com.alibaba.druid.sql.ast.statement.SQLSavePointStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectItem;
import com.alibaba.druid.sql.ast.statement.SQLSelectQueryBlock;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLSetStatement;
import com.alibaba.druid.sql.ast.statement.SQLStartTransactionStatement;
import com.alibaba.druid.sql.ast.statement.SQLTableSource;
import com.alibaba.druid.sql.ast.statement.SQLTruncateStatement;
import com.alibaba.druid.sql.ast.statement.SQLUpdateSetItem;
import com.alibaba.druid.sql.ast.statement.SQLUpdateStatement;
import com.alibaba.druid.sql.ast.statement.SQLUseStatement;
import com.alibaba.druid.sql.ast.statement.SQLWithSubqueryClause;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlAlterUserStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlAnalyzeStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlCheckTableStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlChecksumTableStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlCreateUserStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlDeleteStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlExplainStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlInsertStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlKillStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlLoadDataInFileStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlLockTableStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlOptimizeStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlRenameTableStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlSetTransactionStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlUnlockTablesStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlXAStatement;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import com.alibaba.druid.sql.parser.ParserException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Finds what MySQL statements need, with Alibaba Druid's MySQL parser: the databases, tables and columns each one uses
 * with the access types MySQL itself would check, and the tables it reads, where masks and row filters go.
 *
 * <p>Column names are checked conservatively: a column a statement names without a table counts against every table
 * of its query block and the blocks around it that has such a column, and a {@code *} against every column. A statement
 * the analyzer cannot tell everything about is refused rather than passed: executable comments, stored routines, which
 * MySQL runs as their definer, SQL-level prepared statements, HANDLER, and anything the parser does not read.
 */
public final class SqlAnalyzer
{
    private final Catalog catalog;

    /**
     * Creates an analyzer.
     *
     * @param catalog the server's schema
     */
    public SqlAnalyzer(Catalog catalog)
    {
        this.catalog = requireNonNull(catalog, "catalog");
    }

    /**
     * Analyzes what a client sent, which may be several statements.
     *
     * @param sql the text
     * @param database the session's current database, or {@code null}
     * @return one plan per statement, in order
     * @throws Refusal when a statement may not reach the server as it is
     */
    public List<StatementPlan> analyze(String sql, @Nullable String database) throws Refusal
    {
        // MySQL runs what /*! ... */ holds as if it were not a comment, which a parser that skips comments cannot see.
        if (sql.contains("/*!")) {
            throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge does not allow executable comments (/*! ... */)");
        }
        List<SQLStatement> statements;
        try {
            statements = SQLUtils.parseStatements(sql, DbType.mysql);
        }
        catch (ParserException | IllegalArgumentException | UnsupportedOperationException unreadable) {
            throw new Refusal(Refusal.CANNOT_PARSE, "GrantForge cannot check this statement: " + firstLine(unreadable.getMessage()), unreadable);
        }
        if (statements.size() > 1 && statements.stream().anyMatch(SQLUseStatement.class::isInstance)) {
            throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge does not allow USE together with other statements; send it alone");
        }
        List<StatementPlan> plans = new ArrayList<>();
        for (SQLStatement statement : statements) {
            plans.add(plan(statement, database));
        }
        // The server reads the statements after a new character set in it; the proxy read them in the old one.
        if (plans.size() > 1 && plans.stream().anyMatch(plan -> plan.charset() != null)) {
            throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge does not allow SET NAMES together with other statements; send it alone");
        }
        return plans;
    }

    private StatementPlan plan(SQLStatement statement, @Nullable String database) throws Refusal
    {
        Scan scan = new Scan(database);
        StatementPlan plan;
        if (statement instanceof SQLSelectStatement) {
            plan = new StatementPlan(statement, "SELECT");
            scan.reads(statement);
        }
        else if (statement instanceof MySqlInsertStatement insert) {
            plan = new StatementPlan(statement, "INSERT");
            insert(plan, scan, insert.getTableSource(), insert.getColumns(), insert.getDuplicateKeyUpdate());
            scan.reads(insert.getQuery());
            insert.getValuesList().forEach(scan::reads);
        }
        else if (statement instanceof SQLReplaceStatement replace) {
            plan = new StatementPlan(statement, "REPLACE");
            insert(plan, scan, replace.getTableSource(), replace.getColumns(), List.of());
            plan.access(table(scan.name(replace.getTableSource()), "delete"));
            scan.reads(replace.getQuery());
            replace.getValuesList().forEach(scan::reads);
        }
        else if (statement instanceof SQLUpdateStatement update) {
            plan = update(statement, scan, update);
        }
        else if (statement instanceof SQLDeleteStatement delete) {
            plan = delete(statement, scan, delete);
        }
        else {
            plan = other(statement, scan);
        }
        scan.finish(plan);
        return plan;
    }

    private void insert(StatementPlan plan, Scan scan, SQLExprTableSource target, List<SQLExpr> columns, List<SQLExpr> duplicates)
            throws Refusal
    {
        TableName table = scan.name(target);
        scan.target(target, table, false);
        if (columns.isEmpty()) {
            all(plan, table, "insert");
        }
        for (SQLExpr column : columns) {
            plan.access(column(table, columnName(column), "insert"));
        }
        for (SQLExpr duplicate : duplicates) {
            if (duplicate instanceof SQLBinaryOpExpr assignment) {
                plan.access(column(table, columnName(assignment.getLeft()), "update"));
                scan.reads(assignment.getRight());
            }
            else {
                scan.reads(duplicate);
            }
        }
    }

    private StatementPlan update(SQLStatement statement, Scan scan, SQLUpdateStatement update) throws Refusal
    {
        StatementPlan plan = new StatementPlan(statement, "UPDATE");
        SQLTableSource from = update.getTableSource();
        List<Source> sources = scan.statementSources(from);
        boolean single = from instanceof SQLExprTableSource;
        for (SQLUpdateSetItem item : update.getItems()) {
            SQLExpr column = item.getColumn();
            Source owner = scan.owner(column, sources);
            if (owner == null || owner.table == null) {
                throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge cannot tell which table UPDATE sets " + column + " of");
            }
            String name = scan.actual(owner.table, columnName(column));
            plan.access(column(owner.table, name, "update"));
            plan.target(owner.table);
            scan.markTarget(owner);
            scan.reads(item.getValue());
        }
        scan.readsBesides(from);
        scan.reads(update.getWhere());
        scan.reads(update.getOrderBy());
        if (single && plan.targets().size() == 1) {
            plan.filtered(plan.targets().iterator().next());
        }
        return plan;
    }

    private StatementPlan delete(SQLStatement statement, Scan scan, SQLDeleteStatement delete) throws Refusal
    {
        StatementPlan plan = new StatementPlan(statement, "DELETE");
        SQLTableSource from = delete.getFrom() != null ? delete.getFrom() : delete.getTableSource();
        if (delete.getUsing() != null) {
            from = delete.getUsing();
        }
        List<Source> sources = scan.statementSources(from);
        List<SQLExprTableSource> named = new ArrayList<>();
        if (delete.getFrom() != null || delete.getUsing() != null) {
            collectNamed(delete.getTableSource(), named);
        }
        else if (delete.getTableSource() instanceof SQLExprTableSource only) {
            named.add(only);
        }
        else {
            throw new Refusal(Refusal.CANNOT_PARSE, "GrantForge cannot tell which table this DELETE changes");
        }
        for (SQLExprTableSource target : named) {
            Source owner = scan.alias(target, sources);
            if (owner == null || owner.table == null) {
                throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge cannot tell which table DELETE removes rows of: " + target);
            }
            plan.access(table(owner.table, "delete"));
            plan.target(owner.table);
            scan.markTarget(owner);
        }
        scan.readsBesides(from);
        scan.reads(delete.getWhere());
        if (delete instanceof MySqlDeleteStatement mysql) {
            scan.reads(mysql.getOrderBy());
        }
        if (named.size() == 1 && from instanceof SQLExprTableSource) {
            plan.filtered(plan.targets().iterator().next());
        }
        return plan;
    }

    private static void collectNamed(SQLTableSource source, List<SQLExprTableSource> named)
    {
        if (source instanceof SQLExprTableSource table) {
            named.add(table);
        }
        else if (source instanceof SQLJoinTableSource join) {
            collectNamed(join.getLeft(), named);
            collectNamed(join.getRight(), named);
        }
    }

    // One branch per kind of statement MySQL has; splitting it would only scatter the list.
    private StatementPlan other(SQLStatement statement, Scan scan) throws Refusal
    {
        if (statement instanceof SQLCreateTableStatement create) {
            StatementPlan plan = new StatementPlan(statement, "CREATE TABLE");
            plan.access(table(scan.name(create.getTableSource()), "create"));
            if (create.getLike() != null) {
                plan.access(table(scan.name(create.getLike()), "select"));
            }
            scan.reads(create.getSelect());
            return plan;
        }
        if (statement instanceof SQLCreateViewStatement view) {
            StatementPlan plan = new StatementPlan(statement, "CREATE VIEW");
            plan.access(table(scan.name(view.getTableSource()), "create"));
            plan.view(true);
            scan.reads(view.getSubQuery());
            return plan;
        }
        if (statement instanceof SQLAlterViewStatement view) {
            StatementPlan plan = new StatementPlan(statement, "ALTER VIEW");
            plan.access(table(scan.name(view.getTableSource()), "alter"));
            plan.view(true);
            scan.reads(view.getSubQuery());
            return plan;
        }
        if (statement instanceof SQLDropTableStatement drop) {
            return each(statement, "DROP TABLE", drop.getTableSources(), "drop", scan);
        }
        if (statement instanceof SQLDropViewStatement drop) {
            return each(statement, "DROP VIEW", drop.getTableSources(), "drop", scan);
        }
        if (statement instanceof SQLTruncateStatement truncate) {
            return each(statement, "TRUNCATE", truncate.getTableSources(), "drop", scan);
        }
        if (statement instanceof SQLAlterTableStatement alter) {
            return each(statement, "ALTER TABLE", List.of(alter.getTableSource()), "alter", scan);
        }
        if (statement instanceof MySqlRenameTableStatement rename) {
            StatementPlan plan = new StatementPlan(statement, "RENAME TABLE");
            // MySQL asks ALTER and DROP of the old name, CREATE and INSERT of the new one.
            for (MySqlRenameTableStatement.Item item : rename.getItems()) {
                TableName from = scan.name(item.getName());
                TableName to = scan.name(item.getTo());
                plan.access(table(from, "alter"));
                plan.access(table(from, "drop"));
                plan.access(table(to, "create"));
                plan.access(table(to, "insert"));
            }
            return plan;
        }
        if (statement instanceof SQLCreateIndexStatement index) {
            SQLTableSource table = index.getTable();
            if (!(table instanceof SQLExprTableSource named)) {
                throw new Refusal(Refusal.CANNOT_PARSE, "GrantForge cannot tell which table CREATE INDEX changes");
            }
            return each(statement, "CREATE INDEX", List.of(named), "index", scan);
        }
        if (statement instanceof SQLDropIndexStatement index) {
            return each(statement, "DROP INDEX", List.of(index.getTableName()), "index", scan);
        }
        if (statement instanceof SQLCreateDatabaseStatement create) {
            return database(statement, "CREATE DATABASE", create.getName(), "create");
        }
        if (statement instanceof SQLDropDatabaseStatement drop) {
            return database(statement, "DROP DATABASE", drop.getDatabase(), "drop");
        }
        if (statement instanceof MySqlLoadDataInFileStatement load) {
            StatementPlan plan = new StatementPlan(statement, "LOAD DATA");
            TableName table = scan.name(load.getTableName());
            if (load.getColumns().isEmpty()) {
                all(plan, table, "insert");
            }
            for (SQLExpr column : load.getColumns()) {
                plan.access(column(table, columnName(column), "insert"));
            }
            if (load.isReplicate()) {
                plan.access(table(table, "delete"));
            }
            return plan;
        }
        if (statement instanceof MySqlLockTableStatement lock) {
            List<SQLExprTableSource> tables = new ArrayList<>();
            lock.getItems().forEach(item -> tables.add(item.getTableSource()));
            return each(statement, "LOCK TABLES", tables, "select", scan);
        }
        if (statement instanceof MySqlAnalyzeStatement analyze) {
            return each(statement, "ANALYZE TABLE", analyze.getTableSources(), "select", scan);
        }
        if (statement instanceof MySqlCheckTableStatement check) {
            return each(statement, "CHECK TABLE", check.getTables(), "select", scan);
        }
        if (statement instanceof MySqlChecksumTableStatement checksum) {
            return each(statement, "CHECKSUM TABLE", checksum.getTables(), "select", scan);
        }
        if (statement instanceof MySqlOptimizeStatement optimize) {
            return each(statement, "OPTIMIZE TABLE", optimize.getTableSources(), "alter", scan);
        }
        if (statement instanceof MySqlExplainStatement explain) {
            if (explain.getStatement() == null) {
                // DESCRIBE of a table shows its definition, which MySQL's own grants guard.
                return new StatementPlan(statement, "DESCRIBE");
            }
            StatementPlan inner = plan(explain.getStatement(), scan.database);
            StatementPlan plan = new StatementPlan(statement, "EXPLAIN");
            inner.accesses().forEach(plan::access);
            plan.explain(true);
            return plan;
        }
        if (statement instanceof SQLUseStatement use) {
            StatementPlan plan = new StatementPlan(statement, "USE");
            plan.database(fold(Identifiers.unquote(use.getDatabase().getSimpleName())));
            return plan;
        }
        if (statement instanceof SQLSetStatement set) {
            StatementPlan plan = new StatementPlan(statement, "SET");
            for (SQLAssignItem item : set.getItems()) {
                String charset = charset(item);
                if (charset != null) {
                    plan.charset(charset);
                }
                scan.reads(item.getValue());
            }
            return plan;
        }
        if (passed(statement)) {
            return new StatementPlan(statement, action(statement));
        }
        throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge does not allow " + action(statement)
                + " statements: it cannot check what they read and write");
    }

    /**
     * Statements that use no data, which MySQL's own grants decide: transactions, sessions, accounts and SHOW. Stored
     * routines, triggers, events and SQL-level prepared statements are not among them: MySQL runs those later, out of
     * the proxy's sight.
     */
    private static boolean passed(SQLStatement statement)
    {
        String name = statement.getClass().getSimpleName();
        return statement instanceof SQLBeginStatement || statement instanceof SQLStartTransactionStatement
                || statement instanceof SQLCommitStatement || statement instanceof SQLRollbackStatement
                || statement instanceof SQLSavePointStatement || statement instanceof SQLReleaseSavePointStatement
                || statement instanceof MySqlSetTransactionStatement || statement instanceof MySqlUnlockTablesStatement
                || statement instanceof MySqlXAStatement || statement instanceof MySqlKillStatement
                || statement instanceof SQLGrantStatement || statement instanceof SQLRevokeStatement
                || statement instanceof MySqlCreateUserStatement || statement instanceof SQLDropUserStatement
                || statement instanceof MySqlAlterUserStatement || statement instanceof SQLRenameUserStatement
                || statement instanceof SQLCreateRoleStatement || statement instanceof SQLDropRoleStatement
                || name.startsWith("SQLShow") || name.startsWith("MySqlShow") || "MySqlHelpStatement".equals(name);
    }

    private static String action(SQLStatement statement)
    {
        String name = statement.getClass().getSimpleName().replaceFirst("^(SQL|MySql|Mysql)", "").replaceFirst("Statement$", "");
        return name.replaceAll("([a-z])([A-Z])", "$1 $2").toUpperCase(Locale.ROOT);
    }

    private StatementPlan each(SQLStatement statement, String action, List<SQLExprTableSource> tables, String accessType, Scan scan)
            throws Refusal
    {
        StatementPlan plan = new StatementPlan(statement, action);
        for (SQLExprTableSource table : tables) {
            plan.access(table(scan.name(table), accessType));
        }
        return plan;
    }

    private StatementPlan database(SQLStatement statement, String action, @Nullable SQLExpr name, String accessType) throws Refusal
    {
        if (!(name instanceof SQLName named)) {
            throw new Refusal(Refusal.CANNOT_PARSE, "GrantForge cannot tell which database " + action + " names");
        }
        StatementPlan plan = new StatementPlan(statement, action);
        plan.access(new Access(fold(Identifiers.unquote(named.getSimpleName())), null, null, accessType));
        return plan;
    }

    /** Every column of a table, or the table itself when its columns are unknown. */
    private void all(StatementPlan plan, TableName table, String accessType)
    {
        List<String> columns = catalog.columns(table);
        if (columns == null) {
            plan.access(table(table, accessType));
            return;
        }
        for (String column : columns) {
            plan.access(column(table, column, accessType));
        }
    }

    private static @Nullable String charset(SQLAssignItem item)
    {
        String target = item.getTarget() instanceof SQLVariantRefExpr variable ? variable.getName()
                : item.getTarget() instanceof SQLPropertyExpr property ? property.getName() : null;
        if (target == null) {
            return null;
        }
        String name = target.toLowerCase(Locale.ROOT);
        if (!"names".equals(name) && !"character set".equals(name) && !"charset".equals(name) && !"character_set_client".equals(name)) {
            return null;
        }
        SQLExpr value = item.getValue();
        String text = value instanceof SQLCharExpr literal ? literal.getText() : value instanceof SQLIdentifierExpr identifier
                ? identifier.getName() : String.valueOf(value);
        return Identifiers.unquote(text.trim()).toLowerCase(Locale.ROOT);
    }

    private static Access table(TableName table, String accessType)
    {
        return new Access(table.database(), table.table(), null, accessType);
    }

    private static Access column(TableName table, String column, String accessType)
    {
        return new Access(table.database(), table.table(), column, accessType);
    }

    private static String columnName(SQLExpr column) throws Refusal
    {
        if (column instanceof SQLIdentifierExpr identifier) {
            return Identifiers.unquote(identifier.getName());
        }
        if (column instanceof SQLPropertyExpr property) {
            return Identifiers.unquote(property.getName());
        }
        throw new Refusal(Refusal.CANNOT_PARSE, "GrantForge cannot read the column " + column);
    }

    private String fold(String name)
    {
        return catalog.foldsNames() ? name.toLowerCase(Locale.ROOT) : name;
    }

    private static String firstLine(@Nullable String text)
    {
        String message = text == null ? "unreadable" : text;
        int line = message.indexOf('\n');
        return line < 0 ? message : message.substring(0, line);
    }

    /** A table or derived table a query names, under its alias or name. */
    private static final class Source
    {
        final String alias;
        final @Nullable TableName table;
        final @Nullable SQLExprTableSource node;
        boolean target;

        Source(String alias, @Nullable TableName table, @Nullable SQLExprTableSource node)
        {
            this.alias = alias;
            this.table = table;
            this.node = node;
        }
    }

    /** The tables, columns and routines of one statement, found by walking the parts it reads. */
    private final class Scan
    {
        private final @Nullable String database;
        private final Set<String> ctes = new LinkedHashSet<>();
        // The keys are query parts the parser hands out, and the same part is met again while walking a statement;
        // they are compared by identity, since the parser gives equal-looking parts their own equals.
        private final Map<SQLObject, List<Source>> blocks = new IdentityHashMap<>();
        private final List<Source> statement = new ArrayList<>();
        private final List<Source> everywhere = new ArrayList<>();
        private final List<SQLObject> references = new ArrayList<>();
        private final Map<SQLExprTableSource, Source> reads = new IdentityHashMap<>();
        private final Set<TableName> wholeTables = new LinkedHashSet<>();
        private @Nullable Refusal refused;

        Scan(@Nullable String database)
        {
            this.database = database;
        }

        TableName name(SQLExprTableSource source) throws Refusal
        {
            SQLExpr expr = source.getExpr();
            if (!(expr instanceof SQLName named)) {
                throw new Refusal(Refusal.CANNOT_PARSE, "GrantForge cannot read the table " + source);
            }
            return name(named);
        }

        TableName name(SQLName named) throws Refusal
        {
            String table = Identifiers.unquote(named.getSimpleName());
            String schema = null;
            if (named instanceof SQLPropertyExpr property) {
                if (!(property.getOwner() instanceof SQLIdentifierExpr owner)) {
                    throw new Refusal(Refusal.CANNOT_PARSE, "GrantForge cannot read the table " + named);
                }
                schema = Identifiers.unquote(owner.getName());
            }
            if (schema == null) {
                schema = database;
            }
            if (schema == null) {
                throw new Refusal(Refusal.NO_DATABASE, "No database selected");
            }
            return new TableName(fold(schema), fold(table));
        }

        /** Registers the table INSERT writes: its columns resolve names outside queries, but it is not read. */
        void target(SQLExprTableSource source, TableName table, boolean read)
        {
            Source target = new Source(aliasOf(source, table), table, source);
            target.target = !read;
            statement.add(target);
            everywhere.add(target);
        }

        /** The tables of an UPDATE or DELETE, which name its columns. */
        List<Source> statementSources(SQLTableSource from) throws Refusal
        {
            collectCtes(from);
            List<Source> sources = sources(from);
            statement.addAll(sources);
            return sources;
        }

        void markTarget(Source source)
        {
            source.target = true;
        }

        /** Reads the parts of a statement's tables that are not the tables themselves, such as join conditions. */
        void readsBesides(SQLTableSource from)
        {
            walk(from);
        }

        void reads(@Nullable SQLObject part)
        {
            if (part != null) {
                collectCtes(part);
                walk(part);
            }
        }

        private void collectCtes(SQLObject part)
        {
            part.accept(new MySqlASTVisitorAdapter()
            {
                @Override
                public boolean visit(SQLWithSubqueryClause.Entry entry)
                {
                    ctes.add(Identifiers.unquote(entry.getAlias()).toLowerCase(Locale.ROOT));
                    return true;
                }
            });
        }

        private void walk(SQLObject part)
        {
            part.accept(new MySqlASTVisitorAdapter()
            {
                @Override
                public boolean visit(SQLExprTableSource x)
                {
                    // The table's name is no column; what else it holds (JSON_TABLE's arguments) is read.
                    return !(x.getExpr() instanceof SQLName);
                }

                @Override
                public boolean visit(SQLIdentifierExpr x)
                {
                    references.add(x);
                    return false;
                }

                @Override
                public boolean visit(SQLPropertyExpr x)
                {
                    references.add(x);
                    return false;
                }

                @Override
                public boolean visit(SQLAllColumnExpr x)
                {
                    if (x.getParent() instanceof SQLSelectItem) {
                        references.add(x);
                    }
                    return false;
                }

                @Override
                public boolean visit(SQLMethodInvokeExpr x)
                {
                    routine(x);
                    // VALUES(a) in ON DUPLICATE KEY UPDATE is the value being inserted, not what the table holds.
                    return !"values".equalsIgnoreCase(x.getMethodName());
                }

                @Override
                public boolean visit(SQLSelectQueryBlock x)
                {
                    block(x);
                    return true;
                }

                @Override
                public boolean visit(SQLJoinTableSource x)
                {
                    if (x.isNatural() || x.getJoinType() == SQLJoinTableSource.JoinType.NATURAL_JOIN
                            || x.getJoinType() == SQLJoinTableSource.JoinType.NATURAL_LEFT_JOIN
                            || x.getJoinType() == SQLJoinTableSource.JoinType.NATURAL_RIGHT_JOIN
                            || x.getJoinType() == SQLJoinTableSource.JoinType.NATURAL_INNER_JOIN) {
                        // A natural join compares every column the two sides share: count all of them.
                        natural(x);
                    }
                    return true;
                }
            });
        }

        private void routine(SQLMethodInvokeExpr call)
        {
            if (refused != null) {
                return;
            }
            String name = Identifiers.unquote(call.getMethodName());
            if (call.getOwner() != null) {
                refused = new Refusal(Refusal.NOT_ALLOWED, "GrantForge does not allow stored functions such as " + call.getOwner() + "."
                        + name + ": MySQL runs them as their definer");
            }
            else if (database != null && catalog.routine(fold(database), name)) {
                refused = new Refusal(Refusal.NOT_ALLOWED, "GrantForge does not allow the stored function " + name
                        + ": MySQL runs it as its definer");
            }
        }

        private void block(SQLSelectQueryBlock block)
        {
            if (blocks.containsKey(block) || block.getFrom() == null) {
                return;
            }
            try {
                blocks.put(block, sources(block.getFrom()));
            }
            catch (Refusal refusal) {
                if (refused == null) {
                    refused = refusal;
                }
                blocks.put(block, List.of());
            }
        }

        private void natural(SQLJoinTableSource join)
        {
            try {
                for (Source source : sources(join)) {
                    if (source.table != null) {
                        wholeTables.add(source.table);
                    }
                }
            }
            catch (Refusal refusal) {
                if (refused == null) {
                    refused = refusal;
                }
            }
        }

        /** The tables a FROM names directly, without those inside its derived tables. */
        private List<Source> sources(SQLTableSource from) throws Refusal
        {
            List<Source> found = new ArrayList<>();
            sources(from, found);
            return found;
        }

        private void sources(@Nullable SQLTableSource from, List<Source> found) throws Refusal
        {
            if (from == null) {
                return;
            }
            if (from instanceof SQLJoinTableSource join) {
                sources(join.getLeft(), found);
                sources(join.getRight(), found);
                return;
            }
            if (from instanceof SQLExprTableSource table && table.getExpr() instanceof SQLName named) {
                boolean cte = named instanceof SQLIdentifierExpr && ctes.contains(Identifiers.unquote(named.getSimpleName()).toLowerCase(Locale.ROOT));
                if (cte && database != null && catalog.columns(name(table)) != null) {
                    // A WITH reaches only its own query; the same name elsewhere is the table, which must not go unchecked.
                    throw new Refusal(Refusal.NOT_ALLOWED, "GrantForge cannot tell the table " + named.getSimpleName()
                            + " from the common table expression of the same name; rename the expression");
                }
                if (!cte) {
                    Source existing = reads.get(table);
                    if (existing != null) {
                        found.add(existing);
                        return;
                    }
                    TableName name = name(table);
                    Source source = new Source(aliasOf(table, name), name, table);
                    reads.put(table, source);
                    everywhere.add(source);
                    found.add(source);
                    return;
                }
            }
            String alias = from.getAlias() == null ? "" : Identifiers.unquote(from.getAlias()).toLowerCase(Locale.ROOT);
            if (alias.isEmpty() && from instanceof SQLExprTableSource table && table.getExpr() instanceof SQLName named) {
                alias = Identifiers.unquote(named.getSimpleName()).toLowerCase(Locale.ROOT);
            }
            Source derived = new Source(alias, null, null);
            everywhere.add(derived);
            found.add(derived);
        }

        private String aliasOf(SQLExprTableSource source, TableName table)
        {
            String alias = source.getAlias();
            return (alias == null ? table.table() : Identifiers.unquote(alias)).toLowerCase(Locale.ROOT);
        }

        /** The source a column of an UPDATE's SET names. */
        @Nullable Source owner(SQLExpr column, List<Source> sources)
        {
            if (column instanceof SQLPropertyExpr property && property.getOwner() instanceof SQLIdentifierExpr owner) {
                String alias = Identifiers.unquote(owner.getName()).toLowerCase(Locale.ROOT);
                return sources.stream().filter(source -> source.alias.equals(alias)).findFirst().orElse(null);
            }
            if (column instanceof SQLIdentifierExpr identifier) {
                String name = Identifiers.unquote(identifier.getName());
                List<Source> having = sources.stream().filter(source -> source.table != null && has(source.table, name)).toList();
                if (having.size() == 1) {
                    return having.get(0);
                }
                return sources.size() == 1 ? sources.get(0) : null;
            }
            return null;
        }

        /** The source a multi-table DELETE names as a target. */
        @Nullable Source alias(SQLExprTableSource target, List<Source> sources) throws Refusal
        {
            String alias = Identifiers.unquote(target.getExpr() instanceof SQLName named ? named.getSimpleName() : String.valueOf(target))
                    .toLowerCase(Locale.ROOT);
            for (Source source : sources) {
                if (source.alias.equals(alias)) {
                    return source;
                }
            }
            TableName name = name(target);
            return sources.stream().filter(source -> name.equals(source.table)).findFirst().orElse(null);
        }

        private boolean has(TableName table, String column)
        {
            List<String> columns = catalog.columns(table);
            return columns != null && columns.stream().anyMatch(known -> known.equalsIgnoreCase(column));
        }

        /** A column's name as the catalog writes it; columns compare without case in MySQL. */
        String actual(TableName table, String column)
        {
            List<String> columns = catalog.columns(table);
            if (columns != null) {
                for (String known : columns) {
                    if (known.equalsIgnoreCase(column)) {
                        return known;
                    }
                }
            }
            return column;
        }

        /** Resolves the columns found and adds what the statement reads to its plan. */
        void finish(StatementPlan plan) throws Refusal
        {
            if (refused != null) {
                throw refused;
            }
            for (SQLObject reference : references) {
                resolve(reference, plan);
            }
            for (Source source : reads.values()) {
                if (source.table == null) {
                    continue;
                }
                if (!source.target) {
                    read(plan, source);
                }
            }
            Set<TableName> read = new LinkedHashSet<>();
            for (Source source : reads.values()) {
                if (source.table != null && !source.target) {
                    read.add(source.table);
                }
            }
            for (Source source : statement) {
                if (source.table != null && !source.target) {
                    read.add(source.table);
                }
            }
            read.addAll(wholeTables);
            for (TableName table : read) {
                Set<String> used = plan.used(table);
                if (wholeTables.contains(table)) {
                    List<String> columns = catalog.columns(table);
                    if (columns == null) {
                        plan.access(table(table, "select"));
                    }
                    else {
                        columns.forEach(column -> use(plan, table, column));
                    }
                }
                else if (used.isEmpty()) {
                    // Rows are read without a column, as COUNT(*) or EXISTS do: the whole table must be readable.
                    plan.access(table(table, "select"));
                }
            }
        }

        private void read(StatementPlan plan, Source source)
        {
            plan.read(new StatementPlan.Read(requireNonNull(source.node, "node"), requireNonNull(source.table, "table")));
        }

        private void use(StatementPlan plan, TableName table, String column)
        {
            String name = actual(table, column);
            plan.use(table, name);
            plan.access(column(table, name, "select"));
        }

        private void resolve(SQLObject reference, StatementPlan plan) throws Refusal
        {
            List<List<Source>> scopes = scopes(reference);
            if (reference instanceof SQLAllColumnExpr star) {
                List<Source> matched = star.getOwner() == null ? (scopes.isEmpty() ? List.of() : scopes.get(0))
                        : qualified(Identifiers.unquote(String.valueOf(star.getOwner())).toLowerCase(Locale.ROOT), scopes);
                for (Source source : matched) {
                    if (source.table != null) {
                        wholeTables.add(source.table);
                    }
                }
                return;
            }
            if (reference instanceof SQLPropertyExpr property) {
                String column = Identifiers.unquote(property.getName());
                if (property.getOwner() instanceof SQLPropertyExpr table && table.getOwner() instanceof SQLIdentifierExpr) {
                    use(plan, name(table), column);
                    return;
                }
                if (property.getOwner() instanceof SQLVariantRefExpr) {
                    // @@session.x is a system variable, not a column.
                    return;
                }
                if (!(property.getOwner() instanceof SQLIdentifierExpr owner)) {
                    throw new Refusal(Refusal.CANNOT_PARSE, "GrantForge cannot read the column " + property);
                }
                for (Source source : qualified(Identifiers.unquote(owner.getName()).toLowerCase(Locale.ROOT), scopes)) {
                    if (source.table != null) {
                        use(plan, source.table, column);
                    }
                }
                return;
            }
            String column = Identifiers.unquote(((SQLIdentifierExpr) reference).getName());
            for (List<Source> scope : scopes) {
                for (Source source : scope) {
                    if (source.table != null && has(source.table, column)) {
                        use(plan, source.table, column);
                    }
                }
            }
        }

        /** The sources a column can name: of its query block and the blocks around it, innermost first, then the statement's. */
        private List<List<Source>> scopes(SQLObject reference)
        {
            List<List<Source>> scopes = new ArrayList<>();
            for (SQLObject parent = reference.getParent(); parent != null; parent = parent.getParent()) {
                List<Source> sources = blocks.get(parent);
                if (sources != null) {
                    scopes.add(sources);
                }
            }
            boolean inQuery = !scopes.isEmpty();
            List<Source> outer = new ArrayList<>();
            for (Source source : statement) {
                // INSERT's table names columns only outside its query, as in ON DUPLICATE KEY UPDATE.
                if (!inQuery || !source.target || statementSourceOfUpdateOrDelete(source)) {
                    outer.add(source);
                }
            }
            scopes.add(outer);
            return scopes;
        }

        private boolean statementSourceOfUpdateOrDelete(Source source)
        {
            return source.node != null && reads.containsKey(source.node);
        }

        private List<Source> qualified(String alias, List<List<Source>> scopes)
        {
            for (List<Source> scope : scopes) {
                List<Source> matched = scope.stream().filter(source -> source.alias.equals(alias)).toList();
                if (!matched.isEmpty()) {
                    return matched;
                }
            }
            // Not in sight of the column: count every table of the statement with that alias, to be safe.
            return everywhere.stream().filter(source -> source.alias.equals(alias)).toList();
        }
    }
}
