// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.postgresql.agent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A PostgreSQL that answers queries by a fragment of their text, in one database or any, records the statements it is
 * given as {@code database: statement}, and fails the queries and statements that contain a fragment it is told to
 * fail. An answer given later takes precedence over one given before. A transaction that fails leaves no statement recorded, as a rollback leaves nothing done. A query no answer
 * matches returns no rows.
 */
final class FakePostgreSql
        implements PostgreSql
{
    final List<String> executed = new ArrayList<>();
    final List<String> queried = new ArrayList<>();
    final List<List<Object>> parameters = new ArrayList<>();
    final List<String> rolledBack = new ArrayList<>();
    private final List<Answer> answers = new ArrayList<>();
    private final Map<String, PostgreSqlFailure> failures = new LinkedHashMap<>();
    boolean closed;

    /** Answers the queries containing a fragment, in any database, with rows. */
    FakePostgreSql answer(String fragment, Row... rows)
    {
        return answer(null, fragment, rows);
    }

    /** Answers the queries containing a fragment in a database, or any when it is {@code null}, with rows. */
    FakePostgreSql answer(@Nullable String database, String fragment, Row... rows)
    {
        return answer(database, fragment, parameters -> List.of(rows));
    }

    /** Answers the queries containing a fragment in a database, or any, by their parameters. */
    FakePostgreSql answer(@Nullable String database, String fragment, Function<List<Object>, List<Row>> answer)
    {
        answers.add(new Answer(database, fragment, answer));
        return this;
    }

    /** An answer to the queries containing a fragment, in one database or any. */
    private record Answer(@Nullable String database, String fragment, Function<List<Object>, List<Row>> rows)
    {
    }

    /** Fails the queries and statements containing a fragment with an SQLSTATE, until told otherwise. */
    FakePostgreSql fail(String fragment, @Nullable String state)
    {
        failures.put(fragment, new PostgreSqlFailure("failed: " + fragment, state, null));
        return this;
    }

    /** Stops failing what contains a fragment. */
    FakePostgreSql heal(String fragment)
    {
        failures.remove(fragment);
        return this;
    }

    private void check(String database, String sql)
    {
        for (Map.Entry<String, PostgreSqlFailure> failure : failures.entrySet()) {
            if ((database + ": " + sql).contains(failure.getKey())) {
                throw failure.getValue();
            }
        }
    }

    @Override
    public List<Row> query(String database, String sql, Object... values)
    {
        check(database, sql);
        queried.add(database + ": " + sql);
        parameters.add(Arrays.asList(values));
        for (int index = answers.size() - 1; index >= 0; index--) {
            Answer answer = answers.get(index);
            if ((answer.database() == null || answer.database().equals(database)) && sql.contains(answer.fragment())) {
                return answer.rows().apply(Arrays.asList(values));
            }
        }
        return List.of();
    }

    @Override
    public void execute(String database, String sql)
    {
        check(database, sql);
        executed.add(database + ": " + sql);
    }

    @Override
    public void transaction(String database, Work work)
    {
        int start = executed.size();
        try {
            work.run(new Session()
            {
                @Override
                public void execute(String sql)
                {
                    FakePostgreSql.this.execute(database, sql);
                }

                @Override
                public @Nullable PostgreSqlFailure attempt(String sql)
                {
                    try {
                        FakePostgreSql.this.execute(database, sql);
                        return null;
                    }
                    catch (PostgreSqlFailure failed) {
                        if (failed.unreachable()) {
                            throw failed;
                        }
                        return failed;
                    }
                }

                @Override
                public List<Row> query(String sql, Object... values)
                {
                    return FakePostgreSql.this.query(database, sql, values);
                }
            });
        }
        catch (RuntimeException failed) {
            List<String> undone = executed.subList(start, executed.size());
            rolledBack.addAll(undone);
            undone.clear();
            throw failed;
        }
    }

    @Override
    public void close()
    {
        closed = true;
    }
}
